# Batch 197 - Student Rules Confirmation Audit

## Scope
- Persisted the student rules confirmation timestamp on each attempt.
- Builds directly on Batch 196, where first-entry confirmation became required.

## Changes
- Added `exam_attempt.rules_confirmed_at` to `schema.sql`.
- Added idempotent startup migration for old databases.
- First start of a `status = 0` attempt now writes `rules_confirmed_at = COALESCE(rules_confirmed_at, NOW())`.
- Student exam list and start/recovery payloads expose `rulesConfirmedAt`.

## Why This Matters
- Rules confirmation is now auditable from the server-side attempt record.
- Retry or replay of the start endpoint will not overwrite the original confirmation time.
- Future admin/teacher audit views can use the attempt row directly without reconstructing client behavior.

## Verification
- Quality gates require the schema column, migration, start-time write, recovery projection, and frontend type exposure.
