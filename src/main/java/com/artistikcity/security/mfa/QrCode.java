package com.artistikcity.security.mfa;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A small QR Code encoder (ISO/IEC 18004, byte mode, error correction level M, versions 1-40), drawn as SVG, so the
 * authenticator set-up page needs no library and never sends the secret anywhere. The algorithm follows Project Nayuki's
 * reference implementation (MIT licence): data codewords, Reed-Solomon error correction, interleaving, function patterns,
 * the eight masks with the standard penalty score, format and version information.
 */
public final class QrCode {

    private static final byte[] ECC_PER_BLOCK_M = {-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28,
            28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28};
    private static final byte[] BLOCKS_M = {-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29,
            31, 33, 35, 37, 38, 40, 43, 45, 47, 49};
    private static final int ECC_FORMAT_BITS_M = 0;   // level M = 00 in the format information

    public final int version;
    public final int size;
    private final boolean[][] modules;
    private final boolean[][] isFunction;

    private QrCode(int version) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];
    }

    public boolean get(int x, int y) {
        return x >= 0 && x < size && y >= 0 && y < size && modules[y][x];
    }

    /** Encodes text (UTF-8, byte mode) at error correction level M in the smallest version that fits. */
    public static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        for (; ; version++) {
            if (version > 40) throw new IllegalArgumentException("Text too long for a QR code");
            int cap = numDataCodewords(version) * 8;
            int used = 4 + (version <= 9 ? 8 : 16) + data.length * 8;
            if (used <= cap) break;
        }
        // data bits
        List<Integer> bits = new ArrayList<>();
        appendBits(bits, 0x4, 4);
        appendBits(bits, data.length, version <= 9 ? 8 : 16);
        for (byte b : data) appendBits(bits, b & 0xff, 8);
        int capBits = numDataCodewords(version) * 8;
        appendBits(bits, 0, Math.min(4, capBits - bits.size()));
        appendBits(bits, 0, (8 - bits.size() % 8) % 8);
        for (int pad = 0xEC; bits.size() < capBits; pad ^= 0xEC ^ 0x11) appendBits(bits, pad, 8);
        byte[] codewords = new byte[bits.size() / 8];
        for (int i = 0; i < bits.size(); i++) codewords[i >>> 3] |= (byte) (bits.get(i) << (7 - (i & 7)));

        QrCode qr = new QrCode(version);
        qr.drawFunctionPatterns();
        byte[] all = qr.addEccAndInterleave(codewords);
        qr.drawCodewords(all);
        // best mask
        int bestMask = 0;
        long minPenalty = Long.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            qr.applyMask(m);
            qr.drawFormatBits(m);
            long p = qr.penalty();
            if (p < minPenalty) {
                bestMask = m;
                minPenalty = p;
            }
            qr.applyMask(m);   // undo (XOR)
        }
        qr.applyMask(bestMask);
        qr.drawFormatBits(bestMask);
        return qr;
    }

    /** SVG with a quiet zone of {@code border} modules; scales to its container. */
    public String toSvg(int border, String label) {
        StringBuilder sb = new StringBuilder();
        int dim = size + border * 2;
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ").append(dim).append(' ').append(dim)
                .append("\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"").append(label.replace("\"", "")).append("\">");
        sb.append("<rect width=\"100%\" height=\"100%\" fill=\"#FFFFFF\"/><path fill=\"#000000\" d=\"");
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (modules[y][x]) sb.append('M').append(x + border).append(',').append(y + border).append("h1v1h-1z");
            }
        }
        sb.append("\"/></svg>");
        return sb.toString();
    }

    // ------------------------------------------------------------------ construction

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        int[] align = alignmentPositions();
        int n = align.length;
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if ((i == 0 && j == 0) || (i == 0 && j == n - 1) || (i == n - 1 && j == 0)) continue;
                drawAlignment(align[i], align[j]);
            }
        }
        drawFormatBits(0);   // reserve
        drawVersion();
    }

    private void drawFormatBits(int mask) {
        int data = ECC_FORMAT_BITS_M << 3 | mask;
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = (data << 10 | rem) ^ 0x5412;
        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));
        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);   // dark module
    }

    private void drawVersion() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        long bits = (long) version << 12 | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = ((bits >>> i) & 1) != 0;
            int a = size - 11 + i % 3, c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private void drawFinder(int x, int y) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int xx = x + dx, yy = y + dy;
                if (xx >= 0 && xx < size && yy >= 0 && yy < size) setFunction(xx, yy, dist != 2 && dist != 4);
            }
        }
    }

    private void drawAlignment(int x, int y) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) setFunction(x + dx, y + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
        }
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private int[] alignmentPositions() {
        if (version == 1) return new int[0];
        int num = version / 7 + 2;
        int step = (version == 32) ? 26 : (version * 4 + num * 2 + 1) / (num * 2 - 2) * 2;
        int[] r = new int[num];
        r[0] = 6;
        for (int i = r.length - 1, pos = size - 7; i >= 1; i--, pos -= step) r[i] = pos;
        return r;
    }

    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = BLOCKS_M[version];
        int blockEcc = ECC_PER_BLOCK_M[version];
        int rawCodewords = numRawDataModules(version) / 8;
        int numShort = numBlocks - rawCodewords % numBlocks;
        int shortLen = rawCodewords / numBlocks;
        byte[][] blocks = new byte[numBlocks][];
        byte[] divisor = rsDivisor(blockEcc);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int datLen = shortLen - blockEcc + (i < numShort ? 0 : 1);
            byte[] dat = java.util.Arrays.copyOfRange(data, k, k + datLen);
            k += datLen;
            byte[] block = java.util.Arrays.copyOf(dat, shortLen + 1);
            byte[] ecc = rsRemainder(dat, divisor);
            System.arraycopy(ecc, 0, block, block.length - blockEcc, ecc.length);
            blocks[i] = block;
        }
        byte[] result = new byte[rawCodewords];
        for (int i = 0, k = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < blocks.length; j++) {
                if (i != shortLen - blockEcc || j >= numShort) result[k++] = blocks[j][i];
            }
        }
        return result;
    }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert = switch (mask) {
                    case 0 -> (x + y) % 2 == 0;
                    case 1 -> y % 2 == 0;
                    case 2 -> x % 3 == 0;
                    case 3 -> (x + y) % 3 == 0;
                    case 4 -> (x / 3 + y / 2) % 2 == 0;
                    case 5 -> x * y % 2 + x * y % 3 == 0;
                    case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
                    default -> ((x + y) % 2 + x * y % 3) % 2 == 0;
                };
                modules[y][x] ^= invert & !isFunction[y][x];
            }
        }
    }

    private long penalty() {
        long result = 0;
        // runs and finder-like patterns in rows and columns
        for (int pass = 0; pass < 2; pass++) {
            for (int a = 0; a < size; a++) {
                boolean runColor = false;
                int runLen = 0;
                int[] history = new int[7];
                for (int b = 0; b < size; b++) {
                    boolean c = pass == 0 ? modules[a][b] : modules[b][a];
                    if (c == runColor) {
                        runLen++;
                        if (runLen == 5) result += 3;
                        else if (runLen > 5) result++;
                    } else {
                        addHistory(runLen, history);
                        if (!runColor) result += countFinderLike(history) * 40;
                        runColor = c;
                        runLen = 1;
                    }
                }
                result += terminateAndCount(runColor, runLen, history) * 40;
            }
        }
        // 2x2 blocks
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += 3;
            }
        }
        // balance
        int dark = 0;
        for (boolean[] row : modules) for (boolean m : row) if (m) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;
        result += (long) k * 10;
        return result;
    }

    private int countFinderLike(int[] h) {
        int n = h[1];
        boolean core = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n;
        return (core && h[0] >= n * 4 && h[6] >= n ? 1 : 0) + (core && h[6] >= n * 4 && h[0] >= n ? 1 : 0);
    }

    private int terminateAndCount(boolean runColor, int runLen, int[] h) {
        if (runColor) {
            addHistory(runLen, h);
            runLen = 0;
        }
        runLen += size;
        addHistory(runLen, h);
        return countFinderLike(h);
    }

    private void addHistory(int runLen, int[] h) {
        if (h[0] == 0) runLen += size;   // the light border before the first run
        System.arraycopy(h, 0, h, 1, h.length - 1);
        h[0] = runLen;
    }

    // ------------------------------------------------------------------ maths

    private static int numRawDataModules(int ver) {
        int r = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            r -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) r -= 36;
        }
        return r;
    }

    private static int numDataCodewords(int ver) {
        return numRawDataModules(ver) / 8 - ECC_PER_BLOCK_M[ver] * BLOCKS_M[ver];
    }

    private static byte[] rsDivisor(int degree) {
        byte[] r = new byte[degree];
        r[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < r.length; j++) {
                r[j] = (byte) rsMultiply(r[j] & 0xff, root);
                if (j + 1 < r.length) r[j] ^= r[j + 1];
            }
            root = rsMultiply(root, 0x02);
        }
        return r;
    }

    private static byte[] rsRemainder(byte[] data, byte[] divisor) {
        byte[] r = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ r[0]) & 0xff;
            System.arraycopy(r, 1, r, 0, r.length - 1);
            r[r.length - 1] = 0;
            for (int i = 0; i < r.length; i++) r[i] ^= (byte) rsMultiply(divisor[i] & 0xff, factor);
        }
        return r;
    }

    private static int rsMultiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static void appendBits(List<Integer> bits, int val, int len) {
        for (int i = len - 1; i >= 0; i--) bits.add((val >>> i) & 1);
    }

    private static boolean bit(int x, int i) {
        return ((x >>> i) & 1) != 0;
    }
}
