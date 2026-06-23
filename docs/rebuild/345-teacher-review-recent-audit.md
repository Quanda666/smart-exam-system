# 345 Teacher Review Recent Audit

## Context

Teacher registration review already supported pending and rejected queues, operation log evidence, and notification audit links. The missing piece was a quick retrospective view inside the administrator user workspace: administrators still had to leave the queue and search monitor logs manually to see the latest review decisions.

## Changes

- Backend user status updates now return a `teacherReviewApproved` flag when enabling a pending or rejected teacher profile promotes it to approved.
- Teacher approval operation logs now use the explicit action `审核通过教师` instead of the generic enable-user action.
- `UserOperationResult` includes `teacherReviewApproved`; `OperationLog` includes camel-case fields returned by the monitor log API.
- `UserManagement` loads recent teacher-review operation logs when the selected scope is a teacher review queue.
- The teacher review queue now shows a compact recent audit panel with action, target, operator, timestamp, and copy buttons for operation log IDs and deep links.
- Quality gates now assert the backend approval semantics and the frontend recent-audit entry remain wired.

## Acceptance

1. Open `/system/users?role=TEACHER&status=0&teacherStatus=0` or `teacherStatus=2`.
2. The user list remains scoped to the matching teacher review queue.
3. The recent audit panel shows the latest teacher approval/rejection operation logs.
4. Approving a pending or rejected teacher records an explicit teacher-review approval action and returns `teacherReviewApproved=true`.
5. Administrators can copy the operation log ID or link directly from the queue.
