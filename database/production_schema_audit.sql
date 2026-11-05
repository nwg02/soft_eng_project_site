-- GISU production schema inventory. READ-ONLY: SELECT queries only.
-- Run against the intended MySQL database using an authorized read-only connection.
-- No customer data, credentials, or actual record contents are returned.
-- IMPORTANT: 'all markers present' is NOT proof the migration was applied
-- completely: partially executed migrations must be inspected before any writes.

SELECT DATABASE() AS selected_database, VERSION() AS mysql_version;
SELECT TABLE_NAME AS existing_gisu_table
FROM information_schema.tables
WHERE table_schema = DATABASE()
  AND (table_name LIKE 'gisu_%' OR table_name IN (
    'customers','insurance_assessments','enrollments','marketplace_plans',
    'payment_history','saved_curated_plans','enrollment_referrals',
    'account_totp_settings','account_totp_recovery_codes',
    'assessment_ai_reviews','payroll_simulations','payroll_demo_deductions',
    'user_notifications','identity_verification_attempts'
  ))
ORDER BY table_name;

WITH markers AS (
    SELECT 'V2' AS version, 'table' AS marker_type, 'marketplace_plans' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V2' AS version, 'table' AS marker_type, 'payment_history' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V2' AS version, 'table' AS marker_type, 'user_notifications' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V2' AS version, 'table' AS marker_type, 'identity_verification_attempts' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V2' AS version, 'column' AS marker_type, 'enrollments' AS table_name, 'household_size' AS column_name
    UNION ALL
    SELECT 'V3' AS version, 'column' AS marker_type, 'insurance_assessments' AS table_name, 'zip_code' AS column_name
    UNION ALL
    SELECT 'V3' AS version, 'column' AS marker_type, 'insurance_assessments' AS table_name, 'income_range' AS column_name
    UNION ALL
    SELECT 'V3' AS version, 'column' AS marker_type, 'insurance_assessments' AS table_name, 'priority' AS column_name
    UNION ALL
    SELECT 'V4' AS version, 'table' AS marker_type, 'saved_curated_plans' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V5' AS version, 'table' AS marker_type, 'enrollment_referrals' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V6' AS version, 'table' AS marker_type, 'account_totp_settings' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V6' AS version, 'table' AS marker_type, 'account_totp_recovery_codes' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V7' AS version, 'table' AS marker_type, 'assessment_ai_reviews' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V7' AS version, 'table' AS marker_type, 'payroll_simulations' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V7' AS version, 'table' AS marker_type, 'payroll_demo_deductions' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V8' AS version, 'table' AS marker_type, 'gisu_enrollment_requests' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V8' AS version, 'table' AS marker_type, 'gisu_renewal_reminders' AS table_name, '' AS column_name
    UNION ALL
    SELECT 'V8' AS version, 'table' AS marker_type, 'gisu_notification_receipts' AS table_name, '' AS column_name
),
checked AS (
  SELECT version, marker_type, table_name, column_name,
    CASE WHEN marker_type='table' THEN
      (SELECT COUNT(*) FROM information_schema.tables t
       WHERE t.table_schema = DATABASE() AND t.table_name = markers.table_name)
    ELSE
      (SELECT COUNT(*) FROM information_schema.columns c
       WHERE c.table_schema = DATABASE() AND c.table_name = markers.table_name
         AND c.column_name = markers.column_name)
    END AS present
  FROM markers
)
SELECT version, COUNT(*) AS markers_checked,
       SUM(present) AS markers_present,
       CASE WHEN SUM(present) = COUNT(*) THEN 'all markers found'
            ELSE 'missing or partial; inspect before migrating' END AS schema_evidence
FROM checked
GROUP BY version
ORDER BY version;

-- If an individual marker is missing, query information_schema manually.
-- Do not assume that absent version tracking makes a migration safe to repeat.
