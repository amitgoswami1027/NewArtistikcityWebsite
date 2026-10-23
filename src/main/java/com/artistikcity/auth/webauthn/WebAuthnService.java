package com.artistikcity.auth.webauthn;

import com.artistikcity.auth.AccountService;
import com.artistikcity.auth.Tokens;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Passkeys (W3C WebAuthn Level 2) without external libraries.
 *
 * <h2>Registration</h2> (a signed-in user adds a passkey) - checks the client data (type, challenge, origin),
 * the authenticator data (RP ID hash, user-present + user-verified flags, attested credential) and stores the
 * credential id + COSE public key. Attestation statements are not evaluated ({@code attestation: "none"}),
 * as recommended for consumer sites.
 *
 * <h2>Authentication</h2> - discoverable credentials ("Sign in with a passkey", no username needed). Checks
 * client data, RP ID hash, UP + UV flags, the signature over {@code authenticatorData || SHA-256(clientDataJSON)},
 * the user handle, and the signature counter (a counter that goes backwards indicates a cloned authenticator).
 *
 * Challenges are 32 random bytes, kept in the HTTP session, single use, valid 5 minutes.
 */
@Service
public class WebAuthnService {

    private static final String REG_KEY = "webauthn.registration";
    private static final String AUTH_KEY = "webauthn.authentication";
    private static final long CHALLENGE_TTL = 300;

    private static final int FLAG_UP = 0x01;
    private static final int FLAG_UV = 0x04;
    private static final int FLAG_BE = 0x08;
    private static final int FLAG_BS = 0x10;
    private static final int FLAG_AT = 0x40;

    public static class WebAuthnException extends Exception {
        public WebAuthnException(String message) {
            super(message);
        }
    }

    /** Parsed authenticator data. */
    record AuthData(byte[] rpIdHash, int flags, long signCount, byte[] aaguid, byte[] credentialId, byte[] coseKey) {
    }

    private final Db db;
    private final Json json;
    private final AccountService accounts;
    private final Environment env;

    public WebAuthnService(Db db, Json json, AccountService accounts, Environment env) {
        this.db = db;
        this.json = json;
        this.accounts = accounts;
        this.env = env;
    }

    public String rpId(HttpServletRequest request) {
        String configured = env.getProperty("app.auth.webauthn.rp-id", "");
        return configured.isBlank() ? request.getServerName() : configured;
    }

    private List<String> origins(HttpServletRequest request) {
        String configured = env.getProperty("app.auth.webauthn.origins", "");
        if (!configured.isBlank()) {
            return Arrays.stream(configured.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        }
        String app = env.getProperty("app.url", "");
        return app.isBlank() ? List.of(Inertia.baseUrl(request)) : List.of(app.replaceAll("/+$", ""), Inertia.baseUrl(request));
    }

    // ------------------------------------------------------------------ registration

    public Map<String, Object> registrationOptions(HttpServletRequest request, Row user) {
        long userId = user.lng("id");
        String challenge = Tokens.random(32);
        request.getSession(true).setAttribute(REG_KEY, Map.of("challenge", challenge, "user", userId, "at", Instant.now().getEpochSecond()));
        List<Map<String, Object>> exclude = new ArrayList<>();
        for (Row c : db.select("select credential_id, transports from webauthn_credentials where user_id = ?", userId)) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("type", "public-key");
            d.put("id", c.str("credential_id"));
            if (c.get("transports") != null && !c.str("transports").isBlank()) {
                d.put("transports", List.of(c.str("transports").split(",")));
            }
            exclude.add(d);
        }
        String label = user.get("email") != null ? user.str("email")
                : user.get("phone_number") != null ? user.str("phone_number") : user.str("name");
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("challenge", challenge);
        o.put("rp", Map.of("id", rpId(request), "name", "ArtistikCity"));
        o.put("user", Map.of("id", accounts.webauthnHandle(userId), "name", label, "displayName", user.str("name")));
        o.put("pubKeyCredParams", List.of(Map.of("type", "public-key", "alg", CoseKey.ES256), Map.of("type", "public-key", "alg", CoseKey.EDDSA),
                Map.of("type", "public-key", "alg", CoseKey.RS256)));
        o.put("timeout", 120000);
        o.put("attestation", "none");
        o.put("excludeCredentials", exclude);
        o.put("authenticatorSelection", Map.of("residentKey", "required", "requireResidentKey", true, "userVerification", "required"));
        return o;
    }

    /** @return the new credential's database id */
    @SuppressWarnings("unchecked")
    public long finishRegistration(HttpServletRequest request, long userId, Map<String, Object> credential, String name) throws WebAuthnException {
        Map<String, Object> pending = take(request, REG_KEY);
        if (pending == null || ((Number) pending.get("user")).longValue() != userId) {
            throw new WebAuthnException("The passkey request expired. Please try again.");
        }
        Map<String, Object> response = credential.get("response") instanceof Map<?, ?> r ? (Map<String, Object>) r : null;
        if (response == null || !"public-key".equals(credential.get("type"))) {
            throw new WebAuthnException("Unexpected credential.");
        }
        byte[] clientDataJson = b64(response.get("clientDataJSON"));
        checkClientData(request, clientDataJson, "webauthn.create", (String) pending.get("challenge"));

        Object att;
        try {
            att = Cbor.decode(b64(response.get("attestationObject")));
        } catch (IllegalArgumentException e) {
            throw new WebAuthnException("Unreadable attestation.");
        }
        if (!(att instanceof Map<?, ?> attMap) || !(attMap.get("authData") instanceof byte[] authDataBytes)) {
            throw new WebAuthnException("Unreadable attestation.");
        }
        AuthData ad = parseAuthData(authDataBytes);
        checkRpAndFlags(request, ad);
        if ((ad.flags() & FLAG_AT) == 0 || ad.credentialId() == null) {
            throw new WebAuthnException("The authenticator didn't return a credential.");
        }
        CoseKey key;
        try {
            key = CoseKey.parse(ad.coseKey());
        } catch (IllegalArgumentException e) {
            throw new WebAuthnException("Unsupported passkey type: " + e.getMessage());
        }
        String credentialId = Tokens.b64url(ad.credentialId());
        if (credentialId.length() > 512) {
            throw new WebAuthnException("Credential id too long.");
        }
        if (!credentialId.equals(String.valueOf(credential.get("id")).replace("=", ""))) {
            throw new WebAuthnException("Credential id mismatch.");
        }
        if (db.first("select id from webauthn_credentials where credential_id = ?", credentialId) != null) {
            throw new WebAuthnException("This passkey is already registered.");
        }
        String transports = null;
        if (response.get("transports") instanceof List<?> t) {
            transports = String.join(",", t.stream().map(String::valueOf).filter(s -> s.matches("[a-z-]{1,20}")).toList());
        }
        String cleanName = name == null || name.isBlank() ? defaultName(request) : name.trim();
        return db.insertReturningId("webauthn_credentials", Row.of("user_id", userId, "credential_id", credentialId,
                "public_key_cose", ad.coseKey(), "algorithm", key.algorithm(), "sign_count", ad.signCount(),
                "transports", transports, "aaguid", uuid(ad.aaguid()), "backup_eligible", (ad.flags() & FLAG_BE) != 0,
                "backed_up", (ad.flags() & FLAG_BS) != 0, "name", cleanName.length() > 100 ? cleanName.substring(0, 100) : cleanName,
                "created_at", LocalDateTime.now()));
    }

    // ------------------------------------------------------------------ authentication

    public Map<String, Object> authenticationOptions(HttpServletRequest request) {
        String challenge = Tokens.random(32);
        request.getSession(true).setAttribute(AUTH_KEY, Map.of("challenge", challenge, "at", Instant.now().getEpochSecond()));
        Map<String, Object> o = new LinkedHashMap<>();
        o.put("challenge", challenge);
        o.put("rpId", rpId(request));
        o.put("timeout", 120000);
        o.put("userVerification", "required");
        o.put("allowCredentials", List.of()); // discoverable credentials: the authenticator offers the account
        return o;
    }

    /** @return the user id the passkey belongs to */
    @SuppressWarnings("unchecked")
    public long finishAuthentication(HttpServletRequest request, Map<String, Object> credential) throws WebAuthnException {
        Map<String, Object> pending = take(request, AUTH_KEY);
        if (pending == null) {
            throw new WebAuthnException("The passkey request expired. Please try again.");
        }
        Map<String, Object> response = credential.get("response") instanceof Map<?, ?> r ? (Map<String, Object>) r : null;
        if (response == null || !"public-key".equals(credential.get("type"))) {
            throw new WebAuthnException("Unexpected credential.");
        }
        String credentialId = String.valueOf(credential.get("id")).replace("=", "");
        Row stored = db.first("select c.*, u.webauthn_user_handle, u.status from webauthn_credentials c join users u on u.id = c.user_id"
                + " where c.credential_id = ?", credentialId);
        if (stored == null) {
            throw new WebAuthnException("This passkey isn't registered with ArtistikCity. Sign in another way, then add it in Login & security.");
        }
        byte[] clientDataJson = b64(response.get("clientDataJSON"));
        checkClientData(request, clientDataJson, "webauthn.get", (String) pending.get("challenge"));
        byte[] authDataBytes = b64(response.get("authenticatorData"));
        AuthData ad = parseAuthData(authDataBytes);
        checkRpAndFlags(request, ad);

        if (response.get("userHandle") != null && !String.valueOf(response.get("userHandle")).isBlank()) {
            String handle = Tokens.b64url(b64(response.get("userHandle")));
            if (!handle.equals(stored.str("webauthn_user_handle"))) {
                throw new WebAuthnException("This passkey belongs to a different account.");
            }
        }
        CoseKey key = CoseKey.parse((byte[]) stored.get("public_key_cose"));
        byte[] signed = concat(authDataBytes, Tokens.sha256(clientDataJson));
        if (!key.verify(signed, b64(response.get("signature")))) {
            throw new WebAuthnException("The passkey signature didn't match.");
        }
        long storedCount = stored.lng("sign_count", 0);
        if ((ad.signCount() != 0 || storedCount != 0) && ad.signCount() <= storedCount) {
            throw new WebAuthnException("This passkey may have been cloned (signature counter went backwards). Remove it and add it again.");
        }
        if (stored.lng("status", 1) == 0) {
            throw new WebAuthnException("This account is disabled.");
        }
        db.update("update webauthn_credentials set sign_count = ?, last_used_at = ?, backed_up = ? where id = ?",
                ad.signCount(), LocalDateTime.now(), (ad.flags() & FLAG_BS) != 0, stored.lng("id"));
        return stored.lng("user_id");
    }

    // ------------------------------------------------------------------ checks

    private void checkClientData(HttpServletRequest request, byte[] clientDataJson, String type, String challenge) throws WebAuthnException {
        Map<String, Object> cd = json.decodeMap(new String(clientDataJson, StandardCharsets.UTF_8));
        if (cd == null || !type.equals(cd.get("type"))) {
            throw new WebAuthnException("Unexpected passkey response type.");
        }
        if (!Tokens.constantTimeEquals(challenge, String.valueOf(cd.get("challenge")).replace("=", ""))) {
            throw new WebAuthnException("The passkey request expired. Please try again.");
        }
        if (!origins(request).contains(String.valueOf(cd.get("origin")))) {
            throw new WebAuthnException("Passkey used on the wrong website (" + cd.get("origin") + ").");
        }
        if (Boolean.TRUE.equals(cd.get("crossOrigin"))) {
            throw new WebAuthnException("Passkeys can't be used from an embedded frame.");
        }
    }

    private void checkRpAndFlags(HttpServletRequest request, AuthData ad) throws WebAuthnException {
        byte[] expected = Tokens.sha256(rpId(request).getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expected, ad.rpIdHash())) {
            throw new WebAuthnException("This passkey was made for a different website.");
        }
        if ((ad.flags() & FLAG_UP) == 0) {
            throw new WebAuthnException("User presence wasn't confirmed.");
        }
        if ((ad.flags() & FLAG_UV) == 0) {
            throw new WebAuthnException("Your device didn't verify you (fingerprint, face or PIN). Please try again.");
        }
    }

    static AuthData parseAuthData(byte[] a) throws WebAuthnException {
        if (a == null || a.length < 37) {
            throw new WebAuthnException("Authenticator data too short.");
        }
        ByteBuffer buf = ByteBuffer.wrap(a);
        byte[] rpIdHash = new byte[32];
        buf.get(rpIdHash);
        int flags = buf.get() & 0xff;
        long count = buf.getInt() & 0xffffffffL;
        byte[] aaguid = null;
        byte[] credId = null;
        byte[] cose = null;
        if ((flags & FLAG_AT) != 0) {
            if (a.length < 37 + 18) {
                throw new WebAuthnException("Attested credential data too short.");
            }
            aaguid = new byte[16];
            buf.get(aaguid);
            int len = buf.getShort() & 0xffff;
            if (len > 1023 || buf.remaining() < len) {
                throw new WebAuthnException("Bad credential id length.");
            }
            credId = new byte[len];
            buf.get(credId);
            int start = buf.position();
            try {
                Object[] r = Cbor.decodeAt(a, start);
                int end = (Integer) r[1];
                cose = Arrays.copyOfRange(a, start, end);
            } catch (IllegalArgumentException e) {
                throw new WebAuthnException("Unreadable credential public key.");
            }
        }
        return new AuthData(rpIdHash, flags, count, aaguid, credId, cose);
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private Map<String, Object> take(HttpServletRequest request, String key) {
        HttpSession s = request.getSession(false);
        if (s == null) {
            return null;
        }
        Object v = s.getAttribute(key);
        s.removeAttribute(key); // single use
        if (!(v instanceof Map<?, ?> m) || Instant.now().getEpochSecond() - ((Number) m.get("at")).longValue() > CHALLENGE_TTL) {
            return null;
        }
        return (Map<String, Object>) m;
    }

    private static byte[] b64(Object v) throws WebAuthnException {
        try {
            return Tokens.fromB64url(String.valueOf(v));
        } catch (IllegalArgumentException e) {
            throw new WebAuthnException("Malformed passkey response.");
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static String uuid(byte[] b) {
        if (b == null || b.length != 16) {
            return null;
        }
        ByteBuffer bb = ByteBuffer.wrap(b);
        return new UUID(bb.getLong(), bb.getLong()).toString();
    }

    private static String defaultName(HttpServletRequest request) {
        String ua = request.getHeader("User-Agent");
        if (ua == null) {
            return "Passkey";
        }
        String os = ua.contains("iPhone") ? "iPhone" : ua.contains("iPad") ? "iPad" : ua.contains("Android") ? "Android"
                : ua.contains("Mac OS") ? "Mac" : ua.contains("Windows") ? "Windows" : ua.contains("Linux") ? "Linux" : "device";
        return "Passkey on " + os;
    }
}
