# 423. Score Release Safety Workbench

## Scope

This batch turns per-exam score release readiness into a batch safety workbench for teachers and administrators.

Before this step, users had to open each exam readiness drawer one by one to understand whether scores could be published. The new flow keeps the existing strict publish guard, but adds a paginated workbench for finding ready, blocked, released, revoked, and action-required exams quickly.

## Backend Changes

- Added `GET /api/exams/scores/safety`.
- Added `GET /api/exams/scores/safety/export`.
- Both endpoints require `ADMIN` or `TEACHER`.
- Teacher visibility is scoped through the existing exam ownership and teaching-scope filter.
- The workbench reuses the same blocker rules as single-exam readiness:
  - `ALREADY_RELEASED`
  - `EXAM_NOT_ENDED`
  - `ACTIVE_ATTEMPTS`
  - `PENDING_REVIEW`
  - `PENDING_REVIEW_ANSWERS`
  - `NON_FINAL_ATTEMPTS`
  - `PENDING_APPEALS`
  - `OPEN_RECHECK`
  - `UNSCORED_COMPLETED`
  - `NO_COMPLETED_ATTEMPTS`
- Supported state filters:
  - `ALL`
  - `READY`
  - `BLOCKED`
  - `RELEASED`
  - `REVOKED`
  - `ACTION_REQUIRED`
- The response includes summary counters, blocker distribution, and a normal `PageResult`.
- CSV export is capped at 5000 rows.

## Frontend Changes

- `frontend/src/api/exam.ts` now exposes typed score release safety list/export functions.
- `ExamManagement` now has a `Score Safety` toolbar action.
- The safety drawer supports:
  - keyword search
  - state filter
  - summary metrics
  - top blocker distribution
  - paginated exam table
  - CSV export
- Each row can jump to:
  - readiness detail
  - publish scores
  - blocker resolution route
  - lifecycle timeline

## Coordination Impact

- Admin side: can audit release readiness globally and export the current safety view.
- Teacher side: can find exams that need review, appeal handling, recheck closure, or forced submission follow-up without checking each exam manually.
- Student side: no direct data exposure change. The workbench does not reveal answer content, raw unreleased scores, or student-level score details.
- Review and appeal modules remain the source of truth for resolving blockers.

## Safety Invariants

- The workbench is advisory; publishing still calls the existing `publishScores` backend flow.
- The backend publish guard still checks exam time, active attempts, pending reviews, pending answer reviews, non-final attempts, appeals, rechecks, missing scores, and completed attempts.
- Released scores remain visible to students only through the existing score release visibility rules.
- Revoked releases remain hidden until scores are published again through the guarded path.

## Acceptance Checks

- Admin and teacher users can open the safety workbench from exam management.
- State filters return the matching readiness set.
- Summary counters and blocker distribution reflect the unfiltered result set for the current keyword/scope.
- CSV export downloads the current keyword/state view.
- Row actions reuse existing readiness, publish, resolution, and lifecycle flows.
- Frontend build and full quality gate pass.
