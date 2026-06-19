# 427 Exam Lifecycle Health Workbench

## Scope

- Add a list-level lifecycle health workbench for exams.
- Keep the existing single-exam lifecycle timeline, score release readiness, and score safety flows.
- Help administrators and teachers see where an exam is blocked without opening every drawer one by one.

## Backend

- New API: `GET /api/exams/lifecycle/health`.
- Roles: `ADMIN`, `TEACHER`.
- Query parameters:
  - `keyword`
  - `state`
  - `page`
  - `size`
- Supported `state` values:
  - `ALL`
  - `ACTION_REQUIRED`
  - `APPROVAL`
  - `WAITING`
  - `RUNNING`
  - `REVIEW`
  - `SCORE_READY`
  - `RELEASED`
  - `RISK`
- Response shape:
  - `state`
  - `summary`
  - `page`
- Each row includes existing exam fields plus:
  - `lifecycleState`
  - `lifecycleGroup`
  - `lifecycleSeverity`
  - `lifecycleNextAction`
  - `lifecycleNextActionType`
  - `lifecycleActionRequired`
  - `lifecycleRisk`
  - `lifecycleBlockers`
  - `lifecycleBlockerCodes`
- The teacher exam list also receives lifecycle health fields, so the main table can show a health tag directly.

## Lifecycle States

- Approval: `APPROVAL_PENDING`, `APPROVAL_START_PASSED`, `REJECTED`.
- Snapshot: `SNAPSHOT_RISK`.
- Before exam: `WAITING_TO_START`.
- During exam: `RUNNING_HEALTHY`, `RUNNING_NO_ACTIVE`, `TIMEOUT_PRESSURE`, `MONITOR_RISK`.
- After exam: `FINALIZE_REQUIRED`, `REVIEW_REQUIRED`, `RECHECK_REQUIRED`, `APPEAL_REQUIRED`, `SCORE_MISSING`, `NO_COMPLETED_ATTEMPTS`.
- Score release: `SCORE_READY`, `SCORE_BLOCKED`, `SCORE_RELEASED`.
- Fallback: `UNKNOWN`.

## Frontend

- `ExamManagement.vue` now has a `Lifecycle Health` toolbar button.
- The main exam table has a `Health` column with lifecycle state and next-action tooltip.
- The lifecycle health drawer includes:
  - keyword search
  - state filter
  - summary counters
  - top blocker tags
  - paginated health table
  - next action, timeline, and readiness buttons
- Next-action routing reuses existing workbenches:
  - approval queue
  - exam snapshot drawer
  - exam monitor
  - review queue
  - score appeal and recheck routes
  - score readiness drawer
  - score release logs
  - lifecycle timeline

## Three-End Coordination

- Admin side can locate global approval, monitor, snapshot, and release risks.
- Teacher side can locate their own exams that need review, appeals, rechecks, or score publication.
- Student side remains protected: no answer content, correct answers, analysis, or unreleased scores are exposed by this workbench.
- The workbench is advisory and navigational. It does not publish scores, approve exams, force-submit attempts, or modify reviews by itself.

## Safety Invariants

- Score visibility still depends on score release, finalized attempts, non-null score, and no open recheck.
- Monitor risk remains a risk signal only; it is not an automatic cheating verdict.
- Score publish still goes through the existing readiness and publish guards.
- Approval, review, recheck, and force-submit actions still use existing backend-authoritative state transitions.

## Acceptance Checks

- `GET /api/exams/lifecycle/health` returns stable paginated data for admin and scoped teacher users.
- Main exam list shows a health tag for each exam.
- Lifecycle Health drawer can filter by action required, approval, running, review, score ready, released, and risk.
- Next action links open the existing approval, monitor, review, snapshot, readiness, score log, or timeline surfaces.
- Frontend build passes.
- Full quality gate passes.
