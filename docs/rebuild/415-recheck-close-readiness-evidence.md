# 415. Recheck Close Readiness Evidence

## Scope

This batch adds evidence-based confirmation before a teacher closes a score appeal recheck.

Before this step, the backend blocked invalid close operations, but the teacher only saw a free-text prompt. The teacher could not inspect which answers were reopened, which answers had been reviewed again, or which score audit logs proved the recheck work.

## Backend Changes

- Added `GET /api/reviews/appeals/{id}/recheck/readiness`.
- Added `ScoreAppealService.recheckReadiness(id, handler)`.
- The readiness response includes:
  - appeal and exam context
  - `requiredRecheckAnswerCount`
  - `reviewedRecheckAnswerCount`
  - `pendingRecheckAnswerCount`
  - `reviewScoreLogCount`
  - `recheckAttemptFinalized`
  - `closeAllowed`
  - `closeBlockers`
  - `recheckOpenedAt`
  - per-answer recheck evidence rows
- Per-answer evidence rows include answer/question IDs, question type, current score, review status, latest review score log ID, old/new score, reviewer, review time, and stem.
- Evidence is scoped to the appealed question when the appeal targets a single question.
- Whole-paper rechecks include manually reviewed question types (`FILL_BLANK`, `SUBJECTIVE`) inside the expected exam question scope.

## Frontend Changes

- `ReviewPanel` no longer closes a recheck directly from a prompt.
- Clicking `Complete Recheck` opens a `Close Recheck` drawer.
- The drawer loads backend readiness and shows:
  - required/reviewed/pending answer counts
  - score audit log count
  - attempt finalization state
  - close blockers
  - per-answer scoring evidence
- The close note input and close action are disabled until `closeAllowed = 1`.
- The final close call still uses `POST /api/reviews/appeals/{id}/recheck/close`, so backend hard constraints remain authoritative.

## Close Blockers

- `APPEAL_NOT_OPEN_RECHECK`: the appeal is not an open recheck-required appeal.
- `NO_RECHECK_ANSWERS`: no answer can be associated with the recheck scope.
- `PENDING_RECHECK_ANSWERS`: some reopened answers are still pending review.
- `ATTEMPT_NOT_FINALIZED`: the attempt does not have final status and score.
- `NO_REVIEW_SCORE_LOGS`: no review score audit row exists after the recheck was opened.

## Coordination Impact

- Teacher side: teachers can close a recheck only after inspecting evidence and entering a final note.
- Student side: unchanged visibility rules; students still receive the existing recheck-completed notification after close.
- Admin side: score appeal logs and review score logs remain the audit evidence source for later dispute review.

## Acceptance Checks

- `GET /api/reviews/appeals/{id}/recheck/readiness` returns evidence only after teaching-scope access passes.
- A recheck with pending answers returns `closeAllowed = 0` and `PENDING_RECHECK_ANSWERS`.
- A finalized reviewed recheck with score logs returns `closeAllowed = 1`.
- The frontend close drawer disables closing when readiness is blocked.
- The frontend close drawer displays review score log IDs for reviewed answers.
