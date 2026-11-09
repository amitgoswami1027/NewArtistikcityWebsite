package com.artistikcity.landing;

import org.junit.jupiter.api.Test;

import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

/** The A/B assignment (sticky, split respected, matches MySQL CRC32) and the confidence figure. */
class LandingAbTestsTest {

    @Test
    void assignmentIsStickyAndFollowsTheSplit() {
        int b = 0;
        for (int i = 0; i < 20000; i++) {
            String v = String.format("%032x", i * 2654435761L);
            String first = LandingAbTests.assign(7, 30, v);
            assertEquals(first, LandingAbTests.assign(7, 30, v), "same visitor, same variant");
            if ("B".equals(first)) b++;
        }
        assertTrue(b > 5600 && b < 6400, "about 30 % see B, got " + b);
        assertEquals("A", LandingAbTests.assign(7, 30, null));
    }

    @Test
    void differentTestsReshuffleVisitors() {
        int moved = 0;
        for (int i = 0; i < 2000; i++) {
            String v = String.format("%032x", i * 40503L);
            if (!LandingAbTests.assign(1, 50, v).equals(LandingAbTests.assign(2, 50, v))) moved++;
        }
        assertTrue(moved > 800 && moved < 1200, "a new test is an independent split, moved " + moved);
    }

    @Test
    void assignmentUsesPlainCrc32SoSqlCanCheckIt() {
        CRC32 c = new CRC32();
        c.update("12:abcdef".getBytes());
        assertEquals(c.getValue() % 100 < 50 ? "B" : "A", LandingAbTests.assign(12, 50, "abcdef"));
    }

    @Test
    void confidence() {
        assertEquals(0.0, LandingAbTests.confidence(0, 0, 5, 100));
        assertEquals(0.0, LandingAbTests.confidence(10, 100, 10, 100), 1e-9);
        // 10 % vs 13 % with 1,000 visitors each: z = 2.10, about 96.4 % two-sided
        assertEquals(0.9645, LandingAbTests.confidence(100, 1000, 130, 1000), 0.003);
        // tiny samples stay unsure
        assertTrue(LandingAbTests.confidence(1, 20, 3, 20) < 0.8);
        assertEquals(0.975, LandingAbTests.phi(1.96), 0.001);
    }
}
