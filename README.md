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

## Local setup

1. Create a MySQL database named `gisu`.
2. Run `database/schema.sql` for the original prototype schema.
3. Configure `src/main/resources/application.properties` or environment variables for your local MySQL connection.
4. Run `mvn spring-boot:run`.
5. Open http://localhost:8080.

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
