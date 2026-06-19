# 438 Missing Score Recalculation

## Scope
- Close the `UNSCORED_COMPLETED` / `SCORE_MISSING` blocker loop.
- Provide a guarded backend action and frontend shortcuts to recalculate missing attempt totals before score release.

## Backend Changes
- `POST /api/exams/{id}/scores/recalculate-missing`
  - Requires `ADMIN` or `TEACHER`.
  - Reuses exam ownership checks.
  - Locks the exam row during recalculation.
  - Rejects exams whose scores are already published.
  - Selects only completed attempts with `score IS NULL`.
  - Requires existing answer records.
  - Skips attempts with pending answer review.
  - Sums answer scores within the exam question snapshot, falling back to the paper question scope when no snapshot exists.
  - Records operation log `RECALCULATE_MISSING_SCORES`.

## Safety Boundary
- Does not publish scores.
- Does not create or change score release records.
- Does not overwrite non-null attempt scores.
- Does not change answer records, review records, appeals, monitor events, or snapshots.
- Does not notify students.
- Does not expose correct answers, answer content, analysis, or unreleased student score details.

## Frontend Changes
- Score Release Safety rows with `UNSCORED_COMPLETED` add `Recalculate`.
- Score Release Readiness blocker details add `Recalculate`.
- Lifecycle Health rows for `SCORE_MISSING` add `Recalculate`.
- After recalculation, the page refreshes:
  - exam list
  - current readiness drawer
  - score safety
  - lifecycle health
  - lifecycle handoff
- The latest operation audit ID is shown through the existing audit banner.

## Verification
- Run fast quality gate.
- Run frontend build.
- Run full quality gate.
