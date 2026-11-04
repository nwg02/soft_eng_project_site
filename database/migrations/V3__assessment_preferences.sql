-- GISU Assessment upgrade (MySQL). Apply once after V2, before deploying
-- the redesigned assessment/dashboard code. Existing assessment rows remain.
-- Do NOT execute this on H2: application-local uses local-schema.sql instead.
ALTER TABLE insurance_assessments
    ADD COLUMN zip_code VARCHAR(5) NULL,
    ADD COLUMN income_range VARCHAR(40) NOT NULL DEFAULT 'prefer_not',
    ADD COLUMN priority VARCHAR(40) NOT NULL DEFAULT 'balanced';

-- Preferences control the order of curated issuer documents for educational review.
-- ZIP, income, age, budget, and family size do NOT prove service-area eligibility,
-- insurer rates, subsidies, or personalized plan suitability.
