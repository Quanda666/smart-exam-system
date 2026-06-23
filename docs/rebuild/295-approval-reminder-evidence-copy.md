# 295. Approval Reminder Evidence Copy

Approval reminder logs already had a drawer, export, and notification-audit jump for sent reminders. Administrators still needed a faster way to copy the reminder log id or a notification-audit deep link when writing SLA review notes or sharing evidence with another administrator.

## Changes

- Added clipboard helpers for approval reminder evidence:
  - raw reminder log id
  - notification audit deep link for `APPROVAL_REMINDER / reminderLogId`
- Added a `Log ID` column to the approval reminder log drawer.
- Added copy buttons for:
  - approval reminder log id
  - related notification audit link
- Kept the existing `View` action for sent reminders unchanged.
- Extended quality gates for the clipboard helpers and drawer controls.

## Three-End Impact

- Admin end: can copy stable approval reminder evidence without manually composing notification audit filters.
- Teacher end: no direct UI change.
- Student end: no direct UI change.

## Acceptance

- Reminder rows show `#<id>` in the drawer.
- Copying the id writes the raw reminder log id.
- Copying the audit link writes `/monitor/logs?tab=notification&relatedType=APPROVAL_REMINDER&relatedId=<id>`.
- The reminder remains an audit/SLA record, not a student-facing workflow change.
