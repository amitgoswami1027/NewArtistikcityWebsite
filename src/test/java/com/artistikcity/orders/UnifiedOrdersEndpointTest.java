package com.artistikcity.orders;

import com.artistikcity.http.Auth;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.view.Views;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test of {@code GET /api/v1/user/orders} through the Spring MVC stack (MockMvc): the controller, the
 * aggregation service and the JSON shape, with mock course and marketplace order sources standing in for the database.
 */
class UnifiedOrdersEndpointTest {

    private static final long STUDENT = 7L;
    private final ObjectMapper mapper = new ObjectMapper();
    private Auth auth;
    private MockMvc mvc;
    private final List<String> sourceCalls = java.util.Collections.synchronizedList(new ArrayList<>());

    /** Mock course source: one paid course (oldest) and one workshop awaiting payment. */
    private OrderSource courses() {
        return userId -> {
            sourceCalls.add("courses:" + userId);
            return List.of(
                    UnifiedOrder.builder("c1", "ORD00001", UnifiedOrder.COURSE, "Digital Course")
                            .title("Watercolour Landscapes for Beginners").purchaseDate("2026-09-01T10:00:00")
                            .amount(new BigDecimal("4999")).currency("INR").status("completed", "Paid · access open")
                            .accessUrl("/user/course/1").build(),
                    UnifiedOrder.builder("c2", "ORD00002", UnifiedOrder.COURSE, "Workshop")
                            .title("Weekend Florals").purchaseDate("2026-10-20T09:30:00")
                            .amount(new BigDecimal("1499")).currency("INR").status("pending", "Payment pending")
                            .detailUrl("/course/weekend-florals").build());
        };
    }

    /** Mock marketplace source: a shipped original with tracking and certificate, and a digital download (newest). */
    private OrderSource marketplace() {
        return userId -> {
            sourceCalls.add("marketplace:" + userId);
            return List.of(
                    UnifiedOrder.builder("s10", "AC-S-20261001-AAAAAA", UnifiedOrder.MARKETPLACE, "Original Painting")
                            .title("Lines of a Lifetime").subtitle("by Vaishali J Goswami").purchaseDate("2026-10-01T12:00:00")
                            .amount(new BigDecimal("39621")).currency("INR").status("shipped", "Shipped")
                            .tracking("BD123", "https://www.bluedart.com/tracking?trackFor=0&trackNo=BD123")
                            .certificateUrl("/marketplace/certificates/AC-S-20261001-AAAAAA/10").quantity(1).build(),
                    UnifiedOrder.builder("s11", "AC-S-20261024-BBBBBB", UnifiedOrder.MARKETPLACE, "Digital Download")
                            .title("Scarlet Macaw Study").purchaseDate("2026-10-24T18:45:00")
                            .amount(new BigDecimal("999")).currency("INR").status("completed", "Ready to download")
                            .downloadUrl("/shop/download/abc").quantity(1).build());
        };
    }

    /** A source that is broken must not hide the others. */
    private OrderSource broken() {
        return userId -> {
            throw new IllegalStateException("commission table unavailable");
        };
    }

    @BeforeEach
    void setUp() {
        auth = mock(Auth.class);
        UnifiedOrderService service = new UnifiedOrderService(List.of(courses(), marketplace(), broken()));
        UserOrdersController controller = new UserOrdersController(mock(Inertia.class), auth, new Json(mapper), mock(Db.class), mock(Views.class), service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private JsonNode body(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void returns200AndAggregatesCourseAndMarketplaceOrdersNewestFirst() throws Exception {
        when(auth.userId(any(HttpServletRequest.class))).thenReturn(STUDENT);

        MvcResult r = mvc.perform(get("/api/v1/user/orders")).andExpect(status().isOk()).andReturn();
        JsonNode json = body(r);

        assertThat(r.getResponse().getContentType()).startsWith("application/json");
        assertThat(sourceCalls).containsExactlyInAnyOrder("courses:" + STUDENT, "marketplace:" + STUDENT);
        assertThat(json.get("total").asInt()).isEqualTo(4);

        JsonNode orders = json.get("orders");
        assertThat(orders).hasSize(4);
        List<String> ids = new ArrayList<>();
        orders.forEach(o -> ids.add(o.get("orderId").asText()));
        // newest first, courses and marketplace interleaved by date
        assertThat(ids).containsExactly("AC-S-20261024-BBBBBB", "ORD00002", "AC-S-20261001-AAAAAA", "ORD00001");

        List<String> types = new ArrayList<>();
        orders.forEach(o -> types.add(o.get("orderType").asText()));
        assertThat(types).containsOnly("course", "marketplace").contains("course", "marketplace");

        // the UnifiedOrder shape the page relies on
        JsonNode original = orders.get(2);
        assertThat(original.get("title").asText()).isEqualTo("Lines of a Lifetime");
        assertThat(original.get("category").asText()).isEqualTo("Original Painting");
        assertThat(original.get("status").asText()).isEqualTo("shipped");
        assertThat(original.get("purchaseDate").asText()).isEqualTo("2026-10-01T12:00:00");
        assertThat(original.get("amount").decimalValue()).isEqualByComparingTo("39621");
        assertThat(original.get("trackingUrl").asText()).contains("BD123");
        assertThat(original.get("certificateUrl").asText()).startsWith("/marketplace/certificates/");
        assertThat(original.has("accessUrl")).isFalse();                       // absent fields are left out, not null

        JsonNode course = orders.get(3);
        assertThat(course.get("orderType").asText()).isEqualTo("course");
        assertThat(course.get("category").asText()).isEqualTo("Digital Course");
        assertThat(course.get("accessUrl").asText()).isEqualTo("/user/course/1");
        assertThat(course.get("status").asText()).isEqualTo("completed");

        // filter-chip counts
        JsonNode counts = json.get("counts");
        assertThat(counts.get("all").asInt()).isEqualTo(4);
        assertThat(counts.get("course").asInt()).isEqualTo(1);
        assertThat(counts.get("workshop").asInt()).isEqualTo(1);
        assertThat(counts.get("marketplace").asInt()).isEqualTo(2);
        assertThat(counts.get("original").asInt()).isEqualTo(1);
        assertThat(counts.get("download").asInt()).isEqualTo(1);
        assertThat(counts.get("commission").asInt()).isZero();
    }

    @Test
    void filtersByTypeAndSearch() throws Exception {
        when(auth.userId(any(HttpServletRequest.class))).thenReturn(STUDENT);

        JsonNode marketplace = body(mvc.perform(get("/api/v1/user/orders").param("type", "marketplace")).andExpect(status().isOk()).andReturn());
        assertThat(marketplace.get("orders")).hasSize(2);
        marketplace.get("orders").forEach(o -> assertThat(o.get("orderType").asText()).isEqualTo("marketplace"));
        assertThat(marketplace.get("total").asInt()).isEqualTo(4);

        JsonNode search = body(mvc.perform(get("/api/v1/user/orders").param("q", "vaishali lines")).andExpect(status().isOk()).andReturn());
        assertThat(search.get("orders")).hasSize(1);
        assertThat(search.get("orders").get(0).get("orderId").asText()).isEqualTo("AC-S-20261001-AAAAAA");

        JsonNode unknown = body(mvc.perform(get("/api/v1/user/orders").param("type", "nonsense")).andExpect(status().isOk()).andReturn());
        assertThat(unknown.get("orders")).hasSize(4);                         // unknown filter = all
    }

    @Test
    void signedOutIs401() throws Exception {
        when(auth.userId(any(HttpServletRequest.class))).thenReturn(null);
        MvcResult r = mvc.perform(get("/api/v1/user/orders")).andExpect(status().isUnauthorized()).andReturn();
        assertThat(body(r).get("message").asText()).contains("Sign in");
        assertThat(sourceCalls).isEmpty();
    }
}
