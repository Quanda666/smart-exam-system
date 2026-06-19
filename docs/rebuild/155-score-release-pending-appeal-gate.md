# 155 Score Release Pending Appeal Gate

## Scope

This batch closes a score release loophole around revoked scores and pending appeals.

## Changes

- Added `pendingScoreAppealCount` to teacher/admin exam list rows.
- Disabled score publishing in `ExamManagement` while pending score appeals exist.
- Updated `ExamService.requireExamReadyForScoreRelease` to reject score publishing when any `score_appeal.status = 0` exists for the exam.
- Kept the existing open recheck appeal gate for `status = 1` and `handling_result = 'RECHECK_REQUIRED'`.
- Added quality gates so score release cannot regress to checking only recheck-required appeals.

## Why This Matters

If scores are revoked while a student's appeal is still pending, the system must not allow scores to be republished before that appeal is handled. Otherwise the release workflow can bypass the appeal lifecycle.

Score release now waits for both:

- pending appeals to be handled
- recheck-required appeals to be completed

## Verification

- Java source hygiene
- Frontend source hygiene
- PowerShell parser smoke
- Local quality gates

