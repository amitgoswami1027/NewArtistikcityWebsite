package com.artistikcity.auth.webauthn;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal CBOR (RFC 8949) decoder for WebAuthn attestation objects and COSE keys.
 * Supports unsigned/negative integers (as Long), byte strings (byte[]), text strings, arrays, maps,
 * booleans, null and floats. Indefinite lengths and tags are rejected - authenticators must use
 * the CTAP2 canonical encoding, which has neither.
 */
public final class Cbor {

    private final byte[] data;
    private int pos;

    private Cbor(byte[] data, int offset) {
        this.data = data;
        this.pos = offset;
    }

    /** Decodes one item that must span the whole input. */
    public static Object decode(byte[] data) {
        Cbor c = new Cbor(data, 0);
        Object v = c.read(0);
        if (c.pos != data.length) {
            throw new IllegalArgumentException("trailing bytes after CBOR item");
        }
        return v;
    }

    /** Decodes one item starting at {@code offset}; returns {value, Integer endOffset}. */
    public static Object[] decodeAt(byte[] data, int offset) {
        Cbor c = new Cbor(data, offset);
        Object v = c.read(0);
        return new Object[]{v, c.pos};
    }

    private Object read(int depth) {
        if (depth > 16) {
            throw new IllegalArgumentException("CBOR nesting too deep");
        }
        int initial = u8();
        int major = initial >>> 5;
        int info = initial & 0x1f;
        if (major == 7) {
            return switch (info) {
                case 20 -> Boolean.FALSE;
                case 21 -> Boolean.TRUE;
                case 22, 23 -> null;
                case 25 -> (double) halfToFloat((int) uint(2));
                case 26 -> (double) Float.intBitsToFloat((int) uint(4));
                case 27 -> Double.longBitsToDouble(uint(8));
                default -> throw new IllegalArgumentException("unsupported CBOR simple value " + info);
            };
        }
        long len = argument(info);
        switch (major) {
            case 0:
                return len;
            case 1:
                return -1 - len;
            case 2:
                return bytes(len);
            case 3:
                return new String(bytes(len), StandardCharsets.UTF_8);
            case 4: {
                List<Object> list = new ArrayList<>();
                for (long i = 0; i < len; i++) {
                    list.add(read(depth + 1));
                }
                return list;
            }
            case 5: {
                Map<Object, Object> map = new LinkedHashMap<>();
                for (long i = 0; i < len; i++) {
                    Object k = read(depth + 1);
                    map.put(k, read(depth + 1));
                }
                return map;
            }
            default:
                throw new IllegalArgumentException("CBOR tags are not supported");
        }
    }

    private long argument(int info) {
        if (info < 24) {
            return info;
        }
        return switch (info) {
            case 24 -> uint(1);
            case 25 -> uint(2);
            case 26 -> uint(4);
            case 27 -> {
                long v = uint(8);
                if (v < 0) {
                    throw new IllegalArgumentException("CBOR integer too large");
                }
                yield v;
            }
            default -> throw new IllegalArgumentException("indefinite-length CBOR is not supported");
        };
    }

    private long uint(int n) {
        long v = 0;
        for (int i = 0; i < n; i++) {
            v = (v << 8) | u8();
        }
        return v;
    }

    private int u8() {
        if (pos >= data.length) {
            throw new IllegalArgumentException("truncated CBOR");
        }
        return data[pos++] & 0xff;
    }

    private byte[] bytes(long len) {
        if (len < 0 || len > data.length - pos) {
            throw new IllegalArgumentException("truncated CBOR string");
        }
        byte[] b = new byte[(int) len];
        System.arraycopy(data, pos, b, 0, (int) len);
        pos += (int) len;
        return b;
    }

    private static float halfToFloat(int h) {
        int exp = (h >> 10) & 0x1f;
        int mant = h & 0x3ff;
        float v = exp == 0 ? mant * (float) Math.pow(2, -24) : exp == 31 ? (mant == 0 ? Float.POSITIVE_INFINITY : Float.NaN)
                : (float) ((mant + 1024) * Math.pow(2, exp - 25));
        return (h & 0x8000) != 0 ? -v : v;
    }
}
