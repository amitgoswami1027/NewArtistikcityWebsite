package com.artistikcity.support;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Removes hidden metadata from uploaded photos before they are published: GPS position, camera serial numbers, owner
 * names, editing history (EXIF, XMP, IPTC, comments). Photos of artworks are often taken at home, so the GPS tag alone
 * would reveal where a student or artist lives.
 *
 * <ul>
 *   <li><b>JPEG:</b> drops APP1 (EXIF / XMP), APP13 (IPTC / Photoshop), APP3-APP12, APP15 and COM segments without
 *       re-encoding (no quality loss); colour profiles (APP2 ICC) and APP0 / APP14 stay. A photo whose EXIF says it
 *       is rotated is instead decoded, turned upright and re-encoded at {@code app.uploads.jpeg-quality}, because
 *       without the tag it would otherwise show sideways.</li>
 *   <li><b>PNG:</b> drops tEXt, zTXt, iTXt, eXIf and tIME chunks (lossless).</li>
 *   <li><b>WebP:</b> drops the EXIF and XMP chunks and clears their flags in VP8X (lossless).</li>
 * </ul>
 * Anything else, or a file that does not parse, is returned unchanged (the upload checks already accepted it).
 */
public final class ImageMetadata {

    private ImageMetadata() {
    }

    private static final Set<String> PNG_DROP = Set.of("tEXt", "zTXt", "iTXt", "eXIf", "tIME");

    /** Strips metadata when the file name says it is a JPEG, PNG or WebP image. */
    public static byte[] strip(byte[] bytes, String fileName, float jpegQuality) {
        if (bytes == null || bytes.length < 12) return bytes;
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        try {
            if (isJpeg(bytes)) return stripJpeg(bytes, jpegQuality);
            if (isPng(bytes)) return stripPng(bytes);
            if (isWebp(bytes)) return stripWebp(bytes);
        } catch (RuntimeException | IOException e) {
            return bytes;
        }
        return bytes;
    }

    /** Whether a stored path is a public upload whose photo should be cleaned. */
    public static boolean applies(String relativePath) {
        String p = relativePath == null ? "" : relativePath.toLowerCase(Locale.ROOT);
        return p.startsWith("uploads/") && (p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") || p.endsWith(".webp"));
    }

    static boolean isJpeg(byte[] b) {
        return (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    static boolean isPng(byte[] b) {
        return (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    static boolean isWebp(byte[] b) {
        return b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    // ------------------------------------------------------------------ JPEG

    static byte[] stripJpeg(byte[] b, float quality) throws IOException {
        int orientation = jpegOrientation(b);
        if (orientation > 1 && orientation <= 8) {
            byte[] upright = reencodeUpright(b, orientation, quality);
            if (upright != null) return upright;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        out.write(0xFF);
        out.write(0xD8);
        int i = 2;
        while (i + 4 <= b.length) {
            if ((b[i] & 0xFF) != 0xFF) return b;           // not a marker where one must be: leave the file alone
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xFF) { i++; continue; }        // fill byte
            if (marker == 0xDA || marker == 0xD9) {         // start of scan: the rest is image data
                out.write(b, i, b.length - i);
                return out.toByteArray();
            }
            if (marker >= 0xD0 && marker <= 0xD7 || marker == 0x01) { out.write(b, i, 2); i += 2; continue; }
            int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (len < 2 || i + 2 + len > b.length) return b;
            boolean drop = marker == 0xE1 || marker == 0xED || marker == 0xFE || (marker >= 0xE3 && marker <= 0xEC) || marker == 0xEF;
            if (!drop) out.write(b, i, 2 + len);
            i += 2 + len;
        }
        return b;
    }

    /** The EXIF Orientation tag (1 = upright), or 0 when there is none. */
    static int jpegOrientation(byte[] b) {
        int i = 2;
        while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
            int marker = b[i + 1] & 0xFF;
            if (marker == 0xDA) return 0;
            int len = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (marker == 0xE1 && len > 16 && new String(b, i + 4, 4, StandardCharsets.ISO_8859_1).equals("Exif")) {
                int t = i + 10;                              // TIFF header
                boolean le = b[t] == 'I';
                int ifd = t + u32(b, t + 4, le);
                int count = u16(b, ifd, le);
                for (int k = 0; k < count; k++) {
                    int e = ifd + 2 + k * 12;
                    if (e + 12 > b.length) return 0;
                    if (u16(b, e, le) == 0x0112) return u16(b, e + 8, le);
                }
                return 0;
            }
            i += 2 + len;
        }
        return 0;
    }

    private static int u16(byte[] b, int at, boolean le) {
        return le ? (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 : (b[at] & 0xFF) << 8 | (b[at + 1] & 0xFF);
    }

    private static int u32(byte[] b, int at, boolean le) {
        return le ? (b[at] & 0xFF) | (b[at + 1] & 0xFF) << 8 | (b[at + 2] & 0xFF) << 16 | (b[at + 3] & 0xFF) << 24
                : (b[at] & 0xFF) << 24 | (b[at + 1] & 0xFF) << 16 | (b[at + 2] & 0xFF) << 8 | (b[at + 3] & 0xFF);
    }

    private static byte[] reencodeUpright(byte[] b, int orientation, float quality) throws IOException {
        BufferedImage src = ImageIO.read(new ByteArrayInputStream(b));
        if (src == null) return null;
        int w = src.getWidth();
        int h = src.getHeight();
        AffineTransform t = new AffineTransform();
        boolean swap = orientation >= 5;
        switch (orientation) {
            case 2 -> { t.scale(-1, 1); t.translate(-w, 0); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.scale(1, -1); t.translate(0, -h); }
            case 5 -> t = new AffineTransform(0, 1, 1, 0, 0, 0);            // transpose
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> t = new AffineTransform(0, -1, -1, 0, h, w);           // transverse
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { return null; }
        }
        BufferedImage dst = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        new AffineTransformOp(t, AffineTransformOp.TYPE_BICUBIC).filter(toRgb(src), dst);
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam p = writer.getDefaultWriteParam();
            p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            p.setCompressionQuality(quality);
            writer.write(null, new IIOImage(dst, null, null), p);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) return src;
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.createGraphics().drawImage(src, 0, 0, java.awt.Color.WHITE, null);
        return rgb;
    }

    // ------------------------------------------------------------------ PNG

    static byte[] stripPng(byte[] b) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(b.length);
        out.write(b, 0, 8);
        int i = 8;
        while (i + 12 <= b.length) {
            int len = u32(b, i, false);
            if (len < 0 || i + 12 + len > b.length) return b;
            String type = new String(b, i + 4, 4, StandardCharsets.ISO_8859_1);
            if (!PNG_DROP.contains(type)) out.write(b, i, 12 + len);
            i += 12 + len;
            if (type.equals("IEND")) return out.toByteArray();
        }
        return b;
    }

    // ------------------------------------------------------------------ WebP

    static byte[] stripWebp(byte[] b) {
        ByteArrayOutputStream body = new ByteArrayOutputStream(b.length);
        int i = 12;
        int vp8x = -1;
        while (i + 8 <= b.length) {
            String id = new String(b, i, 4, StandardCharsets.ISO_8859_1);
            int len = u32(b, i + 4, true);
            int padded = len + (len & 1);
            if (len < 0 || i + 8 + padded > b.length) return b;
            if (!id.equals("EXIF") && !id.equals("XMP ")) {
                if (id.equals("VP8X")) vp8x = body.size();
                body.write(b, i, 8 + padded);
            }
            i += 8 + padded;
        }
        byte[] chunks = body.toByteArray();
        if (vp8x >= 0) chunks[vp8x + 8] &= (byte) ~0x0C;   // clear the EXIF (0x08) and XMP (0x04) flags
        ByteArrayOutputStream out = new ByteArrayOutputStream(chunks.length + 12);
        out.write(b, 0, 4);
        int size = chunks.length + 4;
        out.write(size & 0xFF);
        out.write(size >> 8 & 0xFF);
        out.write(size >> 16 & 0xFF);
        out.write(size >> 24 & 0xFF);
        out.write(b, 8, 4);
        out.write(chunks, 0, chunks.length);
        return out.toByteArray();
    }
}
