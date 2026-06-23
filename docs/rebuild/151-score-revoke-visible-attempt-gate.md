# 151 Score Revoke Visible Attempt Gate

## Scope

This batch aligns score revoke notifications and audit counts with the same visibility contract used by student result pages.

## Changes

- Updated `ExamService.revokeScores` so `visibleAttemptsBeforeRevoke` only includes attempts that were actually visible to students before revoke:
  - score release is active
  - attempt is finalized
  - attempt score is non-null
  - no open `RECHECK_REQUIRED` score appeal
- Score revoke notifications now target only students whose scores were currently visible.
- Added a quality gate to prevent revoke audit counts from drifting back to raw finalized attempt counts.

## Why This Matters

An open recheck appeal hides the score from the student while the review loop is active. Revoke audit data should therefore not claim that a hidden score was visible or notify the student as though a visible score just changed.

This keeps score release, revoke, student visibility, and audit history on the same business rule.

## Verification

- Java source hygiene
- Frontend source hygiene
- PowerShell parser smoke
- Local quality gates

