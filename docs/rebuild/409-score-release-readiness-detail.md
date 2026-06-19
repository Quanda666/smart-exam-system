# 409 Score Release Readiness Detail

## Scope
- Add a teacher/admin score release readiness preflight before publishing scores.
- Make score publishing block on answer-level pending review, not only attempt-level status.
- Keep the frontend list state and backend publishing guard aligned through a single readiness payload.

## Backend
- `GET /api/exams/{id}/scores/readiness` returns the current release state for an owned exam.
- The readiness payload includes:
  - `ready` and `scoreReleaseReady`
  - `blockers` and `scoreReleaseBlockers`
  - `blockerDetails` with `code`, `message`, `count`, and `action`
  - attempt counts, scored completed counts, pending review counts, appeal counts, and release status
- The release blocker set now includes `PENDING_REVIEW_ANSWERS` from `answer_record.review_status = 0`.
- `publishScores` keeps the database transition lock and now refuses release while any answer record is still waiting for review.

## Teacher UI
- The publish action calls `getScoreReleaseReadiness` immediately before showing the confirmation dialog.
- If readiness fails, the teacher sees blocker details and the publish request is not sent.
- If readiness passes, the confirmation dialog uses live scored-attempt counts instead of stale list data.
- The exam row is patched with the latest readiness values so the button state reflects the current backend state.

## Three-End Coordination
- Admin and teacher release actions share the same backend readiness endpoint and ownership checks.
- Student score visibility remains gated by release status, finalized attempts, non-null scores, and no open recheck.
- Review and appeal workflows now directly block score publication until their pending work is closed.

## Verification
- `scripts/run-quality-gates.ps1` checks the backend endpoint, blocker code, answer-review guard, frontend API, and preflight-before-publish flow.
- Frontend build should pass after the new `ScoreReleaseReadiness` type and publish preflight are compiled.
