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
