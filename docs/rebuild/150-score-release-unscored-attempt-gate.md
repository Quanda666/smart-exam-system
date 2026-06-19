# 150 Score Release Unscored Attempt Gate

## Scope

This batch tightens score release so an exam cannot publish scores while any finalized attempt is missing a score.

## Changes

- Added `unscoredCompletedAttemptCount` to teacher/admin exam list rows.
- Disabled the score publish action in `ExamManagement` when finalized attempts have `score IS NULL`.
- Updated `ExamService.publishScores` to reject score release if any completed attempt has a missing score.
- Counted released attempts, notifications, and release audit rows from finalized attempts with non-null scores.
- Added quality gates so future changes cannot publish score releases from unscored completed attempts.

## Why This Matters

`exam_attempt.status = 5` means the attempt is finalized, but it is not enough for score publication if `score` is missing. Publishing that state would create a broken student experience: scores would be released, but result pages, exports, and analytics would not have a valid score to display or aggregate.

This batch makes score publication require a scored final state, matching the student-visible and export-visible score contract.

## Verification

- Java source hygiene
- Frontend source hygiene
- PowerShell parser smoke
- Local quality gates

