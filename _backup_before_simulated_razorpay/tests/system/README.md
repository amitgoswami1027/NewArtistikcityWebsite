# System tests (HTTP + database)

End-to-end checks of the production-readiness work that need the database and a fake payment gateway, so they run
against a real app rather than in the browser. Python 3 with `requests`; a `mysql` client on the PATH.

| Script | What it proves |
|---|---|
| `test_security.py` | Security log: sign-ins (and failures) are AUTH entries; a change has before/after JSON; a teacher refused 5 times is signed out; a student session calling the admin API gets 401 JSON and is signed out; CSV export is an EXPORT entry; "Verify integrity" passes, then finds a row edited in the database. |
| `test_platform.py` | Newsletter CSRF (419) and rate limit (429, no "already subscribed" leak); payment reconciliation (a captured payment whose callback and webhook were lost is found and completed; idempotent); a second payment for a course is flagged, refunded once through the gateway by staff; seat cap (409 SOLD_OUT, waitlist, invitation e-mail with a priority link that keeps the seat); messages to enrolled students with read receipts; reschedule; cancel with refunds queued; escrow ledger split, dispute hold, payout batch; content reports, bulk hide, ban and lift. |
| `test_buying.py` | Buying needs an account (guests are sent to log in with `return_to`, the page names the purchase, no hold for guests), "Buy now" keeps one piece, test-buyer accounts simulate payments on a site with live keys (orders `is_test`, kept out of revenue and the ledger; refused for normal accounts and instructors), and Admin > Room preview (upload, area, scale, order, hide, switch off). Same setup as `test_platform.py`; run on a fresh database. |
| `test_admin_setup.py` | Admin > System parameters and Studio Stories: guests and students get 401, a teacher and a moderator 403 on every new admin API; the Configuration Panel never sends a secret (mask), commits valid values to `config_overrides` (secrets encrypted) with immediate effect (a webhook secret changed in the panel is used at once), keeps a secret when the mask comes back, refuses bad values with a 422 per field and stores nothing, resets to the server value, keeps secrets out of the security log; SEO & Layout Controls validation and the public `site` prop; Studio Stories create / PUT / workflow / scheduled publishing / view counting / analytics. Dev profile, fresh database. |
| `test_selling.py` | My studio › Sell your art: only signed-in students (401 otherwise, also without CSRF); only approved work from a certified course, never someone else's; draft → review → staff reject with notes ("changes requested") / approve → live (original only) → edit sends it back to review → withdraw; photo checks; one listing per artwork; payout details validated, encrypted, need a fresh sign-in, readable by finance staff only (logged, never the details); a SELLING restriction blocks it. Dev profile, fresh database. |
| `test_site_access.py` | Site password (Admin > System parameters > Site Access Password, `/admin/parameters`): off by default; super admins only (guest/student 401, teacher/moderator 403); validation 422s store nothing; when on, pages show "Site under construction" (401, noindex), APIs JSON 401, Inertia 409, health/webhooks/static/admin stay open and signed-in admins see the site; right password sets an HttpOnly token cookie (never the password), wrong one doesn't, a new password signs everyone out, switching off opens the site at once; stored as bcrypt under the private `system` tag; the security log never holds the password. Dev profile. |
| `test_landing.py` | Landing Page Management: permissions, pages, versions, pricing, payments per page, checkout, coupons, webhook, refunds, analytics, SEO. Dev profile, fresh database. |
| `test_landing_quick_checkout.py` | Checkout inside the landing page and quick sign-in: `/enroll` back to the page, state for guests, e-mail code (limits, tries, new / existing account), phone code, Google one-tap (full sign-in when the OIDC stub with `/mint` runs on 127.0.0.1:9999), test buyer simulating in the page; Admin › Payments & checkout › Test payments (permissions, test buyers, simulator paid / declined / waiting / already enrolled / coupon not used up, undo, audit). Dev profile, fresh database. |
| `test_landing_showcase.py` | Own addresses and the three showcase pages: installed once through the module at `/landing/{slug}` (generated, unique), the original `/how-it-works`, course and `/commission/step-1` pages untouched, unpublish takes the address offline, course-only sections / unsafe links / wrong templates refused, instructors kept out, no checkout on information pages, SEO / sitemap / structured data / alt text, commission "from" prices equal the price list, CTA events. Dev profile, fresh database. |
| `fake_gateway.py` | A stand-in for the Razorpay endpoints those tests use (orders, order payments, capture, refund). |

Run each against a freshly loaded database (`START-ArtistikCity.bat -ResetDatabase`, or schema + seed + migrations) - they sign the demo teacher out on purpose, so one run affects the next:

```bash
# test_security.py: the app with the dev profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
MYSQL_CMD="mysql -h 127.0.0.1 -u root -pArtistikRoot@2026 artistikcity" python3 tests/system/test_security.py

# test_platform.py: the fake gateway on :9998, and the app pointed at it
python3 tests/system/fake_gateway.py 9998 &
java -jar target/artistikcity-1.0.0.jar --spring.profiles.active=dev --app.url=http://localhost:8080 \
  --razorpay.key=rzp_test_fake --razorpay.secret=fake_secret --razorpay.webhook-secret=test-wh-secret \
  --app.payments.razorpay.api-base=http://127.0.0.1:9998 --app.payments.reconcile.enabled=false
python3 tests/system/test_platform.py      # run from the repo root (it reads logs/mail-outbox)
```
Both print one PASS/FAIL line per check and end with `ALL PASSED`.
