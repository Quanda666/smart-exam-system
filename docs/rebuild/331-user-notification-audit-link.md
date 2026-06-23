# Batch 331: User Notification Audit Link

## Background

Batch 330 made account-enabled notifications precise by linking them to `/account/profile` and relating them to `USER + userId`. Administrators still had to open notification audit manually and type the relation filters to verify delivery for a specific account.

## Changes

- `UserManagement` now exposes a per-row `通知审计` action.
- The action copies `/monitor/logs?tab=notification&relatedType=USER&relatedId=<userId>`.
- The existing notification audit page already hydrates `relatedType` and `relatedId`, so the copied link opens the exact account notification history.

## Three-Terminal Collaboration

- Admin terminal: after enabling an account, administrators can copy the affected user's notification audit link directly from user management.
- Teacher/student/admin recipient terminal: account notifications still open the personal information dialog through `/account/profile`.
- Audit terminal: notification delivery can be verified by user relation without manually entering filters.

## Verification

Local quality gates assert the per-user notification audit action and the `USER + userId` clipboard deep link.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
