# Batch 332: Password Reset Security Notification

## Background

Administrator password resets are security-sensitive account lifecycle events. Before this batch, the operation produced an administrator operation log, but the affected user received no in-app security notification.

## Changes

- Password reset now sends an `ACCOUNT_PASSWORD_RESET` notification to the affected user.
- The notification is related to `USER + userId`.
- The notification links to `/account/profile?panel=security`.
- The account action route now supports panel routing:
  - `/account/profile` opens the personal information dialog.
  - `/account/profile?panel=security` opens the security center dialog.
- `UserProfile` exposes `openAccountPanel(panel)` for account notification deep-links.

## Three-Terminal Collaboration

- Admin terminal: resetting a password still records an operation audit log.
- Recipient terminal: the affected student, teacher, or administrator can open the notification and land directly in the security center.
- Audit terminal: delivery can be verified through the existing `USER + userId` notification audit link from user management.

## Verification

Local quality gates assert the backend notification relation/link contract and the frontend account panel route bridge.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
