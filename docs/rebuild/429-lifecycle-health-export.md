# 429 Lifecycle Health Export

## Scope

- Add CSV export for the exam lifecycle health workbench.
- Keep export read-only and scoped to administrator or teacher data permissions.
- Use the same lifecycle health aggregation as the UI table so exported evidence matches the workbench.

## Backend

- Added `GET /api/exams/lifecycle/health/export`.
- Required roles: `ADMIN`, `TEACHER`.
- Query params:
  - `keyword`: optional exam or paper search keyword.
  - `state`: optional lifecycle filter, default `ALL`.
- `ExamService.exportExamLifecycleHealth(...)` reuses `examLifecycleHealthRows(...)`.
- Export is capped at 5000 rows to avoid accidental large memory responses.

## CSV Fields

- Exam identity: exam id, exam name, paper, subject.
- Lifecycle decision: state, group, severity, action required, next action, next action type, blockers.
- Publication evidence: targets, candidate snapshots, question snapshots.
- Attempt evidence: total, not started, active, submitted, completed, scored, unscored, forced submit.
- Review evidence: pending review attempts and pending answer reviews.
- Appeal evidence: pending appeals and open rechecks.
- Monitor evidence: sessions, events, offline sessions, high-risk sessions, stale drafts, timeout pressure.
- Score release evidence: status, readiness flag, score blockers.
- Timing evidence: start time and end time.

## Frontend

- `frontend/src/api/exam.ts` adds `exportExamLifecycleHealth(...)`.
- `ExamManagement.vue` lifecycle health drawer adds an `Export` button.
- Export uses the current keyword and state filter.
- Loading and failure states follow the existing score safety export behavior.

## Safety Invariants

- Export does not include student answer content.
- Export does not include correct answers, analysis, or grading rubrics.
- Export does not expose unreleased raw scores.
- Monitor data remains risk evidence only and does not make cheating judgments.
- Export is advisory/evidence only; all state transitions still require existing guarded backend actions.

## Acceptance Checks

- `/api/exams/lifecycle/health/export` downloads CSV for admin and teacher users.
- Teacher export follows the same scoped exam visibility as the lifecycle health workbench.
- Filtering by lifecycle state changes exported rows.
- Lifecycle drawer export button starts a download and restores loading state.
- Frontend build passes.
- Full quality gate passes.
