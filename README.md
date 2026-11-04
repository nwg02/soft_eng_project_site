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

The “Ask GISU” button works in **built-in guide mode** without an API key. For genuine Gemini AI replies, provide `GEMINI_API_KEY` as an environment variable in the **same terminal** that runs Spring Boot. The default model is `gemini-2.5-flash-lite`. You can optionally override it with `GEMINI_MODEL`.

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
