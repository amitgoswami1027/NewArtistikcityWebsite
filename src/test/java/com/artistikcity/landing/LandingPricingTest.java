package com.artistikcity.landing;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** One quote serves the page, the checkout and the gateway amount: these are its rules. */
class LandingPricingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 7, 12, 0);
    private static final BigDecimal COURSE = new BigDecimal("4999");
    private static final BigDecimal COURSE_USD = new BigDecimal("69");

    private static LandingPricing.Rules rules(String price, String list, String promo, LocalDateTime promoEnds, String early, LocalDateTime earlyEnds,
                                              String taxMode, String taxRate, boolean coupons) {
        return new LandingPricing.Rules(list == null ? null : new BigDecimal(list), price == null ? null : new BigDecimal(price), null, "Diwali offer",
                promo == null ? null : new BigDecimal(promo), null, promoEnds, early == null ? null : new BigDecimal(early), earlyEnds, coupons, taxMode,
                new BigDecimal(taxRate), "GST");
    }

    private static BigDecimal d(String s) {
        return new BigDecimal(s).setScale(2);
    }

    @Test
    void noRulesUsesTheCoursePrice() {
        LandingPricing.Quote q = LandingPricing.quote(null, COURSE, COURSE_USD, NOW, null);
        assertEquals(d("4999"), q.total());
        assertEquals(d("69"), q.totalUsd());
        assertNull(q.offerPrice());
    }

    @Test
    void offerOnlyWhileItsWindowIsOpen() {
        LandingPricing.Rules r = rules("3500", "4999", "2999", NOW.plusDays(2), null, null, "INCLUDED", "0", true);
        LandingPricing.Quote on = LandingPricing.quote(r, COURSE, COURSE_USD, NOW, null);
        assertEquals(d("2999"), on.total());
        assertEquals("Diwali offer", on.offerLabel());
        assertEquals(NOW.plusDays(2), on.offerEnds());
        LandingPricing.Quote after = LandingPricing.quote(r, COURSE, COURSE_USD, NOW.plusDays(3), null);
        assertEquals(d("3500"), after.total(), "an ended offer ends by itself");
        assertNull(after.offerEnds());
    }

    @Test
    void earlyBirdWinsOnlyWhenLower() {
        LandingPricing.Rules r = rules("4999", null, "3999", NOW.plusDays(5), "3499", NOW.plusDays(1), "INCLUDED", "0", true);
        assertEquals(d("3499"), LandingPricing.quote(r, COURSE, null, NOW, null).total());
        assertEquals("Early-bird price", LandingPricing.quote(r, COURSE, null, NOW, null).offerLabel());
        assertEquals(d("3999"), LandingPricing.quote(r, COURSE, null, NOW.plusDays(2), null).total(), "after the early bird, the offer");
    }

    @Test
    void percentCouponsRoundToWholeRupees() {
        LandingPricing.Rules r = rules("3500", null, "2999", NOW.plusDays(1), null, null, "INCLUDED", "18", true);
        LandingPricing.Quote q = LandingPricing.quote(r, COURSE, null, NOW, new LandingPricing.Discount("LPT10", "PERCENT", new BigDecimal("10")));
        assertEquals(d("300"), q.discount());
        assertEquals(d("2699"), q.total());
        assertEquals("LPT10", q.couponCode());
    }

    @Test
    void flatCouponNeverBelowZeroAndIgnoredWhenCouponsAreOff() {
        LandingPricing.Rules on = rules("1000", null, null, null, null, null, "INCLUDED", "0", true);
        LandingPricing.Discount big = new LandingPricing.Discount("BIG", "FLAT", new BigDecimal("5000"));
        assertEquals(d("0"), LandingPricing.quote(on, COURSE, null, NOW, big).total());
        LandingPricing.Rules off = rules("1000", null, null, null, null, null, "INCLUDED", "0", false);
        assertEquals(d("1000"), LandingPricing.quote(off, COURSE, null, NOW, big).total());
        assertNull(LandingPricing.quote(off, COURSE, null, NOW, big).couponCode());
    }

    @Test
    void taxIncludedVsAdded() {
        LandingPricing.Quote inc = LandingPricing.quote(rules("1180", null, null, null, null, null, "INCLUDED", "18", true), COURSE, null, NOW, null);
        assertEquals(d("1180"), inc.total());
        assertEquals(d("180"), inc.tax());
        LandingPricing.Quote add = LandingPricing.quote(rules("1000", null, null, null, null, null, "ADDED", "18", true), COURSE, null, NOW, null);
        assertEquals(d("1180"), add.total());
        assertEquals(d("180"), add.tax());
    }

    @Test
    void usdFollowsTheSameDiscountShare() {
        LandingPricing.Rules r = rules("5000", null, "4000", NOW.plusDays(1), null, null, "INCLUDED", "0", true);
        assertEquals(d("55.20"), LandingPricing.quote(r, COURSE, COURSE_USD, NOW, null).totalUsd());
    }

    @Test
    void listPriceNeverBelowThePrice() {
        LandingPricing.Quote q = LandingPricing.quote(rules("3000", "2000", null, null, null, null, "INCLUDED", "0", true), COURSE, null, NOW, null);
        assertEquals(d("3000"), q.list());
        assertEquals(d("0"), q.saving());
    }

    @Test
    void pricesShowPaiseWithTwoDigitsOnly() {
        assertEquals("₹2,699", LandingView.inr(new BigDecimal("2699.00")));
        assertEquals("₹3,538.80", LandingView.inr(new BigDecimal("3538.8")));
        assertEquals("₹1,23,456", LandingView.inr(new BigDecimal("123456")));
        assertEquals("₹12,34,56,789.50", LandingView.inr(new BigDecimal("123456789.5")));
        assertEquals("₹999", LandingView.inr(new BigDecimal("999")));
    }

    // ------------------------------------------------------------------------------------- page configuration

    @Test
    void newPagesAreValidForEveryTemplate() {
        for (LandingCatalog.Template t : LandingCatalog.TEMPLATES) {
            LandingConfig.Result r = LandingConfig.normalize(LandingConfig.initial(t));
            assertTrue(r.ok(), t.key() + " " + r.errors());
            assertEquals("hero", ((Map<?, ?>) ((List<?>) r.config().get("sections")).get(0)).get("type"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void normalizeRejectsUnsafeValuesAndCleansHtml() {
        Map<String, Object> c = LandingConfig.initial(LandingCatalog.TEMPLATES.get(0));
        ((Map<String, Object>) c.get("design")).put("accent", "red");
        ((Map<String, Object>) c.get("seo")).put("canonical", "javascript:alert(1)");
        ((Map<String, Object>) c.get("marketing")).put("gtm", "<script>");
        Map<String, Object> custom = LandingConfig.section("custom", "single");
        ((Map<String, Object>) custom.get("content")).put("html", "<p>ok</p><script>alert(1)</script><a href=\"javascript:x\" onclick=\"y\">l</a>");
        ((List<Object>) c.get("sections")).add(custom);
        c.put("unknownKey", "dropped");
        LandingConfig.Result r = LandingConfig.normalize(c);
        assertTrue(r.errors().containsKey("design.accent"));
        assertTrue(r.errors().containsKey("seo.canonical"));
        assertTrue(r.errors().containsKey("marketing.gtm"));
        assertFalse(r.config().containsKey("unknownKey"));
        List<Map<String, Object>> sections = (List<Map<String, Object>>) r.config().get("sections");
        String html = (String) ((Map<String, Object>) sections.get(sections.size() - 1).get("content")).get("html");
        assertFalse(html.contains("<script"));
        assertFalse(html.contains("onclick"));
        assertFalse(html.contains("javascript:"));
        assertTrue(html.contains("<p>ok</p>"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void heroMustStayFirstAndSingleSectionsOnce() {
        Map<String, Object> c = LandingConfig.initial(LandingCatalog.TEMPLATES.get(0));
        List<Object> s = (List<Object>) c.get("sections");
        Object hero = s.remove(0);
        s.add(hero);
        assertTrue(LandingConfig.normalize(c).errors().containsKey("sections"));
        Map<String, Object> c2 = LandingConfig.initial(LandingCatalog.TEMPLATES.get(0));
        ((List<Object>) c2.get("sections")).add(LandingConfig.section("hero", "split"));
        assertTrue(LandingConfig.normalize(c2).errors().keySet().stream().anyMatch(k -> k.endsWith(".type")));
    }
}
