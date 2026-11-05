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
- Integration tests use H2. Separately, GitHub Actions starts a disposable
  MySQL 8 service, applies `database/schema.sql` followed by V2–V8 in order,
  then checks required tables and columns. **This verifies new-install SQL
  syntax and sequencing only**; it does not inspect or modify Railway and does
  not prove its existing schema is up to date.

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

- **CSRF protection:** Implemented using Spring Security for all POST
  operations. Thymeleaf inserts a hidden session token in server-rendered
  forms, and the chat widget sends an `X-CSRF-TOKEN` header. Dedicated
  filter-enabled integration tests check that missing/incorrect tokens are
  rejected. Traditional workflow tests run with servlet filters disabled to
  isolate controller behavior; the security test runs with filters enabled.
- **Login and API rate limits:** Implemented single-instance, bounded-window
  throttles for login, registration, AI analysis and chat. For a scaled
  production deployment add shared/edge rate limits and enforce API budgets;
  in-memory limits are per instance and cannot stop distributed abuse.
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


## Security implementation added during final review

The feature branch now includes `WebSecurityConfig.java` and
`RequestRateLimitFilter.java`. No new database migration is required.
CSRF is enforced even on the public registration/login POST routes; the
forms still work in browsers because Thymeleaf supplies the hidden token.
For API clients submitting JSON to `/api/chat`, the session token must
be sent in the `X-CSRF-TOKEN` header.

`SecurityHardeningFlowTest` exercises Spring Security's actual servlet
filter chain and asserts 403 on POST requests without CSRF tokens, successful
requests with tokens, and 429 after repeated sign-in failures. Existing
`*FlowTest` classes disable the filters to test business logic independently.
Do not disable filters in production or the local running application.

AI assessments now replace internal catalog IDs in their prose with
published plan names, including when showing **previously saved** AI reviews.
The stored catalog slugs used for backend references remain unchanged.

After pulling the branch, manually test registration, login, 2FA, logout,
chat, saved plans, assessment generation, billing, reminders, and enrollment
requests in a real browser with cookies enabled. This verifies that the
browser submits CSRF tokens and that new sessions behave correctly.

## 7. Read-only Railway database inventory

Before running any production migration, inspect the database using
[`database/production_schema_audit.sql`](database/production_schema_audit.sql).
That script uses only `SELECT` against MySQL `information_schema`, reports
server/database identity, and checks 18 version-associated table/column
markers from V2–V8. It does not print customer records or modify data.

The audit output is **evidence, not a migration version ledger**. A version
can have all of its sampled markers while still having partial differences.
Compare actual schema definitions with the migration files, inspect existing
deployment history, and verify that a restorable backup exists before
deciding which migrations (if any) may safely run. Do not run V2 or V3
`ALTER TABLE` statements again when their columns already exist.

This is the next read-only step for an authorized Railway database connection.
No deployment, backup, secret inspection, database writes, or migration
execution is implied by preparing this check.

## 8. Railway database found with a partially applied V2

On 2026-11-04, the project owner inspected the `gisu` MySQL database: it
contained six legacy/partial-V2 tables (`customers`, `enrollments`,
`extension_requests`, `insurance_assessments`, `insurance_plans`,
`payments`). The `enrollments.household_size` column already exists,
while the three V3 assessment columns have not been added. A local
`mysqldump` export was completed and has six CREATE TABLE statements and
a completed-dump footer. This is the observed state, **not a version ledger**.

For **this exact partial state**, the script
`scripts/build-railway-partial-v2-migrations.sh` emits the remaining V2
CREATE TABLE statements (including the no-op `extension_requests` statement
with IF NOT EXISTS) and then V3–V8, in order. It skips only the previously
applied `ALTER TABLE enrollments ADD COLUMN household_size` at the top of V2.
It does not connect to a database or perform writes unless its generated
SQL is explicitly piped into a MySQL client.

After reviewing the local backup and the selected Railway database, an
authorized operator can run from the repository root:

```bash
bash scripts/build-railway-partial-v2-migrations.sh | \
  /usr/local/mysql/bin/mysql -h PUBLIC_MYSQL_HOST -P PUBLIC_MYSQL_PORT -u root -p gisu
```

The command prompts for the password without including it in shell history.
Do not run on a different/unknown schema, or rerun after a partial failure:
MySQL DDL is not a single all-or-nothing transaction. Stop and inspect the
schema if an error occurs. The GitHub Actions MySQL job also tests this exact
partial-upgrade path and verifies that a pre-existing customer is preserved.
