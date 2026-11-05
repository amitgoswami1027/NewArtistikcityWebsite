package com.artistikcity.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoragePublicUrlTest {

    @AfterEach
    void reset() {
        Storage.publicBaseForTests("");
    }

    @Test
    void siteRelativeByDefault() {
        assertEquals("/storage/uploads/marketplace/2026/11/a.jpg", Storage.publicUrl("uploads/marketplace/2026/11/a.jpg"));
        assertEquals(Optional.of("uploads/marketplace/2026/11/a.jpg"), Storage.pathOf("/storage/uploads/marketplace/2026/11/a.jpg"));
    }

    @Test
    void absoluteS3OrCdnUrlWhenConfigured() {
        Storage.publicBaseForTests("https://cdn.artistikcity.com/storage/");
        String url = Storage.publicUrl("uploads/marketplace/2026/11/a.jpg");
        assertEquals("https://cdn.artistikcity.com/storage/uploads/marketplace/2026/11/a.jpg", url);
        assertEquals(Optional.of("uploads/marketplace/2026/11/a.jpg"), Storage.pathOf(url));
        assertEquals(Optional.of("uploads/marketplace/2026/11/a.jpg"), Storage.pathOf(url + "?v=2"));
        // older rows saved as /storage/... keep working
        assertEquals(Optional.of("uploads/x.jpg"), Storage.pathOf("/storage/uploads/x.jpg"));
    }

    @Test
    void rejectsForeignAndUnsafeUrls() {
        Storage.publicBaseForTests("https://cdn.artistikcity.com/storage/");
        assertTrue(Storage.pathOf("https://evil.example.com/storage/uploads/x.jpg").isEmpty());
        assertTrue(Storage.pathOf("https://cdn.artistikcity.com/other/x.jpg").isEmpty());
        assertTrue(Storage.pathOf("/storage/../application.properties").isEmpty());
        assertTrue(Storage.pathOf("/storage/uploads/%2e%2e/%2e%2e/etc/passwd").isEmpty());
        assertTrue(Storage.pathOf("/assets/images/logo.png").isEmpty());
        assertTrue(Storage.pathOf(null).isEmpty());
    }
}
