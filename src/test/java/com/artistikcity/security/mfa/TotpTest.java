package com.artistikcity.security.mfa;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** TOTP against RFC 6238 Appendix B (SHA-1), Base32, the code window, and the QR encoder's structure. */
class TotpTest {

    private static final String RFC_SECRET = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void base32MatchesRfc4648() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", RFC_SECRET);
        assertEquals("12345678901234567890", new String(Totp.unbase32("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"), StandardCharsets.US_ASCII));
        assertEquals(32, Totp.newSecret().length());
    }

    @Test
    void rfc6238TestVectors() {
        long[][] v = {{59, 94287082}, {1111111109, 7081804}, {1111111111, 14050471}, {1234567890, 89005924}, {2000000000, 69279037}, {20000000000L, 65353130}};
        for (long[] t : v) {
            assertEquals(String.format("%08d", t[1]), Totp.code(RFC_SECRET, Totp.step(t[0]), 8), "T=" + t[0]);
        }
        assertEquals("287082", Totp.code(RFC_SECRET, Totp.step(59)));
    }

    @Test
    void acceptsOneStepOfClockDriftOnly() {
        String s = Totp.newSecret();
        long now = 1_000_000;
        assertEquals(now, Totp.match(s, Totp.code(s, now), now));
        assertEquals(now - 1, Totp.match(s, Totp.code(s, now - 1), now));
        assertEquals(now + 1, Totp.match(s, Totp.code(s, now + 1), now));
        assertEquals(-1, Totp.match(s, Totp.code(s, now - 2), now));
        assertEquals(now, Totp.match(s, Totp.code(s, now).substring(0, 3) + " " + Totp.code(s, now).substring(3), now));
        assertEquals(-1, Totp.match(s, "12345", now));
        assertEquals(-1, Totp.match(s, null, now));
    }

    @Test
    void otpauthUri() {
        String u = Totp.uri("ArtistikCity", "admin@artistikcity.com", "ABC");
        assertEquals("otpauth://totp/ArtistikCity:admin%40artistikcity.com?secret=ABC&issuer=ArtistikCity&algorithm=SHA1&digits=6&period=30", u);
    }

    @Test
    void qrCodeHasFinderPatternsAndGrowsWithData() {
        QrCode small = QrCode.encode("hello");
        assertEquals(1, small.version);
        assertEquals(21, small.size);
        // finder pattern corners: dark ring, light ring, dark centre
        for (int[] c : new int[][]{{0, 0}, {small.size - 7, 0}, {0, small.size - 7}}) {
            assertTrue(small.get(c[0], c[1]));
            assertFalse(small.get(c[0] + 1, c[1] + 1));
            assertTrue(small.get(c[0] + 3, c[1] + 3));
        }
        QrCode big = QrCode.encode(Totp.uri("ArtistikCity", "someone.with.a.long.address@artistikcity.com", Totp.newSecret()));
        assertTrue(big.version >= 6 && big.version <= 9, "version " + big.version);
        assertTrue(big.toSvg(2, "x").startsWith("<svg"));
    }
}
