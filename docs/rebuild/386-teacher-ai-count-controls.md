# 386. Teacher AI Count Controls

## Background

The backend now rejects invalid AI generation counts instead of silently capping them. The teacher terminal
still had material-generation count controls capped at 20 per type and no frontend total-count guard, while
the backend allows up to 30 per type and 30 total.

That mismatch could make valid requests unavailable in the UI and invalid total requests fail only after
upload.

## Changes

- Added frontend constants for AI generation count limits:
  - batch question count: 10.
  - material per-type count: 30.
  - material total count: 30.
- General AI question count input now uses the shared frontend constant.
- Material question type inputs now allow up to 30 per type.
- Material upload is blocked before file selection/submission when the total count is 0 or greater than 30.
- Added a quality gate to keep `QuestionBankPanel` aligned with backend AI count limits.

## Three-Terminal Impact

- Teacher terminal: AI generation forms now match backend count limits before submission.
- Administrator terminal: AI usage requests are less likely to contain frontend/backend limit drift.
- Student terminal: downstream generated questions originate from explicit teacher-side accepted quantities.

## Acceptance

- General AI generation count input is capped at 10.
- Material per-type inputs are capped at 30.
- Material total count greater than 30 is blocked in the teacher terminal before upload generation.
- Full quality gates must pass after this step.
