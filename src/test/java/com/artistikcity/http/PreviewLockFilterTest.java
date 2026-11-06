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
        assertTrue(res.getContentAsString().contains("Access password"));
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

    /** A SiteLock without a database: the admin switch as the filter sees it. */
    static final class FakeLock extends SiteLock {
        private final org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder enc = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);
        State s = State.OFF;

        FakeLock() {
            super(null);
        }

        void set(boolean on, String password) {
            String hash = password == null ? "" : enc.encode(password);
            s = new State(on, !hash.isEmpty(), hash, on && !hash.isEmpty() ? SiteLock.token(hash) : "", "", "");
        }

        @Override
        public State state() {
            return s;
        }
    }

    private static Cookie unlockWith(PreviewLockFilter f, String password) throws Exception {
        MockHttpServletRequest ok = new MockHttpServletRequest("POST", PreviewLockFilter.FORM_PATH);
        ok.addParameter("password", password);
        ok.addParameter("next", "/courses");
        MockHttpServletResponse res = run(f, ok, new MockFilterChain());
        return res.getStatus() == 303 ? res.getCookie(PreviewLockFilter.COOKIE) : null;
    }

    private static boolean opens(PreviewLockFilter f, String path, Cookie c) throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", path);
        if (c != null) r.setCookies(c);
        MockFilterChain chain = new MockFilterChain();
        run(f, r, chain);
        return chain.getRequest() != null;
    }

    @Test
    void adminSwitchLocksTheSiteWithTheDatabasePassword() throws Exception {
        FakeLock lock = new FakeLock();
        PreviewLockFilter f = new PreviewLockFilter("", lock, null);
        assertTrue(opens(f, "/marketplace", null), "off by default");

        lock.set(false, "Gallery-Night-26");
        assertTrue(opens(f, "/marketplace", null), "a password alone does not lock");

        lock.set(true, "Gallery-Night-26");
        assertTrue(!opens(f, "/marketplace", null));
        MockHttpServletResponse page = run(f, new MockHttpServletRequest("GET", "/"), new MockFilterChain());
        assertEquals(401, page.getStatus());
        assertTrue(page.getContentAsString().contains("Site under construction"));
        MockHttpServletResponse api = run(f, new MockHttpServletRequest("GET", "/api/v1/cart"), new MockFilterChain());
        assertTrue(api.getContentAsString().contains("PREVIEW_LOCKED"));
        MockHttpServletRequest inertia = new MockHttpServletRequest("GET", "/courses");
        inertia.addHeader("X-Inertia", "true");
        MockHttpServletResponse ir = run(f, inertia, new MockFilterChain());
        assertEquals(409, ir.getStatus());
        assertEquals("/courses", ir.getHeader("X-Inertia-Location"));

        assertNull(unlockWith(f, "wrong-one-123"));
        Cookie c = unlockWith(f, "Gallery-Night-26");
        assertNotNull(c);
        assertTrue(c.getValue().startsWith("s."), "the cookie holds a token, not the password");
        assertTrue(!c.getValue().contains("Gallery"));
        assertTrue(opens(f, "/marketplace", c));

        lock.set(true, "Second-Password-9");            // a new password signs everybody out
        assertTrue(!opens(f, "/marketplace", c));
        assertNotNull(unlockWith(f, "Second-Password-9"));

        lock.set(false, "Second-Password-9");
        assertTrue(opens(f, "/marketplace", null), "switching off opens the site at once");
    }

    @Test
    void adminAreaAndSignedInStaffStayIn() throws Exception {
        FakeLock lock = new FakeLock();
        lock.set(true, "Gallery-Night-26");
        PreviewLockFilter f = new PreviewLockFilter("", lock, null);
        assertTrue(opens(f, "/admin/login", null));
        assertTrue(opens(f, "/admin/parameters", null));
        assertTrue(opens(f, "/admin", null));
        assertTrue(!opens(f, "/administrator", null));

        MockHttpServletRequest staff = new MockHttpServletRequest("GET", "/marketplace");
        staff.getSession(true).setAttribute("auth.admin.id", 1L);
        MockFilterChain chain = new MockFilterChain();
        run(f, staff, chain);
        assertNotNull(chain.getRequest());
    }

    @Test
    void eitherPasswordOpensWhenBothSwitchesAreOn() throws Exception {
        FakeLock lock = new FakeLock();
        lock.set(true, "Gallery-Night-26");
        PreviewLockFilter f = new PreviewLockFilter("server-secret-1", lock, null);
        assertTrue(opens(f, "/", unlockWith(f, "server-secret-1")));
        assertTrue(opens(f, "/", unlockWith(f, "Gallery-Night-26")));
    }

    @Test
    void tooManyWrongPasswordsAreRefusedForAWhile() throws Exception {
        FakeLock lock = new FakeLock();
        lock.set(true, "Gallery-Night-26");
        PreviewLockFilter f = new PreviewLockFilter("", lock, new com.artistikcity.auth.RateLimiter());
        for (int i = 0; i < PreviewLockFilter.MAX_TRIES; i++) assertNull(unlockWith(f, "guess-" + i + "-xxxx"));
        MockHttpServletRequest r = new MockHttpServletRequest("POST", PreviewLockFilter.FORM_PATH);
        r.addParameter("password", "Gallery-Night-26");
        MockHttpServletResponse res = run(f, r, new MockFilterChain());
        assertEquals(429, res.getStatus());
        assertTrue(res.getContentAsString().contains("Too many tries"));
    }

    @Test
    void passwordRules() {
        assertNull(SiteLock.problem("Gallery-Night-26"));
        assertNotNull(SiteLock.problem("short"));
        assertNotNull(SiteLock.problem(" padded-password "));
        assertNotNull(SiteLock.problem("password"));
        assertNotNull(SiteLock.problem("x".repeat(129)));
    }
}
