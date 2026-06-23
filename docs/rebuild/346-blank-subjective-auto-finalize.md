# 346 Blank Subjective Auto Finalize

## Context

Submit finalization already creates one `answer_record` per expected question and reports answered/unanswered counts. A remaining workflow issue was that any subjective question made the whole attempt enter pending review, even when the student left that subjective answer blank. In a real exam, an unanswered subjective answer is a server-known zero score and should not create manual grading work.

## Changes

- Submission finalization now distinguishes blank and non-blank subjective answers.
- Non-blank subjective answers still enter manual review with `review_status = 0`.
- Blank subjective answers are stored as zero-score reviewed records with `review_status = 1`.
- Attempt final status now depends on `hasPendingManualReview`, not on whether the paper merely contains subjective questions.
- Existing answer statistics remain unchanged: blank subjective answers still count as unanswered.
- Quality gates now assert that blank subjective answers cannot force the attempt into pending review.

## Three-End Impact

- Student end: submit feedback still reports unanswered counts accurately; blank subjective answers can be finalized without waiting for a teacher.
- Teacher end: pending review queues contain only answers that actually require human scoring.
- Administrator end: score release readiness is less likely to be blocked by empty manual-review tasks.

## Acceptance

1. Submit an exam containing only auto-graded questions and blank subjective questions.
2. Each blank subjective question has an `answer_record` row with zero score and `review_status = 1`.
3. The attempt reaches completed status when no non-blank subjective answers remain.
4. Submit an exam with at least one non-blank subjective answer.
5. The attempt enters pending review and only the non-blank subjective answer appears in the review queue.
