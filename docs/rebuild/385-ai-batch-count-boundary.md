# 385. AI Batch Count Boundary

## Background

General AI question generation accepts a teacher-selected `count`. The DTO and frontend already limit this
to 1-10, but the service still used `Math.max(1, Math.min(value, 10))`, silently rewriting invalid internal
or bypassed calls.

That could make audit records and teacher expectations disagree with the accepted generation request.

## Changes

- Added `MAX_BATCH_QUESTION_COUNT = 10`.
- `validateBatchRequest(...)` now validates count in the service layer.
- `normalizedCount(...)` rejects values below 1 or above 10 instead of silently capping them.
- Added a quality gate that blocks the old count capping expression.

## Three-Terminal Impact

- Teacher terminal: invalid AI generation counts fail clearly instead of being silently changed.
- Administrator terminal: AI usage logs reflect explicit accepted request sizes.
- Student terminal: downstream questions originate from teacher-approved generation counts.

## Acceptance

- General AI generation count below 1 is rejected.
- General AI generation count above 10 is rejected.
- Valid counts from 1 to 10 are preserved exactly.
- Material-based generation keeps its separate total and per-type limits.
- Full quality gates must pass after this step.
