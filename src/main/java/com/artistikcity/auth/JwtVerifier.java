package com.artistikcity.auth;

import com.artistikcity.support.Json;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Verifies RS256-signed JWTs (OpenID Connect ID tokens) against a provider's JWKS endpoint.
 * Keys are cached per JWKS URL for an hour and refetched when an unknown {@code kid} appears (key rotation).
 */
@Component
public class JwtVerifier {

    public static class InvalidTokenException extends Exception {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    private record CachedKeys(Map<String, PublicKey> keys, Instant fetched) {
    }

    private final Json json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, CachedKeys> cache = new ConcurrentHashMap<>();

    public JwtVerifier(Json json) {
        this.json = json;
    }

    /**
     * Checks signature, issuer, audience, expiry and nonce. Returns the claims.
     * @param issuers accepted {@code iss} values
     */
    public Map<String, Object> verify(String jwt, String jwksUrl, List<String> issuers, String audience, String nonce)
            throws InvalidTokenException {
        if (jwt == null) {
            throw new InvalidTokenException("no id_token");
        }
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new InvalidTokenException("malformed token");
        }
        Map<String, Object> header = json.decodeMap(new String(Tokens.fromB64url(parts[0]), StandardCharsets.UTF_8));
        Map<String, Object> claims = json.decodeMap(new String(Tokens.fromB64url(parts[1]), StandardCharsets.UTF_8));
        if (header == null || claims == null) {
            throw new InvalidTokenException("unreadable token");
        }
        if (!"RS256".equals(header.get("alg"))) {
            throw new InvalidTokenException("unexpected alg " + header.get("alg")); // blocks alg=none / HS256 confusion
        }
        PublicKey key = key(jwksUrl, String.valueOf(header.get("kid")));
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(key);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!sig.verify(Tokens.fromB64url(parts[2]))) {
                throw new InvalidTokenException("bad signature");
            }
        } catch (InvalidTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidTokenException("signature check failed: " + e.getMessage());
        }
        long now = Instant.now().getEpochSecond();
        long skew = 120;
        if (!issuers.contains(String.valueOf(claims.get("iss")))) {
            throw new InvalidTokenException("wrong issuer " + claims.get("iss"));
        }
        Object aud = claims.get("aud");
        boolean audOk = aud instanceof List<?> l ? l.contains(audience) : audience.equals(String.valueOf(aud));
        if (!audOk) {
            throw new InvalidTokenException("wrong audience");
        }
        if (!(claims.get("exp") instanceof Number exp) || exp.longValue() + skew < now) {
            throw new InvalidTokenException("token expired");
        }
        if (claims.get("iat") instanceof Number iat && iat.longValue() - skew > now) {
            throw new InvalidTokenException("token issued in the future");
        }
        if (nonce != null && !Tokens.constantTimeEquals(nonce, String.valueOf(claims.get("nonce")))) {
            throw new InvalidTokenException("nonce mismatch"); // replayed / injected token
        }
        return claims;
    }

    private PublicKey key(String jwksUrl, String kid) throws InvalidTokenException {
        CachedKeys c = cache.get(jwksUrl);
        if (c == null || c.fetched().plus(Duration.ofHours(1)).isBefore(Instant.now()) || !c.keys().containsKey(kid)) {
            c = fetch(jwksUrl);
            cache.put(jwksUrl, c);
        }
        PublicKey k = c.keys().get(kid);
        if (k == null) {
            throw new InvalidTokenException("unknown signing key " + kid);
        }
        return k;
    }

    @SuppressWarnings("unchecked")
    private CachedKeys fetch(String jwksUrl) throws InvalidTokenException {
        try {
            HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(jwksUrl)).timeout(Duration.ofSeconds(10)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = json.decodeMap(res.body());
            Map<String, PublicKey> keys = new ConcurrentHashMap<>();
            for (Object o : (List<Object>) body.get("keys")) {
                Map<String, Object> k = (Map<String, Object>) o;
                if (!"RSA".equals(k.get("kty")) || k.get("kid") == null) {
                    continue;
                }
                BigInteger n = new BigInteger(1, Tokens.fromB64url((String) k.get("n")));
                BigInteger e = new BigInteger(1, Tokens.fromB64url((String) k.get("e")));
                keys.put((String) k.get("kid"), KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e)));
            }
            return new CachedKeys(keys, Instant.now());
        } catch (Exception e) {
            throw new InvalidTokenException("could not load signing keys: " + e.getMessage());
        }
    }
}
