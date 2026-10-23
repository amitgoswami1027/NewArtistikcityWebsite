package com.artistikcity.auth;

import com.artistikcity.support.Json;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ID-token verification against a local JWKS endpoint (stands in for Google). */
class JwtVerifierTest {

    static HttpServer server;
    static KeyPair key;
    static KeyPair otherKey;
    static String jwksUrl;
    static final Json JSON = new Json(new ObjectMapper());

    @BeforeAll
    static void start() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        key = g.generateKeyPair();
        otherKey = g.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) key.getPublic();
        String jwks = JSON.encode(Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "k1", "alg", "RS256", "use", "sig",
                "n", Tokens.b64url(unsigned(pub.getModulus().toByteArray())), "e", Tokens.b64url(pub.getPublicExponent().toByteArray())))));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/certs", ex -> {
            byte[] b = jwks.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        jwksUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/certs";
    }

    @AfterAll
    static void stop() {
        server.stop(0);
    }

    static byte[] unsigned(byte[] b) {
        return b[0] == 0 ? java.util.Arrays.copyOfRange(b, 1, b.length) : b;
    }

    static String jwt(Map<String, Object> header, Map<String, Object> claims, KeyPair signer) throws Exception {
        String h = Tokens.b64url(JSON.encode(header).getBytes(StandardCharsets.UTF_8));
        String c = Tokens.b64url(JSON.encode(claims).getBytes(StandardCharsets.UTF_8));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(signer.getPrivate());
        s.update((h + "." + c).getBytes(StandardCharsets.US_ASCII));
        return h + "." + c + "." + Tokens.b64url(s.sign());
    }

    static Map<String, Object> claims(String nonce, long exp) {
        return Map.of("iss", "https://accounts.google.com", "aud", "client-1", "sub", "1234567890", "email", "a@example.com",
                "email_verified", true, "nonce", nonce, "iat", Instant.now().getEpochSecond(), "exp", exp);
    }

    final JwtVerifier verifier = new JwtVerifier(JSON);
    final List<String> iss = List.of("https://accounts.google.com", "accounts.google.com");

    @Test
    void acceptsAValidToken() throws Exception {
        String t = jwt(Map.of("alg", "RS256", "kid", "k1"), claims("n1", Instant.now().getEpochSecond() + 300), key);
        Map<String, Object> c = verifier.verify(t, jwksUrl, iss, "client-1", "n1");
        assertThat(c.get("sub")).isEqualTo("1234567890");
    }

    @Test
    void rejectsForgedExpiredReplayedOrMisaddressedTokens() throws Exception {
        long later = Instant.now().getEpochSecond() + 300;
        String forged = jwt(Map.of("alg", "RS256", "kid", "k1"), claims("n1", later), otherKey);
        assertThatThrownBy(() -> verifier.verify(forged, jwksUrl, iss, "client-1", "n1")).hasMessageContaining("signature");

        String expired = jwt(Map.of("alg", "RS256", "kid", "k1"), claims("n1", Instant.now().getEpochSecond() - 600), key);
        assertThatThrownBy(() -> verifier.verify(expired, jwksUrl, iss, "client-1", "n1")).hasMessageContaining("expired");

        String ok = jwt(Map.of("alg", "RS256", "kid", "k1"), claims("n1", later), key);
        assertThatThrownBy(() -> verifier.verify(ok, jwksUrl, iss, "client-1", "other-nonce")).hasMessageContaining("nonce");
        assertThatThrownBy(() -> verifier.verify(ok, jwksUrl, iss, "client-2", "n1")).hasMessageContaining("audience");
        assertThatThrownBy(() -> verifier.verify(ok, jwksUrl, List.of("https://evil.example"), "client-1", "n1")).hasMessageContaining("issuer");

        String none = Tokens.b64url("{\"alg\":\"none\"}".getBytes()) + "." + ok.split("\\.")[1] + ".";
        assertThatThrownBy(() -> verifier.verify(none, jwksUrl, iss, "client-1", "n1")).isInstanceOf(JwtVerifier.InvalidTokenException.class);
        String unknownKid = jwt(Map.of("alg", "RS256", "kid", "nope"), claims("n1", later), key);
        assertThatThrownBy(() -> verifier.verify(unknownKid, jwksUrl, iss, "client-1", "n1")).hasMessageContaining("unknown signing key");
    }
}
