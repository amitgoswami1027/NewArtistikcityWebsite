package com.artistikcity.admin;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The upload check for course and lesson videos: extension and file signature must both say "video". */
class VideoUploadCheckTest {

    private static final byte[] MP4 = {0, 0, 0, 0x20, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0, 0, 0, 0};

    @Test
    void recognisesMp4AndWebmSignatures() {
        assertTrue(CourseAdminController.isVideo(MP4, MP4.length));
        assertTrue(CourseAdminController.isVideo(WEBM, WEBM.length));
        assertFalse(CourseAdminController.isVideo("not a video".getBytes(StandardCharsets.US_ASCII), 11));
        assertFalse(CourseAdminController.isVideo(MP4, 4));   // too short to tell
    }

    @Test
    void acceptsRealVideosWithVideoExtensions() {
        assertNull(CourseAdminController.checkVideo(new MockMultipartFile("file", "lesson-1.mp4", "video/mp4", MP4)));
        assertNull(CourseAdminController.checkVideo(new MockMultipartFile("file", "Trailer.MOV", "video/quicktime", MP4)));
        assertNull(CourseAdminController.checkVideo(new MockMultipartFile("file", "intro.webm", "video/webm", WEBM)));
    }

    @Test
    void rejectsRenamedFilesAndOtherTypes() {
        // a text file renamed to .mp4, a real video with a wrong extension, and an image
        assertNotNull(CourseAdminController.checkVideo(new MockMultipartFile("file", "fake.mp4", "video/mp4", "hello".getBytes(StandardCharsets.UTF_8))));
        assertNotNull(CourseAdminController.checkVideo(new MockMultipartFile("file", "clip.exe", "video/mp4", MP4)));
        assertNotNull(CourseAdminController.checkVideo(new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})));
    }
}
