package com.artistikcity.rooms;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomScenesTest {

    private static RoomScenes.Fields f(double x, double y, double w, double h, String widthIn) {
        return new RoomScenes.Fields("Living room", null, x, y, w, h, widthIn == null ? null : new BigDecimal(widthIn), true);
    }

    @Test
    void aGoodRoomHasNoErrors() {
        assertTrue(RoomScenes.validate(f(20, 10, 60, 50, "120")).isEmpty());
        assertTrue(RoomScenes.validate(f(0, 0, 100, 100, null)).isEmpty(), "the whole photo, no scale");
    }

    @Test
    void theAreaMustBeInsideThePhotoAndNotTiny() {
        assertTrue(RoomScenes.validate(f(60, 10, 50, 50, null)).containsKey("area"));
        assertTrue(RoomScenes.validate(f(10, 10, 2, 40, null)).containsKey("area"));
        assertTrue(RoomScenes.validate(f(-1, 10, 40, 40, null)).containsKey("area"));
    }

    @Test
    void nameAndWidthAreChecked() {
        assertTrue(RoomScenes.validate(new RoomScenes.Fields(" ", null, 10, 10, 50, 50, null, true)).containsKey("label"));
        assertTrue(RoomScenes.validate(f(10, 10, 50, 50, "0")).containsKey("widthIn"));
        assertTrue(RoomScenes.validate(f(10, 10, 50, 50, "5000")).containsKey("widthIn"));
    }

    @Test
    void readsTheSizeOfJpgPngAndWebp() throws Exception {
        for (String type : new String[]{"png", "jpg"}) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB), type, out);
            assertArrayEquals(new int[]{1600, 900}, RoomScenes.dimensions(out.toByteArray()), type);
        }
        // a WebP VP8X header for a 2400 x 1600 canvas
        byte[] webp = new byte[40];
        System.arraycopy("RIFF".getBytes(), 0, webp, 0, 4);
        System.arraycopy("WEBPVP8X".getBytes(), 0, webp, 8, 8);
        int w = 2400 - 1, h = 1600 - 1;
        webp[24] = (byte) w; webp[25] = (byte) (w >> 8); webp[26] = (byte) (w >> 16);
        webp[27] = (byte) h; webp[28] = (byte) (h >> 8); webp[29] = (byte) (h >> 16);
        int[] size = RoomScenes.dimensions(webp);
        assertEquals(2400, size[0]);
        assertEquals(1600, size[1]);
        assertNull(RoomScenes.dimensions("not an image at all, just text".getBytes()));
    }
}
