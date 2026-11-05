package com.artistikcity.support;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Uploaded photos lose GPS / camera metadata, stay valid images and stay upright. */
class ImageMetadataTest {

    private static final String SECRET = "GPS 18.5204N 73.8567E Canon serial 0042";

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) img.setRGB(x, y, x < w / 2 ? 0xC0392B : 0x2C3E50);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    /** An APP1 Exif segment (big-endian TIFF, one IFD entry: Orientation) followed by the secret text. */
    private static byte[] exifSegment(int orientation) {
        ByteArrayOutputStream tiff = new ByteArrayOutputStream();
        tiff.writeBytes(new byte[]{'M', 'M', 0, 42, 0, 0, 0, 8});        // header, IFD at 8
        tiff.writeBytes(new byte[]{0, 1});                                // one entry
        tiff.writeBytes(new byte[]{0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0});
        tiff.writeBytes(new byte[]{0, 0, 0, 0});                          // no next IFD
        tiff.writeBytes(SECRET.getBytes(StandardCharsets.US_ASCII));
        byte[] t = tiff.toByteArray();
        int len = 2 + 6 + t.length;
        ByteArrayOutputStream seg = new ByteArrayOutputStream();
        seg.writeBytes(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (len >> 8), (byte) len});
        seg.writeBytes("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1));
        seg.writeBytes(t);
        return seg.toByteArray();
    }

    private static byte[] withExif(byte[] jpeg, int orientation) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.writeBytes(exifSegment(orientation));
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static boolean contains(byte[] b, String s) {
        return new String(b, StandardCharsets.ISO_8859_1).contains(s);
    }

    @Test
    void jpegExifIsRemovedWithoutReEncoding() throws Exception {
        byte[] plain = image("jpg", 40, 20);
        byte[] tagged = withExif(plain, 1);
        assertTrue(contains(tagged, SECRET));
        byte[] clean = ImageMetadata.strip(tagged, "photo.jpg", 0.92f);
        assertFalse(contains(clean, SECRET));
        assertFalse(contains(clean, "Exif"));
        assertArrayEquals(plain, clean, "removing the segment gives back the original bytes: no quality loss");
    }

    @Test
    void aRotatedJpegIsTurnedUprightAndLosesItsTags() throws Exception {
        byte[] tagged = withExif(image("jpg", 40, 20), 6);             // camera held sideways
        assertEquals(6, ImageMetadata.jpegOrientation(tagged));
        byte[] clean = ImageMetadata.strip(tagged, "photo.jpeg", 0.92f);
        assertFalse(contains(clean, SECRET));
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(clean));
        assertNotNull(img);
        assertEquals(20, img.getWidth(), "width and height swap for a 90 degree rotation");
        assertEquals(40, img.getHeight());
        // the left (red) half of the stored image is the top half after turning it clockwise
        int top = img.getRGB(10, 5);
        assertTrue(((top >> 16) & 0xFF) > 150, "red ends up on top");
    }

    @Test
    void pngTextChunksAreRemoved() throws Exception {
        byte[] png = image("png", 8, 8);
        byte[] data = ("Comment\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream chunk = new ByteArrayOutputStream();
        chunk.writeBytes(new byte[]{0, 0, 0, (byte) data.length});
        byte[] typeAndData = new byte[4 + data.length];
        System.arraycopy("tEXt".getBytes(StandardCharsets.ISO_8859_1), 0, typeAndData, 0, 4);
        System.arraycopy(data, 0, typeAndData, 4, data.length);
        chunk.writeBytes(typeAndData);
        CRC32 crc = new CRC32();
        crc.update(typeAndData);
        long c = crc.getValue();
        chunk.writeBytes(new byte[]{(byte) (c >> 24), (byte) (c >> 16), (byte) (c >> 8), (byte) c});
        int ihdrEnd = 8 + 25;                                           // signature + IHDR chunk
        ByteArrayOutputStream tagged = new ByteArrayOutputStream();
        tagged.write(png, 0, ihdrEnd);
        tagged.writeBytes(chunk.toByteArray());
        tagged.write(png, ihdrEnd, png.length - ihdrEnd);
        byte[] clean = ImageMetadata.strip(tagged.toByteArray(), "a.png", 0.92f);
        assertFalse(contains(clean, SECRET));
        assertArrayEquals(png, clean);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(clean)));
    }

    @Test
    void webpExifAndXmpChunksAreRemovedAndFlagsCleared() {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(StandardCharsets.ISO_8859_1));
        body.writeBytes(new byte[]{'V', 'P', '8', 'X', 10, 0, 0, 0, 0x0C, 0, 0, 0, 7, 0, 0, 7, 0, 0});   // EXIF + XMP flags set
        body.writeBytes(new byte[]{'V', 'P', '8', 'L', 4, 0, 0, 0, 1, 2, 3, 4});
        byte[] secret = SECRET.getBytes(StandardCharsets.ISO_8859_1);
        body.writeBytes(new byte[]{'E', 'X', 'I', 'F', (byte) secret.length, 0, 0, 0});
        body.writeBytes(secret);
        if (secret.length % 2 == 1) body.write(0);
        body.writeBytes(new byte[]{'X', 'M', 'P', ' ', 2, 0, 0, 0, 'x', 'y'});
        byte[] b = body.toByteArray();
        ByteArrayOutputStream riff = new ByteArrayOutputStream();
        riff.writeBytes(new byte[]{'R', 'I', 'F', 'F', (byte) b.length, (byte) (b.length >> 8), 0, 0});
        riff.writeBytes(b);
        byte[] clean = ImageMetadata.strip(riff.toByteArray(), "a.webp", 0.92f);
        assertFalse(contains(clean, SECRET));
        assertFalse(contains(clean, "XMP "));
        assertEquals(clean.length - 8, (clean[4] & 0xFF) | (clean[5] & 0xFF) << 8, "RIFF size matches the new length");
        assertEquals(0, clean[20] & 0x0C, "VP8X no longer announces EXIF / XMP");
        assertTrue(contains(clean, "VP8L"));
    }

    @Test
    void onlyPublicPhotoUploadsAreTouched() {
        assertTrue(ImageMetadata.applies("uploads/marketplace/2026/11/a.JPG"));
        assertFalse(ImageMetadata.applies("certificates/x.png"));
        assertFalse(ImageMetadata.applies("uploads/courses/1/schedule.pdf"));
        byte[] junk = "not an image at all".getBytes(StandardCharsets.US_ASCII);
        assertArrayEquals(junk, ImageMetadata.strip(junk, "x.jpg", 0.9f));
    }
}
