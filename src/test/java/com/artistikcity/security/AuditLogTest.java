package com.artistikcity.security;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pure parts of the security log: hashing, action types and snapshots (the chain itself: tests/e2e security-log). */
class AuditLogTest {

    private static Map<String, Object> fields() {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("occurred_at", LocalDateTime.of(2026, 11, 6, 10, 15, 30, 123_000_000));
        f.put("actor_type", "admin");
        f.put("actor_id", 1L);
        f.put("action_type", "UPDATE");
        f.put("action", "course updated");
        f.put("summary", "Watercolour basics");
        f.put("outcome", "OK");
        return f;
    }

    @Test
    void theHashDependsOnThePreviousRowAndEveryField() {
        String h = AuditLog.hash(AuditLog.GENESIS, fields());
        assertEquals(64, h.length());
        assertEquals(h, AuditLog.hash(AuditLog.GENESIS, fields()), "same input, same hash");
        assertNotEquals(h, AuditLog.hash("1".repeat(64), fields()), "a different previous row changes the hash");
        Map<String, Object> changed = fields();
        changed.put("summary", "Watercolour basics!");
        assertNotEquals(h, AuditLog.hash(AuditLog.GENESIS, changed), "an edited field changes the hash");
        Map<String, Object> asRead = fields();
        asRead.put("occurred_at", java.sql.Timestamp.valueOf((LocalDateTime) asRead.get("occurred_at")));
        assertEquals(h, AuditLog.hash(AuditLog.GENESIS, asRead), "a row read back as Timestamp hashes the same");
    }

    @Test
    void actionNamesMapToTheFiveChangeTypes() {
        assertEquals(AuditLog.Type.CREATE, AuditLog.typeOf("created"));
        assertEquals(AuditLog.Type.CREATE, AuditLog.typeOf("subscribers added"));
        assertEquals(AuditLog.Type.CREATE, AuditLog.typeOf("video uploaded"));
        assertEquals(AuditLog.Type.DELETE, AuditLog.typeOf("lesson deleted"));
        assertEquals(AuditLog.Type.UPDATE, AuditLog.typeOf("status: Pending → Paid"));
        assertEquals(AuditLog.Type.UPDATE, AuditLog.typeOf("updated"));
    }

    @Test
    void snapshotsMaskSecretsSortKeysAndShortenLongTexts() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", "Asha");
        row.put("password", "$2y$10$abc");
        row.put("remember_token", null);
        row.put("api_secret", "s3cr3t");
        row.put("bio", "x".repeat(5000));
        String json = AuditLog.snapshotJson(row);
        assertFalse(json.contains("$2y$10$abc"));
        assertFalse(json.contains("s3cr3t"));
        assertTrue(json.contains("\"remember_token\":null"), "an empty secret stays visibly empty");
        assertTrue(json.indexOf("\"api_secret\"") < json.indexOf("\"bio\"") && json.indexOf("\"bio\"") < json.indexOf("\"name\""), "keys sorted");
        assertTrue(json.contains("(5000 characters)"));
        assertNull(AuditLog.snapshotJson(null));
    }
}
