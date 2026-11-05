package com.artistikcity.web;

import com.artistikcity.http.Auth;
import com.artistikcity.http.Flash;
import com.artistikcity.http.Redirects;
import com.artistikcity.inertia.Inertia;
import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import com.artistikcity.support.Str;
import jakarta.servlet.http.HttpServletRequest;
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
import java.util.Map;

/**
 * Google / Facebook login (SocialController.php, formerly Laravel Socialite), implemented with the
 * standard OAuth 2.0 authorization-code flow. Configure GOOGLE_CLIENT_ID/SECRET and
 * FACEBOOK_CLIENT_ID/SECRET; the callback URL is {APP_URL}/social-login/{provider}/callback.
 */
@Controller
public class SocialController {

    private static final Logger log = LoggerFactory.getLogger(SocialController.class);

    private record Provider(String authUrl, String tokenUrl, String profileUrl, String scope) {
    }

    private static final Map<String, Provider> PROVIDERS = Map.of(
            "google", new Provider("https://accounts.google.com/o/oauth2/v2/auth", "https://oauth2.googleapis.com/token",
                    "https://www.googleapis.com/oauth2/v3/userinfo", "openid email profile"),
            "facebook", new Provider("https://www.facebook.com/v21.0/dialog/oauth", "https://graph.facebook.com/v21.0/oauth/access_token",
                    "https://graph.facebook.com/me?fields=id,name,email,picture", "email"));

    private final Environment env;
    private final Db db;
    private final Auth auth;
    private final Json json;
    private final Slugs slugs;
    private final Redirects redirect;
    private final HttpClient http = HttpClient.newHttpClient();

    public SocialController(Environment env, Db db, Auth auth, Json json, Slugs slugs, Redirects redirect) {
        this.env = env;
        this.db = db;
        this.auth = auth;
        this.json = json;
        this.slugs = slugs;
        this.redirect = redirect;
    }

    @GetMapping("/social-login/{provider}")
    public ResponseEntity<String> redirectToProvider(HttpServletRequest request, @PathVariable("provider") String provider) {
        Provider p = PROVIDERS.get(provider);
        String clientId = env.getProperty("social." + provider + ".client-id", "");
        if (p == null || clientId.isBlank()) {
            Flash.put(request, "message", "Login with " + provider + " is not configured.");
            return redirect.route(request, "login");
        }
        AuthController.rememberReturnTo(request);
        String state = Str.random(32);
        request.getSession(true).setAttribute("oauth.state", state);
        String url = p.authUrl() + "?client_id=" + enc(clientId) + "&redirect_uri=" + enc(callbackUrl(request, provider))
                + "&response_type=code&scope=" + enc(p.scope()) + "&state=" + state;
        return redirect.to(request, url);
    }

    @GetMapping("/social-login/{provider}/callback")
    public ResponseEntity<String> callback(HttpServletRequest request, @PathVariable("provider") String provider,
                                           @RequestParam(value = "code", required = false) String code,
                                           @RequestParam(value = "state", required = false) String state,
                                           @RequestParam(value = "error", required = false) String error) {
        Provider p = PROVIDERS.get(provider);
        if (p == null || code == null) {
            if (error != null) {
                Flash.put(request, "message", "Sign in with " + provider + " was cancelled.");
            }
            return redirect.route(request, "login");
        }
        var session = request.getSession(false);
        Object expected = session == null ? null : session.getAttribute("oauth.state");
        if (session != null) {
            session.removeAttribute("oauth.state");
        }
        if (expected == null || !expected.equals(state)) {
            Flash.put(request, "message", "Your sign-in session expired. Please try again.");
            return redirect.route(request, "login");
        }
        try {
            String form = "code=" + enc(code) + "&client_id=" + enc(env.getProperty("social." + provider + ".client-id", ""))
                    + "&client_secret=" + enc(env.getProperty("social." + provider + ".client-secret", ""))
                    + "&redirect_uri=" + enc(callbackUrl(request, provider)) + "&grant_type=authorization_code";
            HttpResponse<String> tokenRes = http.send(HttpRequest.newBuilder(URI.create(p.tokenUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> token = json.decodeMap(tokenRes.body());
            String accessToken = token == null ? null : String.valueOf(token.get("access_token"));
            HttpResponse<String> profileRes = http.send(HttpRequest.newBuilder(URI.create(p.profileUrl()))
                    .header("Authorization", "Bearer " + accessToken).GET().build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> profile = json.decodeMap(profileRes.body());
            String id = String.valueOf(profile.getOrDefault("sub", profile.get("id")));
            String name = String.valueOf(profile.getOrDefault("name", "Artist"));
            Object email = profile.get("email");

            Row user = db.first("select id from users where provider_id = ?", id);
            if (user == null && email != null) {
                user = db.first("select id from users where email = ?", email);
            }
            long userId;
            if (user != null) {
                userId = user.lng("id");
            } else {
                userId = db.insert("users", Row.of("name", name, "slug", slugs.unique("users", "slug", name, null),
                        "email", email == null ? provider + "_" + id + "@users.noreply" : email,
                        "provider", provider, "provider_id", id, "password", auth.hash(Str.random(32)), "status", 1,
                        "free_courses", 0, "free_course_type", "default"));
            }
            auth.login(request, userId);
            return redirect.to(request, AuthController.takeReturnTo(request, "/dashboard"));
        } catch (Exception e) {
            log.error("Social login failed", e);
            Flash.put(request, "message", "Could not sign in with " + provider + ".");
            return redirect.route(request, "login");
        }
    }

    private static String callbackUrl(HttpServletRequest request, String provider) {
        return Inertia.baseUrl(request) + "/social-login/" + provider + "/callback";
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
