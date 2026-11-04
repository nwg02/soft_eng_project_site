# GISU — Georgia Health Insurance Discovery

GISU is a Georgia-focused health insurance discovery and comparison project built with Java 17, Spring Boot, Thymeleaf, JDBC, and MySQL.

## Product scope

GISU helps people explore health insurance plan information, understand plan terminology, compare available options, and continue to an official insurer or Georgia Access website for quotes and enrollment. GISU is not an insurer, does not determine eligibility, and does not confirm coverage.

## Data quality rules

- Publish only plan records imported from a verified official source.
- Use Georgia Access / CMS public-use data and preserve source file, plan year, and source update date.
- Rates must be tied to the appropriate ZIP code, age, and other source dimensions. Do not show a rate as a personalized quote.
- Do not invent plan names, benefits, premiums, deductibles, provider networks, or enrollment URLs.
- Distinguish saved plans and enrollment-intent tracking from an active insurance policy.
- Label rules-based recommendations accurately. Do not describe them as AI unless a real AI service is integrated.

## Technology

- Java 17 and Spring Boot 3.5
- Thymeleaf templates and static CSS
- Spring JDBC
- MySQL 8+
- Maven

## Local setup (registration and sign-in enabled)

Run the development profile to use a **persistent, local-only H2 database**. This avoids requiring a Railway/MySQL connection on your Mac.

```bash
cd ~/Desktop/sprint2_site
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Open http://localhost:8080. You can create a test account and log in; the record persists in `gisu-local.mv.db` across restarts. The local schema is initialized from `src/main/resources/local-schema.sql` with `CREATE TABLE IF NOT EXISTS`. Never commit the generated database file or reuse a real-world account password for a classroom test.

The default application without the `local` profile still connects to the configured MySQL database (including Railway). The local profile **does not** modify or import data into Railway. To use local MySQL instead of H2, supply the existing `MYSQLHOST`, `MYSQLPORT`, `MYSQLUSER`, and `MYSQLPASSWORD` environment variables and run without the local profile.

### AI chat helper

The “Ask GISU” button works in **built-in guide mode** without an API key. For genuine Gemini AI replies, provide `GEMINI_API_KEY` as an environment variable in the **same terminal** that runs Spring Boot. The default model is `gemini-3.5-flash-lite` (the older 2.5 family returns HTTP 404 for many newly created API projects). You can optionally override it with `GEMINI_MODEL`. If you previously set `GEMINI_MODEL=gemini-2.5-flash-lite`, run `unset GEMINI_MODEL` before starting Spring Boot.

```bash
cd ~/Desktop/sprint2_site
git pull origin feature/georgia-health-marketplace-redesign
test -n "$GEMINI_API_KEY" && echo "Gemini key loaded"
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

Visit `http://localhost:8080/api/chat/status` to check for `{"mode":"gemini"}`, then ask the chatbot a question. The status only confirms the server detected the key, not whether Google accepted it. A working chat response confirms Gemini API access. If Gemini is unavailable or rate-limited, the helper falls back to clearly labeled fixed guidance.

The Gemini key is read server-side and sent to Google via an `x-goog-api-key` HTTP header; it is never embedded in HTML or JavaScript. **Never commit keys or paste them into chat.** The API free tier has usage limits and Google may use free-tier content to improve its products; avoid transmitting sensitive health, financial, or identity information. Existing `OPENAI_API_KEY` support remains available if `GEMINI_API_KEY` is absent. This classroom prototype's per-session limits should be strengthened before a public Railway deployment.

## Database changes

Additive changes for the Georgia marketplace are kept in `database/migrations/`. Apply each migration once, in version order, to the existing database before running application code that depends on its new tables. Back up the database first. These SQL files are not automatically executed by Spring Boot in the current project.

The first marketplace migration adds official plan catalog, service-area and rate tables, saved-plan records, enrollment-intent tracking, payment history, agent requests, notifications, mock verification, claim records, support messages, and the missing legacy extension-request structure. It does not delete legacy tables or rewrite existing customer records.

## Git workflow

- Stable application: `main`
- Current redesign work: `feature/georgia-health-marketplace-redesign`
- Make and review changes on the feature branch.
- Use descriptive commits so each milestone is easy to inspect.
- Do not merge into `main` or deploy to Railway until the team has reviewed and approved the pull request.

## Prototype limitations

- Payment history is informational; GISU does not process premiums.
- Identity verification is a mock workflow, not a real identity proofing service.
- Claim records are tracking notes, not claims submitted to an insurer.
- Agent requests are contact requests, not a promise of licensed-agent availability.
- Recommendations are informational and do not replace official plan documents or professional advice.


## 2027 curated plan explorer (feature branch)

The public `/plans` page displays a curated, static 2027 Georgia marketplace plan **subset**, drawn from official insurer plan documents. It does not require a database or login to browse. Currently includes 5 Alliant SoloCare and 7 Kaiser Permanente individual/family plan examples, with issuer, plan name, metal level, plan type, and deductible amount as displayed in the plan name. Each card opens a direct issuer-hosted SBC/EOC for that particular plan; comparison and filters operate entirely in the browser.

- Alliant source: https://alliantplans.com/solocare-sbcs/ (2027 Georgia section, on-market document variation 01)
- Kaiser source: https://healthy.kaiserpermanente.org/georgia/support/forms/documents/individual-family (2027 plans offered by Georgia Access)
- Georgia Access plan public use files: https://georgiaaccess.gov/public-use-files/
- Sources reviewed November 4, 2026.

**Limitations:** This is not a statewide inventory. There is no ZIP/county/age-specific availability filter, premium quoting, provider network or subsidy calculation yet. The deductible shown is parsed from the marketed plan name, not a substitute for the official plan document (and not necessarily an out-of-network deductible). Enrollment must occur on an official insurer/marketplace site. Do not represent browsing or clicking as enrollment or coverage. For a complete catalog, import Georgia Access 2027 Plan, Service Area, Benefits, and Rate PUFs into the marketplace tables, validate source dimensions, and verify plan URLs before publishing.

Anonymous visits to `/dashboard` redirect to `/login?reason=dashboard`, where an explanatory notice appears. The CSS uses Georgia State-inspired blue (#0039A6). None of these changes deploy to Railway or merge to main automatically.

## Educational assessment and document suggestions (feature branch)

- Signed-in users can complete `/assessment` with a Georgia-format ZIP, age, coverage type, household size, monthly premium budget, optional **broad income range**, and coverage priority.
- The completed assessment is stored locally and linked from the dashboard; the new `/assessment/results/{id}` page enforces signed-in ownership before showing saved answers.
- Results order **four official 2027 issuer plan documents** according to the selected research priority: Bronze metal level first for exploring potentially lower monthly premiums; smaller deductible *figures in plan names* first; PPO designs first for network flexibility; or Silver first for a balanced starting point.
- Budget, ZIP, age, income and household details are **not used to filter availability, calculate subsidy eligibility, price policies, or assess medical needs**. Results explicitly explain that limitation; the user must follow official sources for quotes and eligibility.
- The Explore Plans page and assessment results now use **the same 12-entry verified-document catalog** (`GeorgiaPlanCatalog.java`) so links and names remain consistent.
- For an existing local H2 database, `local-schema.sql` applies add-column-if-missing updates automatically. For the **production MySQL database**, apply `database/migrations/V3__assessment_preferences.sql` once, after V2 and after backing up the database. No production migration was applied.
- No Social Security number, exact income, medical history, or payment data is collected in this assessment. Do not use real personal details in classroom demos. Results are not professional insurance advice.

### Local verification

Use `mvn test` to run the automated H2-backed assessment flow tests in memory (without touching the regular local database or Railway). The feature branch also has a GitHub Actions workflow that runs these checks. After pulling the feature branch and restarting Spring Boot with `local` profile, create a test account and complete an assessment, review the document suggestions, then revisit them from Dashboard. Do not commit test accounts or API keys.

## Dashboard and saved curated plan documents

Signed-in users can bookmark and remove any of the 12 curated 2027 insurer plan documents on Explore Plans and from their assessment results. Their shortlist appears on the dashboard, with official document and insurer shopping links. Public visitors can still browse/compare plans without an account, and are invited to sign in to save.

Account-level bookmarks are stored as stable catalog slugs in `saved_curated_plans`, with a unique customer/slug index. No coverage or enrollment is created. The legacy fictional plan demo is labeled and collapsed on the dashboard.

The local H2 schema creates the bookmarks table automatically for existing databases. The production MySQL schema requires applying `database/migrations/V4__saved_curated_plan_bookmarks.sql` once **after V2 and V3**. Back up the database and verify migrations before deploying; nothing was applied to Railway.

Run `mvn test` to verify the feature, then run `mvn spring-boot:run -Dspring-boot.run.profiles=local` and test save, dashboard shortlist, removal, and account isolation.


## Account assistance, notifications and issue notes (classroom prototype)

Three logged-in features now work with the existing account database:

- `/agent-assistance`: Save an insurance-help topic, preferred contact method, and preferred time. The status is `demo_recorded`. **No licensed agent or other recipient gets the request, and no appointment, email or call is scheduled.** The account can revisit its saved requests.
- `/reports`: Create private, non-sensitive issue notes by category and mark them closed or reopened. This is **not an insurer claim submission, official claims status, or appeal**. Only the account owner can update a record. Do not enter medical records, SSNs, passwords, or payment details.
- `/notifications`: Receive in-app activity notifications when a demo assistance request or issue note is created or a note's status changes. Each user can mark individual or all of their own notifications read. Notifications do not send emails, texts, or live insurer updates.
- The dashboard displays counts for demo requests, open issue notes, unread notifications and recent account updates, and links to the feature pages.

Existing H2 local databases create `agent_requests`, `insurance_claim_records` and `user_notifications` automatically from `local-schema.sql` on startup, without erasing saved accounts or documents. The production MySQL definitions for these three tables already exist in the **V2** migration. No new production migration is needed solely for these account tools, but V2, V3 and V4 must be reviewed and applied to Railway's database before deploying feature-branch code. Do not apply the H2-only schema to MySQL.

To test locally, run `mvn test`, then `mvn spring-boot:run -Dspring-boot.run.profiles=local`. Sign in, record a demo assistance request, add an issue note, close and reopen it, and mark the resulting notifications read. No API keys or external services are required for these account features. The public production deployment and `main` are unchanged.


## Read-only, account-aware chatbot summaries

After signing in, users can ask the chatbot questions such as `What are my notifications?`,
`Summarize my reports`, `What plans have I saved?`, `Compare my saved plans`,
`Summarize my assessment`, `What assistance requests have I made?`, and
`Summarize my dashboard`. General insurance questions still go through the existing Gemini API.

- The API checks the authenticated GISU session and reads **only that account's records**.
  Guests are prompted to sign in. There are **no account mutation tools** available to Gemini;
  asking the chatbot to mark a notification read or edit a report cannot update it.
- By default, personal account questions receive a **private, deterministic GISU server-side
  summary**, without sending any database content to Google.
- A checkbox in the chatbot explicitly opts in **for one question** to a Gemini account
  summary. After submission it resets to unchecked. Only a **bounded, redacted snapshot**
  is shared: up to five system-generated notification titles and read statuses; up to eight
  saved insurer document names/tiers/plan types; issue report category/status (not the
  description); the assessment coverage type/priority; assistance contact preference/time;
  and aggregate counts. **No** account names, email addresses, ZIP codes, age, income,
  household size, premium budget, medical information, report descriptions or free-text
  assistance notes are retrieved for the outbound snapshot. The question itself is
  also sent to Gemini when opted in, so users should not type sensitive information.
- If Gemini fails, the chatbot returns its private summary. A request without a Gemini key
  stays server-side even if the opt-in box is selected.
- These summaries describe **classroom records only**, not eligibility, real coverage,
  completed claims or contact with an insurance agent.

Use `mvn test` to run the account-aware chat tests. For manual testing, sign into
`http://localhost:8080`, create an issue note and notification, ask `What are my
notifications?` with the checkbox unchecked, then compare the private answer with
the opt-in Gemini version. Log out and confirm that no personal data can be retrieved.


## Compact homepage and safe official enrollment handoff

The homepage is now a focused, mobile-responsive entry screen: Georgia health insurance plans,
a direct Browse Plans action, and short links to plan comparison, the assessment and dashboard.
The redundant marketing sections and motivational headlines are removed. The header puts
secondary logged-in features (saved plans, agent help, reports, notifications and logout) inside
a small Account menu, rather than displaying every link in the main navigation.

For all 12 curated 2027 issuer documents, `/enrollment/plan/{slug}` now displays a short,
plain-language handoff page with two server-validated options:

- Continue to the issuer's official shopping website.
- Continue to `https://georgiaaccess.gov/`.

The `POST /enrollment/referrals` endpoint validates the fixed catalog slug and the
`issuer` or `georgia-access` destination, then redirects to the official URL selected by
GISU itself. It does **not** accept external URLs supplied by users. Guests can follow
official links without an account. Signed-in users also get a **click-through history** on
the dashboard. A visit is never marked as applied/enrolled, and the website does not
confirm coverage, save payment details or communicate with an insurer.

The old fictional `/enroll`, `/cancel-enrollment`, `/request-extension`, and
`/switch-plan` mutations have been removed. Existing demonstration records remain
read-only in a collapsed archive on the dashboard, explicitly disclaimed as fictional.
No historical rows are deleted.

The local H2 schema adds `enrollment_referrals` automatically. For production MySQL,
apply the additive `database/migrations/V5__official_enrollment_referrals.sql` **after V4**
and review/back up all preceding migrations before deploying. GitHub feature-branch changes
do not alter the live Railway website or its database.

Verify with `mvn test`, then start the local profile and inspect the homepage on both
desktop and mobile widths; open a plan, check official document and handoff links,
test an official destination click while signed in, and verify the dashboard records only a
visit. The original fictional enrollment routes should no longer accept POSTs.


## Manual billing and payment history

GISU includes an account-only `/billing` page for **user-entered** insurance-related expenses:
insurance premiums, copays/coinsurance, prescriptions and other related expenses. Members
can log a planned payment with an expected due date, record a payment they say they have
already made, mark a planned item as paid (using the current date) or delete an entry.
Two totals distinguish planned and self-recorded paid amounts, with the newest 100
entries shown under separate headings. Billing totals and a link to the full history
also appear on the dashboard and the Account menu.

**Safety and data provenance:** GISU does not bill users, process payments, connect to
insurance companies, import bank/card transactions, verify payment receipts or represent
the ledger as insurer-generated records. The form never collects bank account numbers,
card information, policy numbers or financial credentials. Billing categories are
fixed choices with no free-text memo fields. All reads and modifications are scoped to
the signed-in GISU account.

The feature uses the existing MySQL `payment_history` table from
`database/migrations/V2__georgia_marketplace_schema.sql`. No new MySQL migration is
required if V2 is already applied. The local-only H2 schema creates a matching table
automatically without removing existing accounts or saved data. Entries from this
feature use the distinct `planned` and `recorded_paid` statuses, so unrelated
older records are not mislabeled. The `payment_date` field denotes either the expected
date for a planned item or the recorded payment date for a paid item.

Creating an entry or marking it paid generates an **in-app notification**, not an external
message. The account-aware chatbot can answer `What is my billing history?` privately,
including your manual totals. With explicit Gemini opt-in, the outgoing account snapshot
contains only counts of planned and paid entries—not payment amounts, dates or transaction
details; the user's typed question is also sent to Gemini.

Test locally with `mvn test` then the local Spring Boot profile. Sign in, create a
planned payment and a recorded payment, mark the planned item paid, remove one entry and
verify the totals and in-app notifications. Sign in as another account to verify privacy.
Railway and `main` remain unchanged.


## Mock identity check and optional authenticator 2FA

**Security & 2FA** is accessible in the signed-in Account dropdown and on Dashboard.

### Mock identity verification (classroom only)
- The `/account/security` page displays a public test code, `GISU26`, with a confirmation checkbox.
- The account can record that it completed the mock exercise. A dated
  `demo_completed` entry is saved in `identity_verification_attempts`.
- **No actual identity is verified or claimed.** Do not upload driver's licenses,
  government IDs, SSNs, selfies, medical information or personal records.
- The table already exists in the production V2 migration, and is created
  automatically in local H2.

### Authenticator-app two-factor login (TOTP)
- Optional real, time-based six-digit authenticator codes. Users set it up by
  confirming their GISU password, manually entering the displayed secret in an
  authenticator app, and confirming the current six-digit code.
- After activation, **a password alone does not create an authenticated user session**.
  The account must enter the current authenticator code or one of the one-time
  recovery codes on `/login/verify` before accessing the dashboard, billing,
  saved plans, notifications, account-chat records or other logged-in pages.
- Six random recovery codes are shown **only once**, immediately after enabling.
  They are bcrypt-hashed in the database, accepted once and then deleted.
  Authenticator codes use HMAC-SHA1/TOTP with a 30-second step, allow one step of
  clock drift and cannot be reused. Five incorrect codes end that sign-in attempt.
- Disabling 2FA requires the GISU password plus a fresh authenticator code or
  an unused recovery code. No phone or email provider is required.
- Authenticator secrets are encrypted with AES-256-GCM before storage.

**Local encryption key:** Running with the `local` profile creates a stable,
private file `.gisu-totp-key` in the project directory when you begin setup.
It is excluded by `.gitignore`. Keep this file with your local H2 database:
**deleting or changing it makes enabled authenticator accounts inaccessible**,
even if the database remains. Never commit or send this file to anyone.
Alternatively set `GISU_TOTP_ENCRYPTION_KEY` to base64 of 32 random bytes,
generated with `openssl rand -base64 32`, and keep it stable.

**Production:** Apply `database/migrations/V6__totp_login_security.sql`
only after V5, and configure `GISU_TOTP_ENCRYPTION_KEY` in the Railway
environment *before* activating 2FA. Do not store that key in Git or logs.
Neither V6 nor the environment has been changed on Railway. Without the
configured key, production authenticator setup cannot be completed; if a key
changes after setup, previously enabled accounts can be locked out.

This is a feature for a classroom prototype. It does not replace a full
production-grade security audit (CSRF protections, global login rate limiting,
account recovery policies, session/cookie settings, HTTPS enforcement and
secure database administration still require review).

Run `mvn test` and `mvn spring-boot:run -Dspring-boot.run.profiles=local`.
In Account -> Security & 2FA, perform the mock demonstration, then test
authenticator setup, save recovery codes, log out and sign in with a fresh
code or recovery code, and verify the account remains inaccessible before
completing the second step. Do not enable 2FA on an account unless you
have saved your recovery codes.


## Sprint 4: opt-in Gemini assessment and simulated payroll deductions

### AI assessment
- Complete an ordinary signed-in `/assessment` first. The rule-based
  four-document shortlist remains functional and is the default.
- On `/assessment/results/{id}`, users can **explicitly opt in** and press
  **Generate AI analysis**. GISU calls Google Gemini with the saved comparison
  priority, coverage type, broad household-size category, broad monthly budget
  range and the **12 approved 2027 catalog document characteristics**.
- GISU never includes the user's exact ZIP code, age, income, exact monthly
  budget, name, email, account ID, or user-written notes in this request.
  Gemini does receive the limited categories described on the results page.
- Gemini returns an educational summary and 2–4 plan **slugs** selected from
  the published catalog. Server-side validation rejects invented plan IDs,
  duplicates, malformed JSON and insufficient selections. Accepted reviews
  are saved to `assessment_ai_reviews` for this account and assessment and
  appear when the user revisits the results; document links remain the same
  official links already present in the catalog.
- AI suggestions do **not** certify availability by ZIP, live premiums,
  financial assistance, eligibility, doctor networks or actual suitability.
  When Gemini is not configured or fails, GISU clearly says so and retains
  the rule-based shortlist. It never calls a fallback result "AI generated".
- Server-only `GEMINI_API_KEY` and optional `GEMINI_MODEL` settings are
  shared with the chatbot; keys are never stored in the browser or committed.

### Mock payroll deductions
- `/payroll` is reachable from the signed-in Account dropdown, Dashboard
  and Billing. Create up to 30 independently saved simulations using a general
  hypothetical example or one catalog document, a **manually supplied**
  monthly premium, hypothetical monthly employer contribution and pay frequency.
- The displayed estimated deduction per paycheck is:
  `(monthly premium - employer monthly contribution) × 12 / annual pay periods`,
  rounded to cents. Supported schedules: weekly (52), every two weeks (26),
  twice monthly (24) and monthly (12). Estimated annual employee share uses
  the exact unrounded monthly difference × 12.
- **Simulate paycheck** adds a dated sample deduction to this user's
  `payroll_demo_deductions` and creates an in-app demo notification.
  Duplicate scenario/payday combinations are rejected. Sample history holds
  up to the latest 100 entries. Removing a scenario also removes its simulated
  history entries.
- No employee paycheck or salary data, employer identifiers, payment card
  information, real payroll integrations, enrollment or insurer transactions
  are collected or affected. Simulated deductions never change the separate
  `payment_history` ledger. This is classroom functionality only.

### Database and testing
- Local H2 adds the `assessment_ai_reviews`, `payroll_simulations`, and
  `payroll_demo_deductions` tables automatically without deleting existing
  records. The additive **`database/migrations/V7__ai_assessment_and_payroll_demo.sql`**
  is the matching MySQL migration; **apply after V6**, only when explicitly
  preparing a reviewed, backed-up production deployment.
- Run `mvn test` and the usual `local` Spring Boot profile. Verify a
  new assessment, AI consent off/on, behavior with no Gemini key, saved review
  persistence, user isolation, payroll frequency calculations, duplicate
  paycheck prevention, fake history, deleting a scenario, and that billing
  remains unchanged. No code in this branch changes live Railway automatically.


## Concise demo presentation (November 2026)

Normal pages use concise, professional labels and actions without repeating
classroom disclaimers or lengthy warnings. The homepage, plans, assessment,
AI results, support, issue tracker, billing, identity-check practice,
enrollment handoff and dashboard have been cleaned up. The footer has a
single unobtrusive "Student project" label.

Gemini analysis remains **explicitly opt-in**; the brief checkbox clearly
states that general preferences are shared with Google Gemini. Server-side
redaction, account isolation and non-fabrication rules are unchanged. The AI
prompt now emphasizes focused comparisons rather than boilerplate warnings.

The standalone payroll calculator was an extra interpretation of requirement
12; its links have been removed from main navigation, dashboard and billing
without deleting the feature or stored simulations. Direct `/payroll` access
is retained for existing records, should the class decide to use it later.
No actual insurance actions, payments or payroll activities are performed.


## Completing class requirements #5, #6 and #12

The existing account-only Billing page now has **Pay bill (sample)** next to each
planned expense. It moves that one account-owned entry to recorded-payment
history, saves a `GISU-SAMPLE-` reference, and creates an in-app confirmation.
Repeated requests, another account's bill and already-paid bills cannot
repeat that action. **No card data is requested and no payment processor is
contacted.** Existing "Mark paid" and manual history functionality remains.

The Notifications screen now contains policy-renewal dates. A member can save
or remove a renewal date. A renewal notification appears in the account when
the date is within 30 days; planned bill due dates generate a one-time alert
when due within 7 days. Dashboard and Notifications visits refresh alerts.
This is a deliberate **on-visit in-app system**, not an email/SMS scheduler;
nothing is pushed to users while they're signed out. The
`gisu_notification_receipts` table prevents repeated bill reminders. A
policy-selection update is also issued when saving an enrollment request.

From an official plan enrollment page, signed-in users can choose a preferred
start date and **Save enrollment request**. Requests are private, visible at
`/enrollment/requests` and on the dashboard, and can be removed. They remain
at the "Ready to apply" stage with a link to the official insurer/marketplace.
This never sends an application or asserts an active policy; external
enrollment links and historical referral logging continue to work as before.
The earlier separate payroll deduction calculator is preserved but intentionally
kept out of the main navigation.

`database/migrations/V8__enrollment_requests_and_reminders.sql` adds the
request, renewal and reminder-dedupe tables to MySQL. The `local` H2 schema
creates them automatically. **Do not apply V8 to Railway or deploy without
explicit authorization.** The new `RequirementsFinishFlowTest` covers
customer scoping, sample payment idempotence, payment-due and renewal
notification deduplication, and the local enrollment-request workflow.

## Final UI and deployment preparation

See [DEPLOYMENT_READINESS.md](DEPLOYMENT_READINESS.md) for the cross-page
responsive/keyboard test matrix, environment checklist, production migration
inspection order, and security gates. GitHub Actions now runs
`mvn -B -ntp verify` (test + package) rather than only `mvn test`.
The Create Account page uses the same card layout as Sign In.

The application uses secure HTTP-only, SameSite=Lax session cookies in the
default server profile and explicitly permits HTTP cookies in the `local`
profile. **No production environment variables, database migrations, Railway
service, or `main` branch were changed by this preparation.**


## Final security protection and assessment labels

Spring Security now enforces **CSRF tokens for all POST actions** (including
registration, sign-in, billing, and chat). Server-rendered forms include
Thymeleaf's hidden token; the chat widget sends the token with its JSON
request. In-app sign-in/2FA continues to use the existing account session,
rather than the Spring Security sample login page.

A bounded in-memory rate limiter adds limits on repeated sign-in attempts,
registrations, chat calls and AI assessment requests. This is suitable for
the single-instance classroom setup; a multi-instance public deployment
would need shared/edge throttling as well. Run `mvn verify` and check
`SecurityHardeningFlowTest` before deployment.

The Gemini assessment will never display catalog identifiers such as
`gsu-plan-1` or `gisu-plan-01` in explanatory prose. It replaces known
IDs with their real plan names, including on previously saved reviews,
without changing the catalog keys used for links and storage.
