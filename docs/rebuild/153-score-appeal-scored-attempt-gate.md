# 153 Score Appeal Scored Attempt Gate

## Scope

This batch aligns score appeals with the score visibility contract introduced in batches 150-152.

## Changes

- `ScoreAppealService.submitAppeal` now accepts appeals only for attempts that are:
  - released
  - finalized
  - owned by the current student
  - scored with `exam_attempt.score IS NOT NULL`
- `ScoreAppealService.closeRecheck` now requires the rechecked attempt to be finalized with a non-null score before closing the appeal.
- Added quality gates so appeal submission and recheck closure cannot regress to raw `status = 5` checks.

## Why This Matters

The frontend only allows appeals when a score is visible, but a real system cannot rely on page controls. Direct API calls and historical inconsistent data must still be protected by backend rules.

Appeals now operate only on score data that is actually visible and meaningful to the student.

## Verification

- Java source hygiene
- Frontend source hygiene
- PowerShell parser smoke
- Local quality gates

