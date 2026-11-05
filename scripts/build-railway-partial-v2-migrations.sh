#!/usr/bin/env bash
# GISU: emit the SQL needed when Railway already has
# enrollments.household_size and extension_requests, but none of V2's later
# tables nor V3's assessment preference columns.
#
# Usage:
#   bash scripts/build-railway-partial-v2-migrations.sh |
#     mysql -h PUBLIC_HOST -P PUBLIC_PORT -u root -p gisu
#
# STDOUT contains SQL only. This script does NOT connect to any database.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration_dir="$project_root/database/migrations"
v2="$migration_dir/V2__georgia_marketplace_schema.sql"
files=(
  "$migration_dir/V3__assessment_preferences.sql"
  "$migration_dir/V4__saved_curated_plan_bookmarks.sql"
  "$migration_dir/V5__official_enrollment_referrals.sql"
  "$migration_dir/V6__totp_login_security.sql"
  "$migration_dir/V7__ai_assessment_and_payroll_demo.sql"
  "$migration_dir/V8__enrollment_requests_and_reminders.sql"
)

# Check all inputs BEFORE emitting SQL, to avoid half-written output.
for file in "$v2" "${files[@]}"; do
  if [[ ! -s "$file" ]]; then
    echo "Missing migration: $file" >&2
    exit 1
  fi
done
if ! grep -q '^CREATE TABLE IF NOT EXISTS extension_requests (' "$v2"; then
  echo "The expected V2 anchor is missing. Refusing to guess where to resume." >&2
  exit 1
fi

printf '%s\n' '-- GISU Railway continuation: V2 excluding already applied household_size ALTER.'
awk '/^CREATE TABLE IF NOT EXISTS extension_requests \(/ { emit = 1 } emit { print }' "$v2"
for file in "${files[@]}"; do
  printf '\n-- GISU next migration: %s\n' "${file##*/}"
  cat "$file"
  printf '\n'
done
