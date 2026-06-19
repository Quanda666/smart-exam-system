# 134. Fixture Review Score Log Cleanup

## Problem

`review_score_log` became the grading audit table in batch 104. The attempt
resilience fixture cleanup removed `review_record` and `answer_record`, but did
not remove the matching score audit rows first.

That left fixture cleanup able to create orphan grading logs after test or
pressure-test data was removed.

## Change

- Added `reviewScoreLogs` to `cleanupAttemptResilienceFixtures`.
- Deletes `review_score_log` by `attempt_id` before deleting `review_record`
  and `answer_record`.
- Added a quality-gate check for the cleanup ordering.

## Acceptance

- Fixture cleanup no longer leaves review score audit logs pointing at deleted
  attempts or answer records.
- Review audit history remains intact for real data; this path only applies to
  explicit attempt-resilience fixture cleanup.
