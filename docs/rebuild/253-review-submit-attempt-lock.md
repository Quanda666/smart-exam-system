# 253. Review Submit Attempt Lock

## Goal

Make manual review submission deterministic when two teachers submit scores for the same pending attempt at nearly the same time.

## Scope

- Keep review detail reads unlocked.
- Add `requireAttemptPendingReviewForUpdate` for the submit path.
- Lock the `exam_attempt` row before loading pending answer records, inserting review records, writing review score logs, and recalculating the attempt score.
- Preserve existing review rules:
  - the attempt must be in pending-review state;
  - every pending answer must be covered exactly once;
  - each score must be between zero and the question max score;
  - completion still uses the expected question set.
- Extend quality gates so the submit path cannot silently return to an unlocked state check.

## Three-End Coordination

- Teacher end: concurrent review submissions serialize per answer sheet, so the second submit sees the updated attempt state instead of writing duplicate review records.
- Student end: released or rechecked scores are based on one authoritative review pass per pending attempt state.
- Admin/audit end: `review_score_log` remains a clean scoring history instead of receiving parallel duplicate entries for the same review task.

## Acceptance Notes

- The lock is scoped to the single `exam_attempt` row.
- Pending-review list and detail APIs remain normal read operations.
- This complements the score appeal locks from batches 251 and 252.
