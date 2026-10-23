package com.artistikcity.auth;

import com.artistikcity.support.Db;
import com.artistikcity.support.Json;
import com.artistikcity.support.Row;
import com.artistikcity.support.Slugs;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One person = one {@code users} row, however they sign in. This service owns the rules for finding,
 * creating and merging accounts.
 *
 * <h2>Linking rules (account-takeover safe)</h2>
 * <ol>
 *   <li>A provider identity (provider + provider_user_id) that is already linked always signs in to its user.</li>
 *   <li>When a signed-in user connects a provider, it is linked to them (unless it already belongs to someone else).</li>
 *   <li>Otherwise the provider's e-mail is matched against existing users <b>only if the provider says it is
 *       verified</b>. If the matched account had never verified its e-mail, someone else may have registered it
 *       first ("pre-account hijacking"), so its password is removed and its other sessions are ended before linking.</li>
 *   <li>Otherwise a new user is created. Providers that return no e-mail create an account without one.</li>
 * </ol>
 * Phone numbers are only matched once they have been verified by OTP; verifying a number moves it away from
 * any account that merely had it typed into a profile.
 */
@Service
public class AccountService {

    /** Profile received from Google / Facebook, normalised. */
    public record ProviderProfile(String provider, String providerUserId, String email, boolean emailVerified,
                                  String name, String firstName, String lastName, String avatarUrl, Map<String, Object> raw) {
    }

    /** Outcome of a provider sign-in. */
    public record LinkResult(long userId, boolean created, boolean linked, String notice) {
    }

    public static class AccountConflictException extends RuntimeException {
        public AccountConflictException(String message) {
            super(message);
        }
    }

    private final Db db;
    private final Json json;
    private final Slugs slugs;
    private final SessionService sessions;
    private final AuthEvents events;

    public AccountService(Db db, Json json, Slugs slugs, SessionService sessions, AuthEvents events) {
        this.db = db;
        this.json = json;
        this.slugs = slugs;
        this.sessions = sessions;
        this.events = events;
    }

    public static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public Row findByEmail(String email) {
        String e = normalizeEmail(email);
        return e == null ? null : db.first("select * from users where email = ?", e);
    }

    /** Finds a user by a phone number that has been verified with an OTP. */
    public Row findByVerifiedPhone(String e164) {
        return db.first("select * from users where phone_number = ? and phone_verified_at is not null", e164);
    }

    /** Creates a student account. {@code email} / {@code phone} may be null (but not both). */
    public long create(String name, String email, boolean emailVerified, String phone, boolean phoneVerified,
                       String passwordHash, String avatarUrl) {
        String e = normalizeEmail(email);
        if (e == null && phone == null) {
            throw new IllegalArgumentException("an account needs an e-mail or a phone number");
        }
        String display = name == null || name.isBlank() ? defaultName(e) : name.trim();
        if (display.length() > 255) {
            display = display.substring(0, 255);
        }
        LocalDateTime now = LocalDateTime.now();
        Row r = Row.of("name", display, "slug", slugs.unique("users", "slug", display, null), "email", e,
                "email_verified_at", e != null && emailVerified ? now : null,
                "phone_number", phone, "phone_verified_at", phone != null && phoneVerified ? now : null,
                "password_hash", passwordHash, "is_verified", (e != null && emailVerified) || (phone != null && phoneVerified),
                "profile_photo", avatarUrl, "status", 1, "free_courses", 0, "free_course_type", "default");
        return db.insert("users", r);
    }

    private static String defaultName(String email) {
        if (email == null) {
            return "New artist";
        }
        String local = email.substring(0, email.indexOf('@')).replaceAll("[._+\\-]+", " ").trim();
        if (local.isEmpty()) {
            return "New artist";
        }
        StringBuilder sb = new StringBuilder();
        for (String w : local.split("\\s+")) {
            if (!w.isEmpty() && !w.chars().allMatch(Character::isDigit)) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.length() == 0 ? "New artist" : sb.toString().trim();
    }

    // ----------------------------------------------------------------- providers

    /**
     * Signs in with a provider identity, applying the linking rules above.
     * @param currentUserId the signed-in user when they are connecting the provider from their settings, else null
     */
    public LinkResult signInWithProvider(HttpServletRequest request, ProviderProfile p, Long currentUserId) {
        Row link = db.first("select * from linked_accounts where provider_name = ? and provider_user_id = ?", p.provider(), p.providerUserId());
        String email = normalizeEmail(p.email());

        if (link != null) {
            long owner = link.lng("user_id");
            if (currentUserId != null && currentUserId != owner) {
                throw new AccountConflictException("This " + label(p.provider()) + " account is already connected to a different ArtistikCity account.");
            }
            updateLink(link.lng("id"), p);
            fillProfileGaps(owner, p);
            return new LinkResult(owner, false, false, null);
        }

        if (currentUserId != null) {
            if (db.first("select id from linked_accounts where user_id = ? and provider_name = ?", currentUserId, p.provider()) != null) {
                throw new AccountConflictException("Your account is already connected to a different " + label(p.provider()) + " account. Disconnect it first.");
            }
            insertLink(currentUserId, p);
            fillProfileGaps(currentUserId, p);
            events.ok(request, currentUserId, "provider_linked", p.provider());
            return new LinkResult(currentUserId, false, true, label(p.provider()) + " is now connected to your account.");
        }

        if (email != null && p.emailVerified()) {
            Row existing = findByEmail(email);
            if (existing != null) {
                long uid = existing.lng("id");
                if (db.first("select id from linked_accounts where user_id = ? and provider_name = ?", uid, p.provider()) != null) {
                    // the account already has a *different* identity from this provider with the same e-mail
                    throw new AccountConflictException("An account with " + email + " is already connected to another " + label(p.provider())
                            + " login. Sign in with that one, or with your e-mail.");
                }
                String notice = null;
                if (existing.get("email_verified_at") == null) {
                    // nobody had proven ownership of this e-mail before: the provider just did. Anything set up
                    // by whoever created the account (a password, open sessions) must not survive.
                    db.updateById("users", uid, Row.of("password_hash", null, "email_verified_at", LocalDateTime.now(), "is_verified", true));
                    sessions.revokeAll(uid, null, "unverified_account_claimed");
                    events.record(request, uid, "unverified_account_claimed", p.provider(), true, email);
                    notice = "We verified your e-mail through " + label(p.provider()) + ". For your security, any password set on this account was removed.";
                }
                insertLink(uid, p);
                fillProfileGaps(uid, p);
                events.ok(request, uid, "provider_linked", p.provider());
                return new LinkResult(uid, false, true, notice);
            }
        }

        if (email != null && !p.emailVerified() && findByEmail(email) != null) {
            // can't prove it's the same person: don't merge, and don't create a duplicate with that e-mail
            throw new AccountConflictException("An account with " + email + " already exists, but " + label(p.provider())
                    + " couldn't confirm that e-mail belongs to you. Sign in with your e-mail first, then connect " + label(p.provider()) + " in Login & security.");
        }

        String name = p.name() != null && !p.name().isBlank() ? p.name()
                : ((p.firstName() == null ? "" : p.firstName()) + " " + (p.lastName() == null ? "" : p.lastName())).trim();
        long uid;
        if (email == null) {
            uid = createWithoutEmail(name, p.avatarUrl());
        } else {
            uid = create(name, email, p.emailVerified(), null, false, null, p.avatarUrl());
        }
        insertLink(uid, p);
        events.ok(request, uid, "signup", p.provider());
        return new LinkResult(uid, true, true, null);
    }

    /** Provider accounts without an e-mail (allowed by Facebook) get an account with neither e-mail nor phone yet. */
    private long createWithoutEmail(String name, String avatar) {
        String display = name == null || name.isBlank() ? "New artist" : name.trim();
        return db.insert("users", Row.of("name", display, "slug", slugs.unique("users", "slug", display, null),
                "profile_photo", avatar, "status", 1, "free_courses", 0, "free_course_type", "default", "is_verified", false));
    }

    private void insertLink(long userId, ProviderProfile p) {
        LocalDateTime now = LocalDateTime.now();
        db.insertPlain("linked_accounts", Row.of("user_id", userId, "provider_name", p.provider(), "provider_user_id", p.providerUserId(),
                "email", normalizeEmail(p.email()), "email_verified", p.emailVerified(), "display_name", trim(p.name(), 255),
                "first_name", trim(p.firstName(), 100), "last_name", trim(p.lastName(), 100), "avatar_url", trim(p.avatarUrl(), 1000),
                "profile_data", p.raw() == null ? null : json.encode(p.raw()), "created_at", now, "updated_at", now, "last_login_at", now));
    }

    private void updateLink(long linkId, ProviderProfile p) {
        db.update("update linked_accounts set email = ?, email_verified = ?, display_name = ?, first_name = ?, last_name = ?, avatar_url = ?,"
                        + " profile_data = ?, updated_at = ?, last_login_at = ? where id = ?",
                normalizeEmail(p.email()), p.emailVerified(), trim(p.name(), 255), trim(p.firstName(), 100), trim(p.lastName(), 100),
                trim(p.avatarUrl(), 1000), p.raw() == null ? null : json.encode(p.raw()), LocalDateTime.now(), LocalDateTime.now(), linkId);
    }

    /** Copies avatar / e-mail from the provider only where the account has none (never overwrites user data). */
    private void fillProfileGaps(long userId, ProviderProfile p) {
        Row u = db.find("users", userId);
        if (u == null) {
            return;
        }
        Row changes = new Row();
        if ((u.get("profile_photo") == null || u.str("profile_photo").isBlank()) && p.avatarUrl() != null) {
            changes.put("profile_photo", trim(p.avatarUrl(), 1000));
        }
        String email = normalizeEmail(p.email());
        if (u.get("email") == null && email != null && p.emailVerified() && findByEmail(email) == null) {
            changes.put("email", email);
            changes.put("email_verified_at", LocalDateTime.now());
            changes.put("is_verified", true);
        }
        if (!changes.isEmpty()) {
            db.updateById("users", userId, changes);
        }
    }

    public List<Row> linkedAccounts(long userId) {
        return db.select("select id, provider_name, email, display_name, avatar_url, created_at, last_login_at from linked_accounts"
                + " where user_id = ? order by provider_name", userId);
    }

    /** Disconnects a provider, unless it is the only way left to sign in. */
    public void unlink(HttpServletRequest request, long userId, String provider) {
        if (methodsLeftAfterRemoving(userId, provider) == 0) {
            throw new AccountConflictException("Add a password, phone number or passkey before disconnecting " + label(provider)
                    + " - otherwise you couldn't sign in any more.");
        }
        db.update("delete from linked_accounts where user_id = ? and provider_name = ?", userId, provider);
        events.ok(request, userId, "provider_unlinked", provider);
    }

    /** Number of sign-in methods the user would still have (e-mail magic link counts when the e-mail is verified). */
    public int methodsLeftAfterRemoving(long userId, String removingProvider) {
        Row u = db.find("users", userId);
        int n = 0;
        if (u.get("password_hash") != null) n++;
        if (u.get("email") != null && u.get("email_verified_at") != null) n++;
        if (u.get("phone_number") != null && u.get("phone_verified_at") != null) n++;
        n += (int) db.count("select count(*) from webauthn_credentials where user_id = ?", userId);
        n += (int) db.count("select count(*) from linked_accounts where user_id = ? and provider_name <> ?", userId,
                removingProvider == null ? "" : removingProvider);
        return n;
    }

    // ----------------------------------------------------------------- e-mail / phone

    public void markEmailVerified(long userId) {
        db.update("update users set email_verified_at = coalesce(email_verified_at, ?), is_verified = 1 where id = ?", LocalDateTime.now(), userId);
    }

    /**
     * Records that {@code userId} proved ownership of {@code phone} by OTP. The number is removed from any
     * other account that only had it typed in (unverified).
     */
    public void assignVerifiedPhone(HttpServletRequest request, long userId, String phone) {
        Row other = db.first("select id, phone_verified_at from users where phone_number = ? and id <> ?", phone, userId);
        if (other != null) {
            if (other.get("phone_verified_at") != null) {
                throw new AccountConflictException("This phone number is already verified on another account.");
            }
            db.update("update users set phone_number = null where id = ?", other.lng("id"));
            events.record(request, other.lng("id"), "unverified_phone_released", "otp", true, phone);
        }
        db.update("update users set phone_number = ?, phone_verified_at = ?, is_verified = 1 where id = ?", phone, LocalDateTime.now(), userId);
    }

    /** Random, stable WebAuthn user handle for the user (created on first use). */
    public String webauthnHandle(long userId) {
        Row u = db.first("select webauthn_user_handle from users where id = ?", userId);
        if (u != null && u.get("webauthn_user_handle") != null) {
            return u.str("webauthn_user_handle");
        }
        String handle = Tokens.random(32);
        db.update("update users set webauthn_user_handle = ? where id = ? and webauthn_user_handle is null", handle, userId);
        return db.first("select webauthn_user_handle from users where id = ?", userId).str("webauthn_user_handle");
    }

    public static String label(String provider) {
        return switch (provider == null ? "" : provider) {
            case "google" -> "Google";
            case "facebook" -> "Facebook";
            default -> provider;
        };
    }

    private static String trim(String s, int max) {
        return s == null ? null : (s.length() > max ? s.substring(0, max) : s);
    }
}
