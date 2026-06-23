# 440 Review To Score Release Handoff

## Scope
- Close the workflow gap after manual review is submitted.
- Let teachers move directly from a completed review queue to the score release readiness check for the same exam.

## Backend Changes
- `POST /api/reviews/attempt/{attemptId}` now returns release handoff context:
  - `attemptId`
  - `examId`
  - `examName`
  - `examPendingReviewAttemptCount`
  - `examPendingReviewAnswerCount`
  - `examActiveAttemptCount`
  - `examUnscoredCompletedAttemptCount`
  - `scoreReleaseStatus`
  - `examReviewComplete`
  - `scoreReleaseHandoffReady`
- The review scoring path still uses the existing score range validation and attempt total recomputation.

## Frontend Changes
- Review submission records a handoff state when the exam review queue is cleared.
- ReviewPanel shows a `Score Readiness` handoff alert after the final review for an exam.
- Review progress rows whose release gate is clear add a `Readiness` shortcut.
- The shortcut routes to `/exam-tasks?examId={id}&scoreReadiness=1`.
- ExamManagement now consumes `scoreReadiness=1` and automatically opens the score release readiness drawer for the focused exam.

## Safety Boundary
- Does not publish scores.
- Does not bypass score release blockers.
- Does not expose student-visible scores.
- Does not change answer, review, appeal, or score release records beyond the existing review submission behavior.
- Score readiness remains backend-authoritative.

## Verification
- Run fast quality gate.
- Run frontend build.
- Run full quality gate.
