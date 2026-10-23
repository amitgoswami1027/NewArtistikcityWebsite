package com.artistikcity.auth.webauthn;

import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HexFormat;
import java.util.Map;

/**
 * COSE_Key (RFC 9053) public keys used by passkeys, converted to Java keys:
 * ES256 (EC2 / P-256, alg -7), RS256 (RSA, alg -257) and EdDSA (OKP / Ed25519, alg -8).
 */
public record CoseKey(int algorithm, PublicKey publicKey) {

    public static final int ES256 = -7;
    public static final int RS256 = -257;
    public static final int EDDSA = -8;

    private static final byte[] ED25519_X509_PREFIX = HexFormat.of().parseHex("302a300506032b6570032100");

    public static CoseKey parse(byte[] cose) {
        if (!(Cbor.decode(cose) instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("COSE key is not a map");
        }
        long kty = num(m.get(1L));
        int alg = (int) num(m.get(3L));
        try {
            if (kty == 2 && alg == ES256) {
                if (num(m.get(-1L)) != 1) {
                    throw new IllegalArgumentException("only the P-256 curve is supported");
                }
                byte[] x = bytes(m.get(-2L));
                byte[] y = bytes(m.get(-3L));
                if (x.length != 32 || y.length != 32) {
                    throw new IllegalArgumentException("bad P-256 point");
                }
                AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
                params.init(new ECGenParameterSpec("secp256r1"));
                ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
                ECPoint point = new ECPoint(new BigInteger(1, x), new BigInteger(1, y));
                return new CoseKey(alg, KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec)));
            }
            if (kty == 3 && alg == RS256) {
                BigInteger n = new BigInteger(1, bytes(m.get(-1L)));
                BigInteger e = new BigInteger(1, bytes(m.get(-2L)));
                if (n.bitLength() < 2048) {
                    throw new IllegalArgumentException("RSA key shorter than 2048 bits");
                }
                return new CoseKey(alg, KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            }
            if (kty == 1 && alg == EDDSA) {
                if (num(m.get(-1L)) != 6) {
                    throw new IllegalArgumentException("only Ed25519 is supported");
                }
                byte[] x = bytes(m.get(-2L));
                byte[] der = new byte[ED25519_X509_PREFIX.length + x.length];
                System.arraycopy(ED25519_X509_PREFIX, 0, der, 0, ED25519_X509_PREFIX.length);
                System.arraycopy(x, 0, der, ED25519_X509_PREFIX.length, x.length);
                return new CoseKey(alg, KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der)));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid COSE key: " + e.getMessage(), e);
        }
        throw new IllegalArgumentException("unsupported key type " + kty + " / algorithm " + alg);
    }

    /** Verifies a WebAuthn signature (DER for ECDSA, PKCS#1 v1.5 for RSA, raw for Ed25519). */
    public boolean verify(byte[] signed, byte[] signature) {
        try {
            Signature s = Signature.getInstance(switch (algorithm) {
                case ES256 -> "SHA256withECDSA";
                case RS256 -> "SHA256withRSA";
                case EDDSA -> "Ed25519";
                default -> throw new IllegalStateException("alg " + algorithm);
            });
            s.initVerify(publicKey);
            s.update(signed);
            return s.verify(signature);
        } catch (Exception e) {
            return false; // malformed signature = invalid
        }
    }

    private static long num(Object o) {
        if (o instanceof Long l) {
            return l;
        }
        throw new IllegalArgumentException("missing COSE integer");
    }

    private static byte[] bytes(Object o) {
        if (o instanceof byte[] b) {
            return b;
        }
        throw new IllegalArgumentException("missing COSE byte string");
    }
}
