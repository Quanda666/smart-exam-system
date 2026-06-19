# Batch 338: Teacher Registration Review Deep Link

## Background

Teacher self-registration creates an inactive account that requires administrator review, but administrators did not receive a precise notification and could not jump directly to the pending teacher row. This left the teacher approval workflow dependent on manual user-list filtering.

## Changes

- Teacher self-registration now sends administrators an `ACCOUNT_REVIEW` notification.
- The notification is related to `USER + userId`.
- The notification links to `/system/users?userId=<userId>`.
- User list API accepts optional `userId` filtering.
- `UserManagement` reads `route.query.userId`, loads the exact user, and highlights the focused row.

## Three-Terminal Collaboration

- Teacher terminal: self-registration still creates an inactive teacher account awaiting review.
- Admin terminal: administrators receive a notification and land directly on the pending teacher account.
- Audit terminal: notification delivery can be traced through the existing `USER + userId` relation.

## Verification

Local quality gates assert the administrator notification contract, backend `userId` filter, frontend route hydration, and focused row styling.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
