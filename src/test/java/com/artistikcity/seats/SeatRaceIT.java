package com.artistikcity.seats;

import com.artistikcity.config.Settings;
import com.artistikcity.security.AuditLog;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "last seat" race (audit finding 6), against a real MySQL 8 database: 20 students press Pay for a live class
 * with 5 seats at the same instant. Exactly 5 get a seat, the other 15 are refused with SOLD_OUT (HTTP 409 in the
 * checkout), and the database never holds more than 5 seats. Then the 5 pay: all 5 are confirmed, a 6th payment that
 * arrives without a seat is refused (and refunded by the checkout).
 *
 * <p>Runs only with {@code ARTISTIK_IT_DB_URL} set, like {@code MarketplaceConcurrencyIT}:
 * {@code ARTISTIK_IT_DB_URL=jdbc:mysql://127.0.0.1:3306/artistikcity mvn test -Dtest=SeatRaceIT}</p>
 */
class SeatRaceIT {

    private static final int STUDENTS = 20;
    private static final int SEATS = 5;

    private static HikariDataSource ds;
    private static JdbcTemplate jdbc;
    private static SeatService seats;
    private long courseId;

    @BeforeAll
    static void connect() {
        String url = System.getenv("ARTISTIK_IT_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "ARTISTIK_IT_DB_URL not set: seat race test skipped");
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(url);
        c.setUsername(System.getenv().getOrDefault("ARTISTIK_IT_DB_USER", "artistik"));
        c.setPassword(System.getenv().getOrDefault("ARTISTIK_IT_DB_PASSWORD", "Artistik@2026"));
        c.setMaximumPoolSize(STUDENTS + 4);
        ds = new HikariDataSource(c);
        jdbc = new JdbcTemplate(ds);
        DataSourceTransactionManager tm = new DataSourceTransactionManager(ds);
        StandardEnvironment env = new StandardEnvironment();
        seats = new SeatService(jdbc, tm, new Settings(env), null, env, new AuditLog(jdbc, tm));
    }

    @AfterAll
    static void close() {
        if (ds != null) ds.close();
    }

    @BeforeEach
    void course() {
        // a copy of the first course, capped at 5 seats
        List<String> cols = jdbc.queryForList("select column_name from information_schema.columns where table_schema = database() and table_name = 'courses'"
                + " and column_name not in ('id', 'slug', 'seat_limit') order by ordinal_position", String.class);
        String list = String.join(", ", cols.stream().map(x -> "`" + x + "`").toList());
        String slug = "it-seats-" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("insert into courses (" + list + ", slug, seat_limit) select " + list + ", ?, ? from courses order by id limit 1", slug, SEATS);
        courseId = jdbc.queryForObject("select id from courses where slug = ?", Long.class, slug);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from seat_holds where course_id = ?", courseId);
        jdbc.update("delete from courses where id = ?", courseId);
    }

    private static List<Object> race(List<Callable<Object>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> t : tasks) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                try {
                    return t.call();
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        go.countDown();
        List<Object> out = new ArrayList<>();
        for (Future<Object> f : futures) out.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        return out;
    }

    @Test
    void twentyStudentsFiveSeatsExactlyFiveGetOne() throws Exception {
        List<Callable<Object>> buyers = new ArrayList<>();
        for (int i = 0; i < STUDENTS; i++) {
            long userId = 900_000 + i;
            buyers.add(() -> {
                seats.hold(courseId, userId, "it" + userId + "@example.com", null, null);
                return "SEAT";
            });
        }
        List<Object> results = race(buyers);
        long won = results.stream().filter("SEAT"::equals).count();
        long soldOut = results.stream().filter(r -> r instanceof SeatService.SoldOutException).count();
        assertEquals(List.of(), results.stream().filter(r -> !"SEAT".equals(r) && !(r instanceof SeatService.SoldOutException)).toList(), "only seats or SOLD_OUT");
        assertEquals(SEATS, won, "exactly 5 students hold a seat");
        assertEquals(STUDENTS - SEATS, soldOut, "the other 15 get SOLD_OUT (409)");
        assertEquals(SEATS, jdbc.queryForObject("select count(*) from seat_holds where course_id = ? and status = 'HELD'", Integer.class, courseId));
        assertEquals(0, seats.seats(courseId).left());
        assertTrue(seats.seats(courseId).soldOut());

        // the 5 pay at the same moment: all confirmed; a payment from someone without a seat is refused
        List<Long> holders = jdbc.queryForList("select user_id from seat_holds where course_id = ?", Long.class, courseId);
        List<Callable<Object>> payments = new ArrayList<>();
        for (Long u : holders) payments.add(() -> seats.confirm(courseId, u));
        long late = holders.contains(900_019L) ? 900_000L + STUDENTS + 1 : 900_019L;
        payments.add(() -> seats.confirm(courseId, late));
        List<Object> paid = race(payments);
        assertEquals(SEATS, paid.stream().filter(Boolean.TRUE::equals).count(), "every seat holder is confirmed");
        assertEquals(1, paid.stream().filter(Boolean.FALSE::equals).count(), "the payment without a seat is refused");
        assertEquals(SEATS, jdbc.queryForObject("select count(*) from seat_holds where course_id = ? and status = 'PAID'", Integer.class, courseId));
    }

    @Test
    void aReleasedSeatGoesBackAndRefundedStudentsFreeTheirSeat() throws Exception {
        for (int i = 0; i < SEATS; i++) seats.hold(courseId, 910_000 + i, null, null, null);
        assertTrue(seats.seats(courseId).soldOut());
        seats.release(courseId, 910_000);
        assertEquals(1, seats.seats(courseId).left());
        seats.hold(courseId, 920_000, null, null, null);
        assertTrue(seats.seats(courseId).soldOut());
    }
}
