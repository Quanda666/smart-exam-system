# 138. Review Correctness Sync

## Problem

Manual review updated `answer_record.score` and `review_status`, but left
`is_correct` unchanged.

That created an inconsistency after subjective grading or score recheck:
a teacher could give full credit, while the student wrong-question book and
learning analysis still treated the answer as wrong because they rely on
`answer_record.is_correct`.

## Change

- `ReviewService.submitReview` now calculates `reviewedCorrect` from the frozen
  max score used for review validation.
- A manually reviewed answer is considered correct when:
  `review.score >= answer.maxScore`.
- `answer_record` now updates `score`, `is_correct`, and `review_status`
  together.
- Quality gates now require correctness synchronization during manual review.

## Three-End Impact

- Teacher end: full-credit manual grading updates the answer state consistently.
- Student end: wrong-question lists no longer show full-credit reviewed answers
  as wrong after release or recheck closure.
- Analysis end: student mastery and wrong-answer metrics use the reviewed score
  state instead of stale automatic correctness.

## Acceptance

- Manual review still enforces non-negative scores and max-score bounds.
- Full-credit reviewed answers set `is_correct = true`.
- Partial-credit or zero-credit reviewed answers remain incorrect.
- Review score audit logs continue to record old and new scores.
