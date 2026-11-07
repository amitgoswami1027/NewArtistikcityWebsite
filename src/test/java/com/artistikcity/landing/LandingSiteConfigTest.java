package com.artistikcity.landing;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Site pages (no course) and the new sections: what the editor's config may and may not contain. */
class LandingSiteConfigTest {

    private static Map<String, Object> siteConfig() {
        return LandingConfig.initial(LandingCatalog.template("platform-showcase"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sections(Map<String, Object> c) {
        return (List<Map<String, Object>>) c.get("sections");
    }

    @Test
    void siteTemplatesAreSitePagesAndCourseTemplatesStayCourse() {
        assertEquals("SITE", LandingCatalog.template("platform-showcase").kind());
        assertEquals("SITE", LandingCatalog.template("commission-studio").kind());
        assertEquals("COURSE", LandingCatalog.template("watercolour-wash").kind());
        assertEquals("COURSE", LandingCatalog.template("artist-showcase").kind());
        for (LandingCatalog.Template t : LandingCatalog.TEMPLATES) {
            if ("SITE".equals(t.kind())) {
                assertTrue(t.sections().stream().noneMatch(LandingCatalog.COURSE_ONLY::contains), t.key());
            }
            for (String s : t.sections()) assertNotNull(LandingCatalog.section(s), t.key() + ": " + s);
        }
    }

    @Test
    void aNewSitePageIsValidAsCreated() {
        LandingConfig.Result r = LandingConfig.normalize(siteConfig(), true);
        assertTrue(r.ok(), r.errors().toString());
        Map<?, ?> cta = (Map<?, ?>) r.config().get("cta");
        assertEquals("/courses?type=all", cta.get("primaryHref"));
        assertEquals(false, cta.get("sticky"));
    }

    @Test
    void courseOnlySectionsAreRefusedOnSitePagesButFineOnCoursePages() {
        Map<String, Object> c = siteConfig();
        sections(c).add(LandingConfig.section("pricing", "card"));
        assertFalse(LandingConfig.normalize(c, true).ok());
        Map<String, Object> course = LandingConfig.initial(LandingCatalog.template("watercolour-wash"));
        assertTrue(LandingConfig.normalize(course, false).ok(), LandingConfig.normalize(course, false).errors().toString());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theMainButtonOfASitePageNeedsAnAddress() {
        Map<String, Object> c = siteConfig();
        ((Map<String, Object>) c.get("cta")).put("primaryHref", "");
        assertTrue(LandingConfig.normalize(c, true).errors().containsKey("cta.primaryHref"));
        assertFalse(LandingConfig.normalize(c, false).errors().containsKey("cta.primaryHref"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void linksMayBeSitePathsHttpsOrSectionAnchorsNothingElse() {
        Map<String, Object> c = siteConfig();
        Map<String, Object> cards = LandingConfig.section("cards", "grid");
        List<Object> items = new ArrayList<>();
        items.add(new LinkedHashMap<>(Map.of("title", "A", "link", "/marketplace", "linkLabel", "Go")));
        items.add(new LinkedHashMap<>(Map.of("title", "B", "link", "#meet-artists")));
        items.add(new LinkedHashMap<>(Map.of("title", "C", "link", "https://example.com/x")));
        ((Map<String, Object>) cards.get("content")).put("items", items);
        sections(c).add(cards);
        LandingConfig.Result ok = LandingConfig.normalize(c, true);
        assertTrue(ok.ok(), ok.errors().toString());
        List<Map<String, Object>> kept = (List<Map<String, Object>>) ((Map<String, Object>) sections(ok.config()).get(sections(ok.config()).size() - 1).get("content")).get("items");
        assertEquals("#meet-artists", kept.get(1).get("link"));
        for (String bad : List.of("javascript:alert(1)", "//evil.example", "http://plain.example", "/a/../admin", "#Bad Anchor")) {
            items.set(0, new LinkedHashMap<>(Map.of("title", "A", "link", bad)));
            assertFalse(LandingConfig.normalize(c, true).ok(), bad);
        }
        // an anchor is never accepted as an image
        Map<String, Object> hero = sections(c).get(0);
        ((Map<String, Object>) hero.get("content")).put("image", "#hero");
        assertFalse(LandingConfig.normalize(c, true).ok());
    }

    @Test
    @SuppressWarnings("unchecked")
    void sourcesLimitsAndCollageAreCleaned() {
        Map<String, Object> c = siteConfig();
        Map<String, Object> courses = LandingConfig.section("courses", "cards");
        ((Map<String, Object>) courses.get("content")).put("limit", 99);
        Map<String, Object> gallery = LandingConfig.section("gallery", "masonry");
        ((Map<String, Object>) gallery.get("content")).put("source", "everything");
        sections(c).add(courses);
        sections(c).add(gallery);
        ((Map<String, Object>) sections(c).get(0).get("content")).put("images", List.of("/a.jpg", "/b.jpg", "/c.jpg", "/d.jpg", "/e.jpg"));
        LandingConfig.Result r = LandingConfig.normalize(c, true);
        assertTrue(r.ok(), r.errors().toString());
        List<Map<String, Object>> out = sections(r.config());
        assertEquals(12, ((Map<?, ?>) out.get(out.size() - 2).get("content")).get("limit"));
        assertEquals("course", ((Map<?, ?>) out.get(out.size() - 1).get("content")).get("source"));
        assertEquals(4, ((List<?>) ((Map<?, ?>) out.get(0).get("content")).get("images")).size());
    }

    @Test
    void textureIsOneOfTheKnownOnes() {
        Map<String, Object> c = siteConfig();
        @SuppressWarnings("unchecked") Map<String, Object> d = (Map<String, Object>) c.get("design");
        d.put("texture", "url(evil)");
        assertEquals("none", ((Map<?, ?>) LandingConfig.normalize(c, true).config().get("design")).get("texture"));
        d.put("texture", "wash");
        assertEquals("wash", ((Map<?, ?>) LandingConfig.normalize(c, true).config().get("design")).get("texture"));
    }

    @Test
    void onlyTheListedSiteAddressesExist() {
        assertEquals("/how-it-works", LandingCatalog.siteRoute("how-it-works").path());
        assertEquals("/commission/step-1", LandingCatalog.siteRoute("commission-step-1").path());
        assertNull(LandingCatalog.siteRoute("login"));
        assertNull(LandingCatalog.siteRoute("../admin"));
    }

    @Test
    void commissionStylesMatchThePriceList() {
        assertEquals(com.artistikcity.commission.CommissionPricing.THEMES, LandingView.COMMISSION_THEMES.stream().map(m -> m.get("id")).toList());
        assertEquals(6, LandingView.COMMISSION_STEPS.size());
    }
}
