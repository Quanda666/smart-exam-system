# 431 Lifecycle Health Handoff Notification

## Scope

- Extend lifecycle health handoff from copy-only evidence into in-app notification collaboration.
- Keep the notification action read-only: it creates notification records and does not change exam, attempt, review, monitor, or score states.
- Let operators jump from a notification directly back to the lifecycle health workbench with the same filter context.

## Backend

- Added `POST /api/exams/lifecycle/health/handoff/notify`.
- Required roles: `ADMIN`, `TEACHER`.
- Query params:
  - `keyword`: optional exam or paper search keyword.
  - `state`: optional lifecycle filter, default `ALL`.
  - `audience`: `SELF` or `OPERATORS`, default `SELF`.
- `SELF` sends the handoff notification only to the current user.
- `OPERATORS` sends to:
  - the current user
  - active administrators
  - active admin/teacher users who created the prioritized action-row exams
- Notification type: `LIFECYCLE_HANDOFF`.
- Related type: `EXAM_LIFECYCLE_HANDOFF`.
- Notification link opens `/exam-tasks?lifecycleHealth=1&lifecycleState=...`.

## Frontend

- `frontend/src/api/exam.ts` adds:
  - `ExamLifecycleHandoffAudience`
  - `ExamLifecycleHandoffNotifyResult`
  - `notifyExamLifecycleHealthHandoff(...)`
- `ExamManagement.vue` lifecycle handoff drawer adds:
  - `Notify Me`
  - `Notify Operators`
- Notification links now open the Lifecycle Health drawer automatically and apply:
  - lifecycle state
  - lifecycle keyword

## Three-Side Coordination

- Administrators can push a global lifecycle risk handoff to all active administrators and exam creators.
- Teachers can push a scoped lifecycle handoff without exposing out-of-scope exams.
- Recipients receive an in-app notification through the existing notification center and can jump straight to handling surfaces.
- Students are never recipients of lifecycle handoff notifications.

## Safety Invariants

- Notification content does not include student answers.
- Notification content does not include correct answers, analysis, grading rubrics, or unreleased raw scores.
- Monitor risk remains a risk indicator only.
- Handoff notifications do not bypass approval, monitor, review, recheck, or score-release backend guards.
- Teacher-sent handoffs use the same scoped lifecycle health source as the workbench.

## Acceptance Checks

- `POST /api/exams/lifecycle/health/handoff/notify?audience=SELF` creates a notification for the current admin/teacher.
- `POST /api/exams/lifecycle/health/handoff/notify?audience=OPERATORS` creates notifications only for active operator users.
- Notification center displays the lifecycle handoff notification.
- Clicking the notification opens the lifecycle health drawer with the requested state filter.
- Frontend build passes.
- Full quality gate passes.
