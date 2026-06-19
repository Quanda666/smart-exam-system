# 152 Score Visible Non Null Gate

## Scope

This batch extends the score release contract to old or inconsistent data. A released finalized attempt is not treated as student-visible score data unless `exam_attempt.score` is non-null.

## Changes

- Added `score IS NOT NULL` to student score visibility in `StudentService.getGrades`.
- Blocked `StudentService.getExamResult` for released finalized attempts with missing scores.
- Excluded unscored finalized attempts from wrong book, knowledge mastery, AI wrong-question explanation, student insight, and score exports.
- Added `PENDING_SCORE` visibility for released finalized attempts whose score is missing.
- Updated the student results UI and frontend API types to recognize `PENDING_SCORE`.
- Tightened `OverviewService` student score metrics so wrong count, score trend, and knowledge mastery only use scored released attempts.
- Added quality gates covering backend score visibility and frontend `PENDING_SCORE` handling.

## Why This Matters

Batch 150 prevents new score releases when completed attempts are missing scores. This batch handles historical or manually corrupted records that may already exist in a database.

Student-facing score data, exports, analytics, wrong-book explanations, and mastery views now share the same minimum score visibility contract:

- score release is active
- attempt is finalized
- attempt score is non-null
- no open `RECHECK_REQUIRED` appeal

## Verification

- Java source hygiene
- Frontend source hygiene
- PowerShell parser smoke
- Local quality gates

