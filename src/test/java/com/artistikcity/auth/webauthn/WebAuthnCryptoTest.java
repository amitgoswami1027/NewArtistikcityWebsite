package com.artistikcity.auth.webauthn;

import com.artistikcity.auth.Tokens;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Simulates an authenticator: builds COSE keys / authenticator data in CBOR and signs like a passkey would. */
class WebAuthnCryptoTest {

    // ---------- tiny CBOR encoder for the test ----------
    static byte[] cbor(Object o) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, o);
        return out.toByteArray();
    }

    static void head(ByteArrayOutputStream out, int major, long v) {
        if (v < 24) out.write((major << 5) | (int) v);
        else if (v < 256) { out.write((major << 5) | 24); out.write((int) v); }
        else if (v < 65536) { out.write((major << 5) | 25); out.write((int) (v >> 8)); out.write((int) v); }
        else { out.write((major << 5) | 26); for (int s = 24; s >= 0; s -= 8) out.write((int) (v >> s)); }
    }

    @SuppressWarnings("unchecked")
    static void write(ByteArrayOutputStream out, Object o) {
        if (o instanceof Integer i) o = (long) i;
        if (o instanceof Long l) { if (l >= 0) head(out, 0, l); else head(out, 1, -1 - l); }
        else if (o instanceof byte[] b) { head(out, 2, b.length); out.writeBytes(b); }
        else if (o instanceof String s) { byte[] b = s.getBytes(StandardCharsets.UTF_8); head(out, 3, b.length); out.writeBytes(b); }
        else if (o instanceof List<?> l) { head(out, 4, l.size()); l.forEach(x -> write(out, x)); }
        else if (o instanceof Map<?, ?> m) { head(out, 5, m.size()); m.forEach((k, v) -> { write(out, k); write(out, v); }); }
        else throw new IllegalArgumentException(String.valueOf(o));
    }

    static byte[] fixed(BigInteger v, int len) {
        byte[] b = v.toByteArray();
        if (b.length == len) return b;
        if (b.length > len) return Arrays.copyOfRange(b, b.length - len, b.length);
        byte[] r = new byte[len];
        System.arraycopy(b, 0, r, len - b.length, b.length);
        return r;
    }

    static byte[] coseEs256(ECPublicKey k) {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put(1, 2); m.put(3, -7); m.put(-1, 1);
        m.put(-2, fixed(k.getW().getAffineX(), 32)); m.put(-3, fixed(k.getW().getAffineY(), 32));
        return cbor(m);
    }

    static byte[] authData(String rpId, int flags, long count, byte[] credId, byte[] cose) {
        ByteBuffer b = ByteBuffer.allocate(37 + (credId == null ? 0 : 18 + credId.length + cose.length));
        b.put(Tokens.sha256(rpId.getBytes(StandardCharsets.UTF_8))).put((byte) flags).putInt((int) count);
        if (credId != null) {
            b.put(new byte[16]).putShort((short) credId.length).put(credId).put(cose);
        }
        return b.array();
    }

    // ---------- tests ----------

    @Test
    void cborDecodesTheTypesWebAuthnUses() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put("fmt", "none"); m.put("attStmt", Map.of()); m.put(-2, new byte[]{1, 2, 3}); m.put(1000, List.of(1, 2));
        Object back = Cbor.decode(cbor(m));
        assertThat(back).isInstanceOf(Map.class);
        Map<?, ?> r = (Map<?, ?>) back;
        assertThat(r.get("fmt")).isEqualTo("none");
        assertThat((byte[]) r.get(-2L)).containsExactly(1, 2, 3);
        assertThat(r.get(1000L)).isEqualTo(List.of(1L, 2L));
    }

    @Test
    void cborRejectsMalformedInput() {
        assertThatThrownBy(() -> Cbor.decode(new byte[]{(byte) 0x5f})).isInstanceOf(IllegalArgumentException.class); // indefinite
        assertThatThrownBy(() -> Cbor.decode(new byte[]{0x45, 1, 2})).isInstanceOf(IllegalArgumentException.class);  // truncated
        assertThatThrownBy(() -> Cbor.decode(new byte[]{0x01, 0x02})).isInstanceOf(IllegalArgumentException.class);  // trailing
    }

    @Test
    void es256PasskeyRoundTrip() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
        g.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair kp = g.generateKeyPair();
        byte[] cose = coseEs256((ECPublicKey) kp.getPublic());
        byte[] credId = Tokens.randomBytes(32);

        WebAuthnService.AuthData reg = WebAuthnService.parseAuthData(authData("localhost", 0x45, 0, credId, cose));
        assertThat(reg.credentialId()).isEqualTo(credId);
        assertThat(reg.coseKey()).isEqualTo(cose);
        assertThat(reg.flags() & 0x04).isNotZero(); // UV

        CoseKey key = CoseKey.parse(reg.coseKey());
        assertThat(key.algorithm()).isEqualTo(CoseKey.ES256);

        byte[] assertion = authData("localhost", 0x05, 7, null, null);
        byte[] clientData = "{\"type\":\"webauthn.get\",\"challenge\":\"abc\",\"origin\":\"http://localhost:8080\"}".getBytes(StandardCharsets.UTF_8);
        byte[] signed = concat(assertion, Tokens.sha256(clientData));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(kp.getPrivate());
        s.update(signed);
        byte[] sig = s.sign();
        assertThat(key.verify(signed, sig)).isTrue();
        assertThat(WebAuthnService.parseAuthData(assertion).signCount()).isEqualTo(7);

        signed[signed.length - 1] ^= 1; // any change to the signed data must fail
        assertThat(key.verify(signed, sig)).isFalse();
        assertThat(key.verify(concat(assertion, Tokens.sha256(clientData)), new byte[]{1, 2, 3})).isFalse();
    }

    @Test
    void ed25519AndRsaKeys() throws Exception {
        KeyPair ed = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] x509 = ed.getPublic().getEncoded();
        Map<Object, Object> okp = new LinkedHashMap<>();
        okp.put(1, 1); okp.put(3, -8); okp.put(-1, 6); okp.put(-2, Arrays.copyOfRange(x509, x509.length - 32, x509.length));
        CoseKey edKey = CoseKey.parse(cbor(okp));
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(ed.getPrivate());
        s.update("data".getBytes());
        assertThat(edKey.verify("data".getBytes(), s.sign())).isTrue();

        KeyPairGenerator rg = KeyPairGenerator.getInstance("RSA");
        rg.initialize(2048);
        KeyPair rsa = rg.generateKeyPair();
        RSAPublicKey rp = (RSAPublicKey) rsa.getPublic();
        Map<Object, Object> rm = new LinkedHashMap<>();
        rm.put(1, 3); rm.put(3, -257); rm.put(-1, rp.getModulus().toByteArray()); rm.put(-2, rp.getPublicExponent().toByteArray());
        CoseKey rsaKey = CoseKey.parse(cbor(rm));
        Signature rs = Signature.getInstance("SHA256withRSA");
        rs.initSign(rsa.getPrivate());
        rs.update("data".getBytes());
        assertThat(rsaKey.verify("data".getBytes(), rs.sign())).isTrue();
    }

    @Test
    void rejectsUnsupportedOrBrokenKeysAndAuthData() {
        Map<Object, Object> m = new LinkedHashMap<>();
        m.put(1, 2); m.put(3, -35); // ES384 not offered
        assertThatThrownBy(() -> CoseKey.parse(cbor(m))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebAuthnService.parseAuthData(new byte[10])).isInstanceOf(WebAuthnService.WebAuthnException.class);
        byte[] bad = authData("localhost", 0x45, 0, new byte[4], new byte[]{(byte) 0xa1});
        assertThatThrownBy(() -> WebAuthnService.parseAuthData(bad)).isInstanceOf(WebAuthnService.WebAuthnException.class);
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }
}
