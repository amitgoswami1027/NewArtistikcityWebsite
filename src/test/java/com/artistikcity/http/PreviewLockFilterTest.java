package com.artistikcity.http;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewLockFilterTest {

    private static MockHttpServletResponse run(PreviewLockFilter f, MockHttpServletRequest req, MockFilterChain chain) throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        f.doFilter(req, res, chain);
        return res;
    }

    @Test
    void offWithoutPassword() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse res = run(new PreviewLockFilter(""), new MockHttpServletRequest("GET", "/"), chain);
        assertNotNull(chain.getRequest());
        assertNull(res.getHeader("X-Robots-Tag"));
    }

    @Test
    void locksPagesAndApisButNotHealthWebhooksOrAssets() throws Exception {
        PreviewLockFilter f = new PreviewLockFilter("s3cret-preview");
        MockFilterChain page = new MockFilterChain();
        MockHttpServletResponse res = run(f, new MockHttpServletRequest("GET", "/marketplace"), page);
        assertNull(page.getRequest());
        assertEquals(401, res.getStatus());
        assertTrue(res.getContentAsString().contains("Preview password"));
        assertEquals("noindex, nofollow, noarchive", res.getHeader("X-Robots-Tag"));

        MockFilterChain api = new MockFilterChain();
        res = run(f, new MockHttpServletRequest("GET", "/api/v1/cart"), api);
        assertNull(api.getRequest());
        assertTrue(res.getContentAsString().contains("PREVIEW_LOCKED"));

        for (String open : new String[]{"/healthz", "/api/v1/courses/razorpay-webhook", "/api/v1/marketplace/webhook", "/assets/images/logo.png", "/js/app.js"}) {
            MockFilterChain c = new MockFilterChain();
            run(f, new MockHttpServletRequest("POST", open), c);
            assertNotNull(c.getRequest(), open);
        }
        res = run(f, new MockHttpServletRequest("GET", "/robots.txt"), new MockFilterChain());
        assertTrue(res.getContentAsString().contains("Disallow: /"));
    }

    @Test
    void rightPasswordSetsCookieThatOpensTheSite() throws Exception {
        PreviewLockFilter f = new PreviewLockFilter("s3cret-preview");
        MockHttpServletRequest wrong = new MockHttpServletRequest("POST", PreviewLockFilter.FORM_PATH);
        wrong.addParameter("password", "nope");
        MockHttpServletResponse res = run(f, wrong, new MockFilterChain());
        assertEquals(401, res.getStatus());
        assertNull(res.getCookie(PreviewLockFilter.COOKIE));

        MockHttpServletRequest ok = new MockHttpServletRequest("POST", PreviewLockFilter.FORM_PATH);
        ok.addParameter("password", "s3cret-preview");
        ok.addParameter("next", "/marketplace?x=1");
        res = run(f, ok, new MockFilterChain());
        assertEquals(303, res.getStatus());
        assertEquals("/marketplace?x=1", res.getHeader("Location"));
        Cookie c = res.getCookie(PreviewLockFilter.COOKIE);
        assertNotNull(c);
        assertTrue(c.isHttpOnly());

        MockHttpServletRequest back = new MockHttpServletRequest("GET", "/marketplace");
        back.setCookies(c);
        MockFilterChain chain = new MockFilterChain();
        run(f, back, chain);
        assertNotNull(chain.getRequest());

        MockHttpServletRequest forged = new MockHttpServletRequest("GET", "/marketplace");
        forged.setCookies(new Cookie(PreviewLockFilter.COOKIE, "0000"));
        MockFilterChain chain2 = new MockFilterChain();
        run(f, forged, chain2);
        assertNull(chain2.getRequest());
    }

    @Test
    void nextOnlyStaysOnThisSite() {
        assertEquals("/", PreviewLockFilter.safeNext("https://evil.example.com"));
        assertEquals("/", PreviewLockFilter.safeNext("//evil.example.com"));
        assertEquals("/", PreviewLockFilter.safeNext("/\\evil.example.com"));
        assertEquals("/courses", PreviewLockFilter.safeNext("/courses"));
    }
}
