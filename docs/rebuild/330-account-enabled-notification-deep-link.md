# Batch 330: Account Enabled Notification Deep Link

## Background

Account enablement is part of the admin-to-user account lifecycle. Before this batch, enabling a disabled account sent an `APPROVAL` notification with no link and no business relation, so the recipient could not jump to account details and administrators could not audit delivery by the affected user.

## Changes

- Account-enabled notifications now use type `ACCOUNT_ENABLED`.
- Notifications link to `/account/profile`.
- Notifications are related to `USER + userId`.
- `App.vue` treats `/account/profile` as an authenticated action route that does not require a side-menu permission.
- `UserProfile` exposes `openProfileDialog()`, letting the action route open the existing personal information dialog.

## Three-Terminal Collaboration

- Admin terminal: enabling a user creates a precise notification bound to that user.
- Teacher/student/admin recipient terminal: clicking the notification opens the personal information dialog from the current workspace.
- Audit terminal: notification records can be filtered by `related_type = USER` and the enabled user ID.

## Verification

Local quality gates assert the backend relation/link contract and the frontend action-route dialog bridge.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
