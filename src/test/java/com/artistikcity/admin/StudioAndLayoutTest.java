package com.artistikcity.admin;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Studio Stories read time / workflow names and the SEO & Layout Controls link and menu checks. */
class StudioAndLayoutTest {

    @Test
    void readTimeIsWordsOverTwoHundredRoundedUp() {
        assertEquals(0, BlogAdminController.readMinutes(""));
        assertEquals(1, BlogAdminController.readMinutes("<p>Just a few words</p>"));
        assertEquals(3, BlogAdminController.readMinutes("<p>" + "word ".repeat(450) + "</p>"));
    }

    @Test
    void workflowNames() {
        assertEquals("draft", BlogAdminController.workflowName(0));
        assertEquals("published", BlogAdminController.workflowName(1));
        assertEquals("archived", BlogAdminController.workflowName(2));
        assertEquals("review", BlogAdminController.workflowName(3));
        assertEquals("scheduled", BlogAdminController.workflowName(4));
    }

    @Test
    void siteLinksArePathsOnThisSiteOrHttps() {
        assertTrue(ContentAdminController.siteLink("/courses?type=all"));
        assertTrue(ContentAdminController.siteLink("https://www.instagram.com/artistikcity"));
        assertFalse(ContentAdminController.siteLink("//evil.com"));
        assertFalse(ContentAdminController.siteLink("javascript:alert(1)"));
        assertFalse(ContentAdminController.siteLink("http://plain.example.com"));
        assertFalse(ContentAdminController.siteLink("/a\"onmouseover=x"));
    }

    @Test
    void menuChecks() {
        assertNull(ContentAdminController.checkMenu(List.of(Map.of("label", "Courses", "href", "/courses?type=all"))));
        assertNotNull(ContentAdminController.checkMenu("not a list"));
        assertNotNull(ContentAdminController.checkMenu(List.of()));
        List<Object> eleven = new ArrayList<>();
        for (int i = 0; i < 11; i++) eleven.add(Map.of("label", "L" + i, "href", "/x"));
        assertTrue(ContentAdminController.checkMenu(eleven).contains("10"));
        assertTrue(ContentAdminController.checkMenu(List.of(Map.of("label", "A", "href", "/a"), Map.of("label", "a", "href", "/b"))).contains("twice"));
        assertNotNull(ContentAdminController.checkMenu(List.of(Map.of("label", "", "href", "/a"))));
        assertNotNull(ContentAdminController.checkMenu(List.of(Map.of("label", "Evil", "href", "javascript:alert(1)"))));
        var norm = ContentAdminController.normalizeMenu(List.of(Map.of("label", " Shop ", "href", "/marketplace", "visible", false, "accent", "true")));
        assertEquals("Shop", norm.get(0).get("label"));
        assertEquals(false, norm.get(0).get("visible"));
        assertEquals(true, norm.get(0).get("accent"));
    }
}
