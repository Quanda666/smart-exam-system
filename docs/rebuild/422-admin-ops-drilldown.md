# 422. Admin Ops Drilldown

## Scope

This batch turns the administrator operations capacity panel into a drilldown workbench.

Before this step, administrators could see capacity counters but had to jump to unrelated pages or raw logs to find the affected attempts, monitor sessions, or drafts. The new drilldown flow keeps the overview as the first screen and lets admins inspect concrete records directly.

## Backend Changes

- Added `GET /api/overview/admin/ops-drilldown`.
- Added `GET /api/overview/admin/ops-drilldown/export`.
- Both endpoints require `ADMIN`.
- Supported drilldown types:
  - `TIMEOUT_PRESSURE`
  - `DEADLINE_PASSED_ACTIVE`
  - `OFFLINE_MONITOR`
  - `HIGH_RISK_MONITOR`
  - `STALE_DB_DRAFTS`
  - `DIRTY_DRAFTS`
  - `FORCED_SUBMITS_TODAY`
- SQL-backed drilldowns use fixed server-side query templates selected by type.
- Redis dirty draft drilldown uses `ExamDraftCacheService.dirtyDrafts` and enriches active attempt context when available.
- CSV export caps each export at 5000 rows to avoid accidental oversized downloads.

## Frontend Changes

- `frontend/src/api/admin.ts` now exposes typed ops drilldown list/export functions.
- `AdminDashboard` ops cards now provide drilldown actions:
  - Dirty Redis drafts
  - Stale MySQL drafts
  - Timeout pressure
  - Past-deadline active attempts
  - Offline monitor sessions
  - High-risk monitor sessions
  - Forced submissions today
- A shared drawer shows paginated records with exam, student, attempt, deadline, risk, heartbeat, draft, and note fields.
- The drawer supports CSV export for the current drilldown type.

## Coordination Impact

- Admin side: can move from a capacity alert to concrete affected records without leaving the dashboard.
- Teacher side: no workflow change. Monitor and attempt data remain teacher-facing through existing monitor/review pages.
- Student side: no data exposure change. Drilldown does not include answer content or unreleased score data.

## Acceptance Checks

- Admin overview still loads `opsCapacity`.
- Each drilldown type returns a paginated list.
- Drilldown export downloads a CSV for the selected type.
- Dirty Redis drafts are best-effort and do not fail the dashboard when Redis is unavailable.
- Frontend build and full quality gate pass.
