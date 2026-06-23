# 293. Approval Reminder Log Export

Approval reminder logs already record manual and scheduled overdue-approval reminder runs, including skipped runs. Administrators could view the paged drawer and jump to notification audit for sent reminders, but could not export the reminder task history for offline review.

## Changes

- Added `ExamService.exportApprovalReminderLogs`.
- Added `GET /api/exams/approvals/reminders/export`.
- The export is administrator-only and includes:
  - reminder log id
  - time
  - status
  - trigger source
  - trigger actor
  - overdue and cooldown thresholds
  - overdue exam count
  - recipient count
  - scheduler node
  - duration
  - message
- Added frontend `exportApprovalReminderLogs`.
- Added an Export button to the approval reminder log drawer in `ExamApprovalQueue`.
- Extended quality gates for the backend service, controller route, frontend API, and drawer controls.

## Three-End Impact

- Admin end: can export the global approval reminder run history and keep evidence for SLA review.
- Teacher end: no direct UI change; approval reminder evidence remains an administrator audit concern.
- Student end: no direct UI change.

## Acceptance

- Administrators can open the approval queue, view reminder records, and export the CSV.
- Non-admin users are still rejected by the backend service and controller role guard.
- The export includes skipped scheduler/manual runs as well as sent reminders.
