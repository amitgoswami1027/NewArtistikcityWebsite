package com.artistikcity.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Collects a student's orders from every order system ({@link OrderSource} beans), newest first, with optional filters.
 * The sources are independent queries, so they run in parallel; if one fails, the others are still shown and the
 * failure is logged (a broken commission query must never hide someone's course orders).
 */
@Service
public class UnifiedOrderService {

    private static final Logger log = LoggerFactory.getLogger(UnifiedOrderService.class);

    /** Filter values the page offers: everything, by order system, or by item category. */
    public static final List<String> TYPES = List.of("all", "course", "workshop", "marketplace", "original", "print", "download", "commission");

    private final List<OrderSource> sources;

    public UnifiedOrderService(List<OrderSource> sources) {
        this.sources = sources;
    }

    public record Result(List<UnifiedOrder> orders, Map<String, Integer> counts, int total) {
    }

    /** All orders of the user, newest first. */
    public List<UnifiedOrder> all(long userId) {
        List<CompletableFuture<List<UnifiedOrder>>> jobs = sources.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> safe(s, userId)))
                .toList();
        List<UnifiedOrder> out = new ArrayList<>();
        for (CompletableFuture<List<UnifiedOrder>> j : jobs) out.addAll(j.join());
        out.sort(NEWEST_FIRST);
        return out;
    }

    private List<UnifiedOrder> safe(OrderSource s, long userId) {
        try {
            List<UnifiedOrder> r = s.forUser(userId);
            return r == null ? List.of() : r;
        } catch (RuntimeException e) {
            log.error("Order source {} failed for user {}", s.getClass().getSimpleName(), userId, e);
            return List.of();
        }
    }

    static final Comparator<UnifiedOrder> NEWEST_FIRST = Comparator.comparing(UnifiedOrder::purchaseDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .reversed()
            .thenComparing(UnifiedOrder::key);

    /**
     * Orders of the user matching {@code type} (see {@link #TYPES}) and the free-text {@code query} (order number, title,
     * artist or variant), plus how many orders each type filter would show (for the filter chips).
     */
    public Result list(long userId, String type, String query) {
        List<UnifiedOrder> all = all(userId);
        String t = type == null || !TYPES.contains(type) ? "all" : type;
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String k : TYPES) counts.put(k, (int) all.stream().filter(o -> matchesType(o, k)).count());
        List<UnifiedOrder> shown = all.stream().filter(o -> matchesType(o, t) && matchesQuery(o, q)).toList();
        return new Result(shown, counts, all.size());
    }

    static boolean matchesType(UnifiedOrder o, String type) {
        return switch (type) {
            case "all" -> true;
            case "course" -> "Digital Course".equals(o.category());
            case "workshop" -> "Workshop".equals(o.category());
            case "marketplace" -> UnifiedOrder.MARKETPLACE.equals(o.orderType());
            case "original" -> "Original Painting".equals(o.category());
            case "print" -> "Canvas Print".equals(o.category()) || "Poster Print".equals(o.category());
            case "download" -> "Digital Download".equals(o.category());
            case "commission" -> UnifiedOrder.COMMISSION.equals(o.orderType());
            default -> false;
        };
    }

    static boolean matchesQuery(UnifiedOrder o, String q) {
        if (q.isEmpty()) return true;
        String hay = String.join(" ", nz(o.orderId()), nz(o.title()), nz(o.subtitle()), nz(o.variant()), nz(o.category()), nz(o.statusLabel()))
                .toLowerCase(Locale.ROOT);
        for (String word : q.split("\\s+")) {
            if (!hay.contains(word)) return false;
        }
        return true;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
