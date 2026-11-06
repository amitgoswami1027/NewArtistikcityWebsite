package com.artistikcity.web;

import com.artistikcity.auth.AccountService;
import com.artistikcity.auth.AccountService.AccountConflictException;
import com.artistikcity.auth.AccountService.ProviderProfile;
import com.artistikcity.auth.AuthEvents;
import com.artistikcity.auth.JwtVerifier;
import com.artistikcity.auth.RateLimiter;
import com.artistikcity.auth.Tokens;
import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Google and Facebook sign-in, implemented directly on the OAuth 2.0 authorization-code flow.
 *
 * <ul>
 *   <li><b>CSRF:</b> a random {@code state} per attempt, stored server-side and checked once on the callback.</li>
 *   <li><b>PKCE (S256):</b> the authorization code is useless without the verifier that never left the server.</li>
 *   <li><b>Google = OpenID Connect:</b> the ID token's RS256 signature is verified against Google's JWKS, plus
 *       {@code iss}, {@code aud}, {@code exp} and the {@code nonce} bound to this attempt. The profile
 *       (sub, e-mail, email_verified, name, picture) comes from the verified token.</li>
 *   <li><b>Facebook:</b> Graph API {@code /me} called with {@code appsecret_proof}.</li>
 * </ul>
 * Configure GOOGLE_CLIENT_ID/SECRET and FACEBOOK_CLIENT_ID/SECRET. Redirect URI to register with each provider:
 * {@code {site}/social-login/google/callback} and {@code {site}/social-login/facebook/callback}.
 */
@Controller
public class SocialController {

    private static final Logger log = LoggerFactory.getLogger(SocialController.class);
    private static final String STATE_PREFIX = "oauth.state.";
    private static final Duration ATTEMPT_TTL = Duration.ofMinutes(10);

    private final Environment env;
    private final Auth auth;
    private final AccountService accounts;
    private final JwtVerifier jwt;
    private final RateLimiter limiter;
    private final AuthEvents events;
    private final Json json;
    private final Redirects redirect;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SocialController(Environment env, Auth auth, AccountService accounts, JwtVerifier jwt, RateLimiter limiter,
                            AuthEvents events, Json json, Redirects redirect) {
        this.env = env;
        this.auth = auth;
        this.accounts = accounts;
        this.jwt = jwt;
        this.limiter = limiter;
        this.events = events;
        this.json = json;
        this.redirect = redirect;
    }

    /** Which providers are configured (shown as buttons on the login page). */
    public static Map<String, Boolean> enabledProviders(Environment env) {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String p : List.of("google", "facebook")) {
            m.put(p, !env.getProperty("social." + p + ".client-id", "").isBlank()
                    && !env.getProperty("social." + p + ".client-secret", "").isBlank());
        }
        return m;
    }

    // ------------------------------------------------------------------ step 1: redirect to the provider

    @GetMapping("/social-login/{provider}")
    public ResponseEntity<String> start(HttpServletRequest request, @PathVariable("provider") String provider,
                                        @RequestParam(value = "intent", required = false) String intent,
                                        @RequestParam(value = "remember", required = false) String remember) {
        if (!List.of("google", "facebook").contains(provider) || !enabledProviders(env).get(provider)) {
            Flash.put(request, "message", "Sign in with " + AccountService.label(provider) + " isn't available right now.");
            return redirect.route(request, "login");
        }
        if (!limiter.hit("oauth:start:" + AuthEvents.ip(request), 30, 600).allowed()) {
            Flash.put(request, "message", "Too many sign-in attempts. Please wait a few minutes and try again.");
            return redirect.route(request, "login");
        }
        boolean link = "link".equals(intent);
        if (link && auth.userId(request) == null) {
            return redirect.route(request, "login");
        }
        AuthController.rememberReturnTo(request);

        String state = Tokens.random(32);
        String verifier = Tokens.random(48);
        String nonce = Tokens.random(32);
        Map<String, Object> attempt = new HashMap<>();
        attempt.put("provider", provider);
        attempt.put("verifier", verifier);
        attempt.put("nonce", nonce);
        attempt.put("link", link);
        attempt.put("remember", !"0".equals(remember));
        attempt.put("created", Instant.now().getEpochSecond());
        HttpSession session = request.getSession(true);
        session.setAttribute(STATE_PREFIX + state, attempt);

        String clientId = env.getProperty("social." + provider + ".client-id");
        StringBuilder url = new StringBuilder(endpoint(provider, "auth-url"))
                .append("?response_type=code")
                .append("&client_id=").append(enc(clientId))
                .append("&redirect_uri=").append(enc(callbackUrl(request, provider)))
                .append("&state=").append(state)
                .append("&code_challenge=").append(Tokens.pkceChallenge(verifier))
                .append("&code_challenge_method=S256");
        if ("google".equals(provider)) {
            url.append("&scope=").append(enc("openid email profile")).append("&nonce=").append(nonce)
                    .append("&prompt=select_account");
        } else {
            url.append("&scope=").append(enc("email,public_profile"));
            if (link) {
                url.append("&auth_type=rerequest"); // ask again for the e-mail permission if it was declined before
            }
        }
        return redirect.to(request, url.toString());
    }

    // ------------------------------------------------------------------ step 2: the provider sends the user back

    @GetMapping("/social-login/{provider}/callback")
    @SuppressWarnings("unchecked")
    public ResponseEntity<String> callback(HttpServletRequest request, @PathVariable("provider") String provider,
                                           @RequestParam(value = "code", required = false) String code,
                                           @RequestParam(value = "state", required = false) String state,
                                           @RequestParam(value = "error", required = false) String error,
                                           @RequestParam(value = "error_reason", required = false) String errorReason) {
        String label = AccountService.label(provider);
        HttpSession session = request.getSession(false);
        Map<String, Object> attempt = null;
        if (session != null && state != null && state.length() < 100) {
            Object a = session.getAttribute(STATE_PREFIX + state);
            session.removeAttribute(STATE_PREFIX + state); // single use
            if (a instanceof Map<?, ?> m) {
                attempt = (Map<String, Object>) m;
            }
        }
        boolean link = attempt != null && Boolean.TRUE.equals(attempt.get("link"));

        if (error != null) {
            boolean cancelled = "access_denied".equals(error) || "user_denied".equals(errorReason);
            events.fail(request, auth.userId(request), cancelled ? "oauth_cancelled" : "oauth_error", provider, error);
            return back(request, link, cancelled ? "You cancelled signing in with " + label + "."
                    : label + " couldn't sign you in (" + error + "). Please try again.");
        }
        if (attempt == null || !provider.equals(attempt.get("provider"))
                || Instant.now().getEpochSecond() - ((Number) attempt.get("created")).longValue() > ATTEMPT_TTL.getSeconds()) {
            events.fail(request, null, "oauth_state_mismatch", provider, null);
            return back(request, link, "That sign-in link has expired or was opened in another browser. Please try again.");
        }
        if (code == null || code.isBlank() || code.length() > 2000) {
            return back(request, link, label + " didn't send an authorization code. Please try again.");
        }

        ProviderProfile profile;
        try {
            profile = "google".equals(provider)
                    ? google(request, code, (String) attempt.get("verifier"), (String) attempt.get("nonce"))
                    : facebook(request, code, (String) attempt.get("verifier"));
        } catch (Exception e) {
            log.warn("{} sign-in failed: {}", label, e.getMessage());
            events.fail(request, null, "oauth_exchange_failed", provider, e.getMessage());
            return back(request, link, "We couldn't complete signing in with " + label + ". Please try again.");
        }

        try {
            Long current = link ? auth.userId(request) : null;
            if (link && current == null) {
                return redirect.route(request, "login");
            }
            AccountService.LinkResult r = accounts.signInWithProvider(request, profile, current);
            if (link) {
                Flash.put(request, "success", r.notice() != null ? r.notice() : label + " is now connected.");
                return redirect.to(request, "/user/linked-accounts");
            }
            auth.login(request, r.userId(), provider, Boolean.TRUE.equals(attempt.get("remember")));
            if (r.notice() != null) {
                Flash.put(request, "message", r.notice());
            }
            String fallback = r.created() ? "/dashboard?welcome=1" : "/dashboard";
            if (r.created() && profile.email() == null) {
                Flash.put(request, "message", label + " didn't share an e-mail address. Add one in Login & security so you can get course updates.");
            }
            return redirect.to(request, AuthController.takeReturnTo(request, fallback));
        } catch (AccountConflictException e) {
            events.fail(request, auth.userId(request), "oauth_conflict", provider, e.getMessage());
            return back(request, link, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ providers

    /** Google: code -> tokens (with PKCE verifier) -> verified ID token claims. */
    private ProviderProfile google(HttpServletRequest request, String code, String verifier, String nonce) throws Exception {
        String clientId = env.getProperty("social.google.client-id");
        String form = "grant_type=authorization_code&code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&client_secret=" + enc(env.getProperty("social.google.client-secret"))
                + "&redirect_uri=" + enc(callbackUrl(request, "google"))
                + "&code_verifier=" + enc(verifier);
        Map<String, Object> token = postForm(endpoint("google", "token-url"), form);
        Map<String, Object> c = jwt.verify((String) token.get("id_token"), endpoint("google", "jwks-url"),
                List.of(env.getProperty("social.google.issuer", "https://accounts.google.com"), "accounts.google.com"), clientId, nonce);
        Object verified = c.get("email_verified");
        Map<String, Object> raw = new LinkedHashMap<>();
        for (String k : List.of("sub", "email", "email_verified", "name", "given_name", "family_name", "picture", "locale", "hd")) {
            if (c.get(k) != null) {
                raw.put(k, c.get(k));
            }
        }
        return new ProviderProfile("google", String.valueOf(c.get("sub")), (String) c.get("email"),
                Boolean.TRUE.equals(verified) || "true".equals(String.valueOf(verified)),
                (String) c.get("name"), (String) c.get("given_name"), (String) c.get("family_name"), (String) c.get("picture"), raw);
    }

    /** Facebook: code -> access token (with PKCE verifier) -> Graph /me with appsecret_proof. */
    @SuppressWarnings("unchecked")
    private ProviderProfile facebook(HttpServletRequest request, String code, String verifier) throws Exception {
        String secret = env.getProperty("social.facebook.client-secret");
        String form = "code=" + enc(code)
                + "&client_id=" + enc(env.getProperty("social.facebook.client-id"))
                + "&client_secret=" + enc(secret)
                + "&redirect_uri=" + enc(callbackUrl(request, "facebook"))
                + "&code_verifier=" + enc(verifier);
        Map<String, Object> token = postForm(endpoint("facebook", "token-url"), form);
        String accessToken = (String) token.get("access_token");
        if (accessToken == null) {
            throw new IllegalStateException("no access_token");
        }
        String proof = Tokens.hmacSha256Hex(secret.getBytes(StandardCharsets.UTF_8), accessToken);
        String url = endpoint("facebook", "graph-url") + "?fields=" + enc("id,name,first_name,last_name,email,picture.width(256).height(256)")
                + "&access_token=" + enc(accessToken) + "&appsecret_proof=" + proof;
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        Map<String, Object> me = json.decodeMap(res.body());
        if (res.statusCode() != 200 || me == null || me.get("id") == null) {
            throw new IllegalStateException("profile request failed (" + res.statusCode() + ")");
        }
        String picture = null;
        if (me.get("picture") instanceof Map<?, ?> pic && pic.get("data") instanceof Map<?, ?> data
                && !Boolean.TRUE.equals(data.get("is_silhouette"))) {
            picture = (String) data.get("url");
        }
        // Facebook only returns e-mail addresses the person has confirmed; FACEBOOK_TRUST_EMAIL=false turns merging off
        boolean trusted = env.getProperty("social.facebook.trust-email", Boolean.class, true);
        Map<String, Object> raw = new LinkedHashMap<>(me);
        return new ProviderProfile("facebook", String.valueOf(me.get("id")), (String) me.get("email"), trusted && me.get("email") != null,
                (String) me.get("name"), (String) me.get("first_name"), (String) me.get("last_name"), picture, raw);
    }

    private Map<String, Object> postForm(String url, String form) throws Exception {
        HttpResponse<String> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> body = json.decodeMap(res.body());
        if (res.statusCode() != 200 || body == null) {
            Object err = body == null ? res.statusCode() : body.getOrDefault("error_description", body.get("error"));
            throw new IllegalStateException("token request failed: " + err);
        }
        return body;
    }

    // ------------------------------------------------------------------ helpers

    private String endpoint(String provider, String key) {
        return env.getProperty("social." + provider + "." + key, switch (provider + "." + key) {
            case "google.auth-url" -> "https://accounts.google.com/o/oauth2/v2/auth";
            case "google.token-url" -> "https://oauth2.googleapis.com/token";
            case "google.jwks-url" -> "https://www.googleapis.com/oauth2/v3/certs";
            case "facebook.auth-url" -> "https://www.facebook.com/v21.0/dialog/oauth";
            case "facebook.token-url" -> "https://graph.facebook.com/v21.0/oauth/access_token";
            case "facebook.graph-url" -> "https://graph.facebook.com/v21.0/me";
            default -> throw new IllegalArgumentException(provider + "." + key);
        });
    }

    private ResponseEntity<String> back(HttpServletRequest request, boolean link, String message) {
        if (link && auth.userId(request) != null) {
            Flash.put(request, "error", message);
            return redirect.to(request, "/user/linked-accounts");
        }
        Flash.put(request, "message", message);
        return redirect.route(request, "login");
    }

    private String callbackUrl(HttpServletRequest request, String provider) {
        String base = env.getProperty("app.url", "");
        return (base.isBlank() ? Inertia.baseUrl(request) : base.replaceAll("/+$", "")) + "/social-login/" + provider + "/callback";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
