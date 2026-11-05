package com.artistikcity.marketplace;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Double-selling a 1-of-1 original, against a real MySQL 8 database: many collectors press "Reserve" (and then pay) for
 * the same painting at the same instant. Exactly one may win; every other one must get 409 Conflict, the database
 * must hold one hold row and the painting must end up SOLD exactly once, with the late payers flagged for a refund.
 *
 * <p>Runs only when a database is named, because it writes (and afterwards deletes) its own test paintings:
 * <pre>
 *   ARTISTIK_IT_DB_URL=jdbc:mysql://127.0.0.1:3306/artistikcity ARTISTIK_IT_DB_USER=artistik ARTISTIK_IT_DB_PASSWORD=Artistik@2026 \
 *     mvn test -Dtest=MarketplaceConcurrencyIT
 * </pre>
 * Without those variables the test is skipped (so a plain {@code mvn test} never touches a database).</p>
 */
class MarketplaceConcurrencyIT {

    private static final int BUYERS = 12;

    private static HikariDataSource ds;
    private static JdbcTemplate jdbc;
    private static MarketplaceService service;

    private long paintingId;
    private final String slug = "it-race-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll
    static void connect() {
        String url = System.getenv("ARTISTIK_IT_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "ARTISTIK_IT_DB_URL not set: database race test skipped");
        HikariConfig c = new HikariConfig();
        c.setJdbcUrl(url);
        c.setUsername(System.getenv().getOrDefault("ARTISTIK_IT_DB_USER", "artistik"));
        c.setPassword(System.getenv().getOrDefault("ARTISTIK_IT_DB_PASSWORD", "Artistik@2026"));
        c.setMaximumPoolSize(BUYERS + 4);          // every buyer really gets its own connection
        c.setTransactionIsolation("TRANSACTION_REPEATABLE_READ");
        ds = new HikariDataSource(c);
        jdbc = new JdbcTemplate(ds);
        DataSourceTransactionManager tm = new DataSourceTransactionManager(ds);
        service = new MarketplaceService(jdbc, tm, new MarketplaceRepository(jdbc, tm));
    }

    @AfterAll
    static void close() {
        if (ds != null) ds.close();
    }

    @BeforeEach
    void painting() {
        jdbc.update("insert into paintings (title, slug, description, medium, surface, height_inches, width_inches, year_created, base_price,"
                        + " discount_percentage, final_price, currency, stock_status, version, source, artist_name, created_at, updated_at)"
                        + " values (?, ?, ?, 'Oil', 'Canvas', 24, 18, 2025, 10000, 0, 10000, 'INR', 'AVAILABLE', 0, 'STUDIO', 'Test Artist', now(), now())",
                "Race test " + slug, slug, "A painting created by MarketplaceConcurrencyIT.");
        paintingId = jdbc.queryForObject("select id from paintings where slug = ?", Long.class, slug);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from marketplace_orders where painting_id = ?", paintingId);
        jdbc.update("delete from cart_reservations where painting_id = ?", paintingId);
        jdbc.update("delete from paintings where id = ?", paintingId);
    }

    /** Runs every task at the same instant on its own thread and returns what each one produced. */
    private static <T> List<Object> race(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<T> t : tasks) {
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
        assertTrue(ready.await(10, TimeUnit.SECONDS), "threads did not start");
        go.countDown();
        List<Object> out = new ArrayList<>();
        for (Future<Object> f : futures) out.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        return out;
    }

    @Test
    void onlyOneOfManySimultaneousReservationsWinsTheOthersGet409() throws Exception {
        List<Callable<MarketplaceService.Hold>> buyers = new ArrayList<>();
        for (int i = 0; i < BUYERS; i++) {
            String key = "S:it-buyer-" + i + "-" + slug;
            buyers.add(() -> service.reserve(paintingId, key, List.of(key), null, false));
        }
        List<Object> results = race(buyers);

        long won = results.stream().filter(r -> r instanceof MarketplaceService.Hold h && h.created()).count();
        List<MarketplaceException> lost = results.stream().filter(r -> r instanceof MarketplaceException).map(r -> (MarketplaceException) r).toList();
        List<Object> unexpected = results.stream().filter(r -> !(r instanceof MarketplaceService.Hold) && !(r instanceof MarketplaceException)).toList();

        assertEquals(List.of(), unexpected, "only holds or clean 409s are acceptable answers");
        assertEquals(1, won, "exactly one collector holds the piece");
        assertEquals(BUYERS - 1, lost.size());
        for (MarketplaceException e : lost) {
            assertEquals(409, e.status(), "a losing buyer gets 409 Conflict, not " + e.status());
            assertTrue(List.of("JUST_TAKEN", "HELD_BY_OTHER").contains(e.body().get("code")), "unexpected code " + e.body().get("code"));
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from cart_reservations where painting_id = ?", Integer.class, paintingId));
        assertEquals("RESERVED", jdbc.queryForObject("select stock_status from paintings where id = ?", String.class, paintingId));
    }

    @Test
    void theHolderKeepsTheirHoldWhenTheyPressReserveAgain() throws Exception {
        String key = "U:it-" + slug;
        service.reserve(paintingId, key, List.of(key), null, false);
        // what the database stored (DATETIME rounds the fractional second)
        java.time.LocalDateTime stored = jdbc.queryForObject("select expires_at from cart_reservations where painting_id = ?",
                java.time.LocalDateTime.class, paintingId);
        List<Callable<MarketplaceService.Hold>> again = new ArrayList<>();
        for (int i = 0; i < 4; i++) again.add(() -> service.reserve(paintingId, key, List.of(key), null, false));
        for (Object r : race(again)) {
            assertTrue(r instanceof MarketplaceService.Hold, "the holder is never refused: " + r);
            assertEquals(stored, ((MarketplaceService.Hold) r).expiresAt(), "pressing again returns the same hold and does not extend it");
        }
        assertEquals(1, jdbc.queryForObject("select count(*) from cart_reservations where painting_id = ?", Integer.class, paintingId));
        assertEquals(stored, jdbc.queryForObject("select expires_at from cart_reservations where painting_id = ?", java.time.LocalDateTime.class, paintingId));
    }

    @Test
    void simultaneousVerifiedPaymentsSellThePaintingOnceAndFlagTheRestForRefund() throws Exception {
        // several collectors reached the payment step at different times (each had the hold once, then it expired):
        // their payments are all confirmed by the gateway at the same moment
        List<String> orders = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            String orderId = "IT" + i + slug.replace("-", "").substring(0, 8).toUpperCase();
            orders.add(orderId);
            service.createPendingOrder(new MarketplaceService.NewOrder(orderId, paintingId, null, "S:it-payer-" + i + "-" + slug,
                    Map.of("name", "Payer " + i, "email", "payer" + i + "@example.com", "phone", "", "line1", "1 Test Road", "line2", "",
                            "city", "Pune", "state", "MH", "postal", "411001", "country", "India"),
                    "TEST", "INR", new BigDecimal("10000.00"), new BigDecimal("10000.00")));
        }
        List<Callable<MarketplaceService.Outcome>> confirmations = new ArrayList<>();
        for (String id : orders) {
            confirmations.add(() -> service.finalizeSale(id, "pay_" + id));
            confirmations.add(() -> service.finalizeSale(id, "pay_" + id));   // the webhook and the browser callback, together
        }
        List<Object> results = race(confirmations);

        assertEquals(List.of(), results.stream().filter(r -> r instanceof Exception).toList(), "no confirmation may fail");
        assertEquals("SOLD", jdbc.queryForObject("select stock_status from paintings where id = ?", String.class, paintingId));
        assertEquals(1, jdbc.queryForObject("select count(*) from marketplace_orders where painting_id = ? and status = 'PAID'", Integer.class, paintingId),
                "exactly one buyer owns the original");
        assertEquals(orders.size() - 1, jdbc.queryForObject(
                        "select count(*) from marketplace_orders where painting_id = ? and status = 'REFUND_REQUIRED'", Integer.class, paintingId),
                "every other payment is flagged for a refund, none is silently lost");
        assertEquals(1, results.stream().filter(r -> r == MarketplaceService.Outcome.SOLD).count());
        // the second confirmation of each order is idempotent: it never sells twice
        for (String id : orders) {
            assertTrue(List.of("PAID", "REFUND_REQUIRED").contains(
                    jdbc.queryForObject("select status from marketplace_orders where order_id = ?", String.class, id)));
        }
    }

    @Test
    void databaseRunsWithTheIsolationTheEngineExpects() throws Exception {
        try (Connection c = ds.getConnection()) {
            assertTrue(c.getMetaData().supportsTransactionIsolationLevel(Connection.TRANSACTION_SERIALIZABLE));
        }
        // the UNIQUE(painting_id) guard behind the optimistic lock must exist
        Integer unique = jdbc.queryForObject("select count(*) from information_schema.statistics where table_schema = database()"
                + " and table_name = 'cart_reservations' and column_name = 'painting_id' and non_unique = 0", Integer.class);
        assertTrue(unique != null && unique > 0, "cart_reservations.painting_id must be UNIQUE");
    }
}
