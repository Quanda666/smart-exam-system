# 428 Lifecycle Health Action Center

## Scope

- Connect the 427 exam lifecycle health model into administrator and teacher Action Centers.
- Keep lifecycle health as a read-only navigation layer.
- Reuse existing approval, monitor, review, exam management, readiness, and timeline surfaces for actual handling.

## Backend

- `OverviewController.admin()` now passes the current `AuthUser` to `OverviewService.adminOverview(user)`.
- `OverviewService` reuses `ExamService.examLifecycleHealth(null, "ACTION_REQUIRED", user, 1, 5)`.
- Admin Action Center now includes:
  - `lifecycleActionRequiredExams`
  - `lifecycleRiskExams`
  - top lifecycle health rows as `LIFECYCLE_HEALTH` items
- Teacher Action Center now includes:
  - `lifecycleActionRequiredExams`
  - `lifecycleRiskExams`
  - top scoped lifecycle health rows as `LIFECYCLE_HEALTH` items
- Lifecycle item target routing:
  - approval -> `/exam-approvals?examId=...`
  - monitor -> `/exam-monitor?examId=...`
  - review -> `/reviews?reviewExamId=...`
  - recheck -> `/reviews?reviewExamId=...&reviewTaskType=RECHECK...`
  - appeals -> `/reviews?appealExamId=...&appealStatus=0...`
  - default -> `/exam-tasks?examId=...`

## Frontend

- `AdminDashboard.vue`
  - Adds lifecycle counts to `AdminActionCenter`.
  - Adds a `Lifecycle` summary tile.
  - Shows lifecycle rows with the existing Action Center row UI.
- `TeacherDashboard.vue`
  - Adds lifecycle counts to `TeacherActionCenter`.
  - Adds a `Lifecycle` summary tile.
  - Shows lifecycle rows with the existing Action Center row UI.

## Coordination Impact

- Admins can see global lifecycle blockers from the home dashboard.
- Teachers can see scoped lifecycle blockers from their home dashboard.
- The detailed lifecycle health workbench remains in exam management.
- The single-exam timeline remains the evidence view.
- Existing score release readiness and score safety guards remain authoritative.

## Safety Invariants

- Action Center rows do not approve exams, publish scores, force-submit attempts, close rechecks, or alter reviews.
- Student answers, correct answers, explanations, and unreleased scores are not exposed.
- Monitor risk is still a risk indicator only, not a cheating verdict.
- Data scope still follows the same admin/global and teacher/scoped rules from `ExamService.examLifecycleHealth`.

## Acceptance Checks

- `/api/overview/admin` includes lifecycle counts and lifecycle action rows.
- `/api/overview/teacher` includes scoped lifecycle counts and lifecycle action rows.
- Admin and teacher dashboards render lifecycle summary counts.
- Clicking lifecycle rows navigates to existing handling workbenches.
- Frontend build passes.
- Full quality gate passes.
