# 430 Lifecycle Health Handoff

## Scope

- Add an operations handoff package for lifecycle health.
- Help administrators and teachers copy a compact current-state summary into meetings, work orders, or class operation groups.
- Keep handoff read-only; actual actions still use approval, monitor, review, readiness, and score release guards.

## Backend

- Added `GET /api/exams/lifecycle/health/handoff`.
- Required roles: `ADMIN`, `TEACHER`.
- Query params:
  - `keyword`: optional exam or paper search keyword.
  - `state`: optional lifecycle filter, default `ALL`.
- Reuses the same `examLifecycleHealthRows(...)` data source as the workbench and CSV export.
- Response includes:
  - generation time, role, keyword, normalized state
  - lifecycle summary
  - filtered total and action row total
  - top blockers
  - group, state, and severity distributions
  - up to 20 prioritized action rows with target paths

## Frontend

- `frontend/src/api/exam.ts` adds handoff response types and `getExamLifecycleHealthHandoff(...)`.
- `ExamManagement.vue` lifecycle health drawer adds a `Handoff` button.
- Handoff drawer shows:
  - summary metric cards
  - top blockers
  - copyable Markdown handoff text
  - prioritized action rows with open links

## Coordination Impact

- Admins can produce a global lifecycle handoff before exam operation shifts.
- Teachers can produce a scoped handoff for their own exams.
- The handoff text is short enough to paste into an external issue, chat, or daily operation note.
- Target paths point back into existing handling workbenches, reducing manual route hunting.

## Safety Invariants

- Handoff does not expose student answer content.
- Handoff does not expose correct answers, analysis, rubrics, or unreleased raw scores.
- Monitor risk remains risk evidence only, not a cheating verdict.
- Handoff links do not bypass backend state-machine checks.
- Teacher handoff follows the same exam scope as the lifecycle health workbench.

## Acceptance Checks

- `/api/exams/lifecycle/health/handoff` returns scoped handoff data for admin and teacher users.
- Current keyword and lifecycle state filters are reflected in the handoff.
- Handoff drawer opens from the lifecycle health workbench.
- Copy button writes Markdown text to clipboard.
- Open buttons route to existing handling pages.
- Frontend build passes.
- Full quality gate passes.
