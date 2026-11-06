package com.artistikcity.lifecycle;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The studio's "next step" across the seven-step journey. */
class StudioStageTest {

    @Test
    void stageFollowsTheJourney() {
        List<Map<String, Object>> none = List.of();
        List<Map<String, Object>> enrolled = List.of(Map.of("course_id", 1));
        assertEquals(3, StudentStudioController.stage(none, none, none));
        assertEquals(4, StudentStudioController.stage(enrolled, none, none));
        assertEquals(5, StudentStudioController.stage(enrolled, List.of(Map.of("admin_status", "Pending Review")), none));
        assertEquals(6, StudentStudioController.stage(enrolled, List.of(Map.of("admin_status", "Approved")), List.of(Map.of("eligible", false))));
        assertEquals(7, StudentStudioController.stage(enrolled, List.of(Map.of("admin_status", "Approved")), List.of(Map.of("eligible", true))));
    }
}
