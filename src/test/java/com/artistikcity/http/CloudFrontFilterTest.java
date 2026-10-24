package com.artistikcity.http;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Requests behind CloudFront: origin secret, visitor IP, https, spoofed forwarding headers. */
class CloudFrontFilterTest {

    private static final String SECRET = "0123456789abcdef-secret";

    private static MockHttpServletRequest viewerRequest() {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/courses");
        r.setRemoteAddr("130.176.1.1");                       // a CloudFront edge server
        r.addHeader("X-Origin-Verify", SECRET);
        r.addHeader("CloudFront-Viewer-Address", "203.0.113.7:51234");
        r.addHeader("CloudFront-Forwarded-Proto", "https");
        r.addHeader("X-Forwarded-For", "1.2.3.4, 203.0.113.7");
        r.addHeader("X-Forwarded-Proto", "http");            // sent by the visitor: must be ignored
        r.addHeader("X-Forwarded-Host", "evil.example");
        r.addHeader("Host", "www.artistikcity.com");
        r.setServerName("www.artistikcity.com");
        return r;
    }

    private static HttpServletRequest run(CloudFrontFilter f, MockHttpServletRequest r, MockHttpServletResponse res) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        f.doFilter(r, res, chain);
        return (HttpServletRequest) chain.getRequest();
    }

    @Test
    void passesTheVisitorsIpAndHttpsThrough() throws Exception {
        MockHttpServletResponse res = new MockHttpServletResponse();
        HttpServletRequest seen = run(new CloudFrontFilter(true, SECRET), viewerRequest(), res);
        assertEquals(200, res.getStatus());
        assertEquals("203.0.113.7", seen.getRemoteAddr());
        assertTrue(seen.isSecure());
        assertEquals("https", seen.getScheme());
        assertEquals(443, seen.getServerPort());
        assertEquals("https", seen.getHeader("X-Forwarded-Proto"));
        assertNull(seen.getHeader("X-Forwarded-Host"));
        assertEquals("https://www.artistikcity.com/courses", seen.getRequestURL().toString());
    }

    @Test
    void ipv6ViewerAndXffFallback() throws Exception {
        MockHttpServletRequest r = viewerRequest();
        r.removeHeader("CloudFront-Viewer-Address");
        r.addHeader("CloudFront-Viewer-Address", "2001:db8::1:443");
        assertEquals("2001:db8::1", run(new CloudFrontFilter(true, SECRET), r, new MockHttpServletResponse()).getRemoteAddr());
        MockHttpServletRequest r2 = viewerRequest();
        r2.removeHeader("CloudFront-Viewer-Address");
        // the visitor can prepend fake entries, but CloudFront always appends the real address last
        assertEquals("203.0.113.7", run(new CloudFrontFilter(true, SECRET), r2, new MockHttpServletResponse()).getRemoteAddr());
    }

    @Test
    void rejectsRequestsThatDidNotComeThroughCloudFront() throws Exception {
        for (String bad : new String[] {null, "", "wrong", SECRET + "x"}) {
            MockHttpServletRequest r = viewerRequest();
            r.removeHeader("X-Origin-Verify");
            if (bad != null) {
                r.addHeader("X-Origin-Verify", bad);
            }
            MockHttpServletResponse res = new MockHttpServletResponse();
            MockFilterChain chain = new MockFilterChain();
            new CloudFrontFilter(true, SECRET).doFilter(r, res, chain);
            assertEquals(403, res.getStatus(), "secret " + bad);
            assertNull(chain.getRequest(), "request must not reach the app");
        }
    }

    @Test
    void healthCheckNeedsNoSecret() throws Exception {
        MockHttpServletRequest r = new MockHttpServletRequest("GET", "/healthz");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        new CloudFrontFilter(true, SECRET).doFilter(r, res, chain);
        assertEquals(200, res.getStatus());
        assertTrue(chain.getRequest() != null);
    }

    @Test
    void offByDefaultAndRefusesAWeakSecret() throws Exception {
        MockHttpServletRequest r = viewerRequest();
        HttpServletRequest seen = run(new CloudFrontFilter(false, ""), r, new MockHttpServletResponse());
        assertEquals("130.176.1.1", seen.getRemoteAddr());   // untouched when not behind CloudFront
        assertThrows(IllegalStateException.class, () -> new CloudFrontFilter(true, "short"));
        assertThrows(IllegalStateException.class, () -> new CloudFrontFilter(true, null));
    }
}
