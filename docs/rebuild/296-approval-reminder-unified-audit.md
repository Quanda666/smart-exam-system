# 296. Approval Reminder Unified Audit

Approval reminder runs were visible in the approval queue drawer and could be exported, but the history was not searchable from the unified system log page. This made scheduler/SLA investigations inconsistent with exam approval, score release, appeal, notification, and review score audits.

## Changes

- Added administrator audit APIs:
  - `GET /api/monitor/approval-reminder-logs`
  - `GET /api/monitor/approval-reminder-logs/export`
- Added filters for:
  - exact reminder log id
  - keyword across actor, status, source, node, and message
  - reminder status
  - trigger source
  - created time range
- Added an `Approval Reminder Audit` tab to the system log page.
- Added deep-link hydration:
  - `/monitor/logs?approvalReminderLogId=<id>`
- Added copyable reminder audit IDs and audit deep links in the unified audit table.
- Added a copy action for the unified reminder audit link in the approval queue reminder drawer.
- Extended quality gates for backend routes, service filters, frontend API, system log controls, clipboard helpers, and drawer copy controls.

## Three-End Impact

- Admin end: reminder runs from manual and scheduled jobs can be searched, exported, and shared from the same audit workbench.
- Teacher end: no direct UI change.
- Student end: no direct UI change.

## Acceptance

- An administrator can open `/monitor/logs?approvalReminderLogId=<id>` and land on the matching reminder run.
- The unified audit page can filter and export approval reminder runs.
- The approval queue reminder drawer can copy both notification-audit links and unified reminder-audit links.
- Reminder logs remain operational/SLA records and do not alter approval, notification, or student exam behavior.
