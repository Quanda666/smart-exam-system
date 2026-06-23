# 439 Active Attempt Finalization

## Scope
- Close the `ACTIVE_ATTEMPTS` / `NON_FINAL_ATTEMPTS` score-release blocker loop after an exam has ended.
- Provide a guarded bulk action that force-submits still-active attempts from saved drafts before score release.

## Backend Changes
- `POST /api/exams/{id}/attempts/finalize-active`
  - Requires `ADMIN` or `TEACHER`.
  - Reuses exam ownership checks.
  - Locks the exam row during finalization.
  - Rejects exams whose scores are already published.
  - Allows bulk finalization only after the exam has ended or been closed.
  - Selects only active attempts with `status = 1`.
  - Reuses the standard `finalizeAttempt` path with `submit_type = FORCED`.
  - Uses saved draft answers; missing answers are recorded as unanswered.
  - Objective and fill-blank answers are scored by existing rules.
  - Subjective answers remain pending manual review.
  - Records operation log `FINALIZE_ACTIVE_ATTEMPTS`.

## Safety Boundary
- Does not publish scores.
- Does not create or change score release records.
- Does not force-complete pending manual review.
- Does not overwrite already finalized attempts.
- Does not run while the exam window is still open unless the exam has been explicitly closed.
- Does not notify students.

## Frontend Changes
- Score Release Safety rows with active/non-final blockers add `Finalize`.
- Score Release Readiness blocker details add `Finalize` for `ACTIVE_ATTEMPTS` and `NON_FINAL_ATTEMPTS`.
- Lifecycle Health `Finalize` now calls the guarded backend action instead of only opening the snapshot drawer.
- After finalization, the page refreshes:
  - exam list
  - current readiness drawer
  - score safety
  - lifecycle health
  - lifecycle handoff
  - snapshot drawer when open
- If manual review remains, the UI warns that review must still be completed before score release.

## Verification
- Run fast quality gate.
- Run frontend build.
- Run full quality gate.
