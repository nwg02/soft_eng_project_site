# GISU — final review and Railway deployment preparation

**Status:** preparation only. This document does not authorize a merge, live
migration, or Railway deployment. Work remains in
`feature/georgia-health-marketplace-redesign` (PR #17).

## 1. Automated checks (feature branch)

- GitHub Actions now runs `mvn -B -ntp verify` (tests **and** packages the
  Spring Boot JAR) on each feature-branch push and PR into `main`.
- `SiteReadinessSmokeTest` checks public pages/assets, the updated register
  page, guest redirects, and all principal authenticated screens with an
  isolated H2 database. The existing integration tests exercise real account
  ownership, TOTP, billing, enrollment requests, notifications, assessments,
  and AI response validation.
- These automated checks **do not open a real browser** and therefore cannot
  prove every mobile alignment, scrollbar, dropdown, or focus interaction.
- CI uses H2, **not production MySQL**. Passing CI does not prove that every
  MySQL migration has been applied.

## 2. Manual layout check before merge

Use a disposable local GISU test account, the `local` Spring Boot profile,
and browser responsive mode (or desktop and phone). Test approximately
1440px, 1024px, 768px, 390px, and 320px viewport widths.

- [ ] Guest: homepage, Plans filter/search/compare, links to plan documents,
      assessment sign-in redirect, Log in, Create account.
- [ ] Signed in: Account menu fits the screen; logout works; dashboard cards
      do not overlap or run offscreen; 2FA security settings work.
- [ ] Assessment: enter preferences, complete, generate consented Gemini
      analysis when configured, return to saved results.
- [ ] Billing: add a planned expense, Pay bill (sample), verify recorded
      history; test deleting an entry and another account's isolation.
- [ ] Notifications: add a renewal date and confirm upcoming reminders
      appear once; mark alerts read.
- [ ] Enrollment: save an enrollment request, return to it from the dashboard,
      remove it; insurer links open official sites.
- [ ] Agent requests and reports: save, view, close/reopen as appropriate.
- [ ] Chat widget: opens and closes on desktop/mobile, stays in viewport,
      responds to a question, and does not cover a form permanently.
- [ ] Keyboard-only navigation: visible focus, usable Account dropdown,
      accessible form labels and submit controls, and no trapped focus.
- [ ] Keep screenshots of any observed clipping/alignment problems; fix them
      in the feature branch before merging.

## 3. Production configuration

Default `application.properties` now enables Thymeleaf caching and sets
`HttpOnly`, `Secure`, `SameSite=Lax` session cookies, 30-minute sessions,
limited error detail, and standard forwarded-header handling for Railway HTTPS.
`application-local.properties` overrides `cookie.secure=false` and
`spring.thymeleaf.cache=false` so localhost over HTTP works normally.

Check production **server-side environment variables** in Railway (do not
send or paste their values into source control, screenshots, or chat):

| Variable | Purpose |
| --- | --- |
| `MYSQLHOST`, `MYSQLPORT` | Existing production database host and port |
| `MYSQLUSER`, `MYSQLPASSWORD` | Existing database access credentials |
| `PORT` | Railway-injected port (Spring Boot already reads it) |
| `GEMINI_API_KEY` | Real Gemini assessment/chat requests; otherwise AI action is unavailable |
| `GEMINI_MODEL` (optional) | Existing working Gemini model override |
| `GISU_TOTP_ENCRYPTION_KEY` | Stable base64-encoded **32-byte** encryption key for authenticator 2FA |

The JDBC URL currently selects the **`gisu`** MySQL database explicitly:
`jdbc:mysql://${MYSQLHOST}:${MYSQLPORT}/gisu`. Confirm that is the actual
Railway database name; do not replace it or update credentials speculatively.
`OPENAI_API_KEY` is optional for the alternative chatbot path.

Generate a **new** server-only TOTP encryption key locally if production
does not yet have one, for example `openssl rand -base64 32`, and enter
it only into Railway secrets. **Keep this key stable**: rotation without
re-encrypting the existing TOTP secrets can lock out enrolled accounts.
Never copy `.gisu-totp-key` into GitHub. Do not use live account credentials
in demo recordings.

## 4. MySQL migration readiness — DO NOT RUN YET

The feature uses manually maintained additive SQL scripts rather than
Flyway/Liquibase. A Railway backup and a record of already-applied migrations
are prerequisites. Local H2 creates the new tables automatically; MySQL
does not.

1. Inspect the existing Railway database **read-only** first: table names,
   `customers`, `enrollments`, and `insurance_assessments` columns;
   check existing data and applied migration history if tracked by the team.
2. Confirm whether V2, V3, V4, V5, V6, V7, or V8 are already applied. Do not
   execute a migration twice blindly: V2's `ALTER TABLE enrollments ADD COLUMN
   household_size` and V3's `ALTER TABLE insurance_assessments ADD COLUMN`
   statements are **not repeatable** on databases with those columns.
3. With a restorable snapshot, apply **only the missing** scripts, in order:
   `V2 → V3 → V4 → V5 → V6 → V7 → V8`, preferably on a cloned/staging MySQL
   database first. Record the applied versions and verify all expected tables.
4. Specifically verify `payment_history`, `user_notifications`,
   `identity_verification_attempts`, `account_totp_settings`,
   `account_totp_recovery_codes`, `assessment_ai_reviews`,
   `gisu_enrollment_requests`, `gisu_renewal_reminders`, and
   `gisu_notification_receipts`.
5. Do not re-run `database/schema.sql` as a blanket update; it contains
   baseline and legacy fixture setup, not the versioned upgrades.
6. Only then test the updated application against **staging** MySQL with
   test accounts and confirm it starts without missing-table errors.

## 5. Security gates before an open public deployment

The following safeguards still need review and/or implementation:

- **CSRF protection on authenticated POST forms and chat requests**. Cookies
  and SameSite settings help but do not replace full CSRF protection.
- **Global login/registration and AI request rate limiting**, not just the
  existing per-session chat limit; enforce reasonable AI service budgets.
- Confirm production HTTPS, trusted proxy forwarding, session invalidation,
  and user isolation after deployment.
- Establish an administrator recovery policy for lost 2FA devices and
  recovery codes.
- Verify that sensitive data and credentials are not present in app logs,
  code, or test/demo screenshots.

For a private, tightly controlled class presentation, these are risk
boundaries to understand. Do not describe this as a reviewed production-grade
insurance service or leave it publicly exposed without hardening.

## 6. Go-live and rollback sequence (requires team approval)

After PR review and the checks above: back up MySQL, validate scripts on
staging, verify environment, approve and merge PR #17, and intentionally
trigger Railway deployment. Smoke-test guest and authenticated flows
through HTTPS, then verify on a mobile screen. Record commit SHA and migration
versions.

If a code deployment fails, redeploy the previously known-good application
and leave additive tables intact while diagnosing. Restore the database
from the verified backup only if necessary and with coordinated team approval;
do not run guessed destructive SQL against customer records.

**Current state:** No migrations run, no `main` merge, and no Railway
environment or service modified as part of this preparation.
