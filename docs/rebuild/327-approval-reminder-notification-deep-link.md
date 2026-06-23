# Batch 327: Approval Reminder Notification Deep Link

## Background

Approval overdue reminders already create an `exam_approval_reminder_log` record and relate notifications to `APPROVAL_REMINDER + reminderLogId`. The notification link still opened the generic approval queue, so administrators had to manually find the reminder run and its notification audit evidence.

## Changes

- Approval reminder notifications now link to `/exam-approvals?reminderLogId=<id>`.
- The approval reminder log list API accepts an optional `logId` filter.
- `ExamApprovalQueue` reads `route.query.reminderLogId`, opens the reminder log drawer automatically, requests the exact reminder log, and highlights the matched row.
- Existing notification audit links remain available from the reminder log row.

## Three-Terminal Collaboration

- Admin terminal: clicking the overdue reminder notification lands on the exact reminder log run and can jump onward to notification audit.
- Teacher terminal: no workflow change; teachers still receive approval result notifications scoped to their exam.
- Student terminal: no workflow change; student exam publish notifications remain scoped to attempt ids.

## Verification

- Local quality gates assert that the backend sends the exact reminder log link.
- Local quality gates assert that the API, controller, and approval queue page all support `reminderLogId` routing.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
