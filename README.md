# GISU — Georgia Insurance Search & Understanding

GISU is evolving into a Georgia-focused health insurance discovery and comparison platform. It helps people understand plan options, compare verified information, and continue to the official insurer or Georgia Access enrollment experience.

## Product direction

- Browse without creating an account.
- Enter a Georgia ZIP code and household information.
- Complete a guided insurance-needs assessment or browse manually.
- Compare real plans and clearly labeled plan details.
- See where plan information came from and when it was last updated.
- Continue to the official insurer or Georgia Access site for quotes, eligibility, and enrollment.

GISU does not issue insurance, determine eligibility, bind coverage, collect premiums, or submit claims to insurers. Payment history, claim tracking, and enrollment status inside the prototype are informational records only unless a verified external integration is added.

## Official Georgia plan data

The initial catalog source is the Georgia Access Public Use Files (PUFs):
https://georgiaaccess.gov/public-use-files/

Georgia Access publishes Plan, Rate, Benefits and Cost Sharing, and Service Area files for plan years including 2027. Import related files together and retain source filenames, plan year, and source update dates. A premium must never be shown as a personalized quote unless it is calculated using the correct rating inputs and applicable eligibility rules.

## Stack

- Java / Spring Boot
- Thymeleaf
- MySQL
- Maven
- Railway deployment

## Database migration

Apply `src/main/resources/db/migration/V2__georgia_marketplace_catalog.sql` to the existing `gisu` database after confirming the current application tables are present. This migration adds separate marketplace catalog and support tables; it does not delete or rename existing tables.

## Data quality rules

1. Do not seed fictional plans or prices as real listings.
2. Preserve source plan identifiers and plan year.
3. Store service areas by county/ZIP and filter before recommending.
4. Display source and data-as-of dates on plan details.
5. Distinguish a published rate from an actual user quote.
6. Link to official enrollment resources and state clearly that eligibility and final pricing are confirmed there.
