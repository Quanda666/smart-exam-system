# Batch 334: Account Session Revocation

## Background

Account security notifications are only half of the lifecycle. Password changes, administrator password resets, account disablement, and deletion must also invalidate existing sessions. Before this batch, tokens were checked against current user status on each request, but password changes and resets did not actively revoke old tokens.

## Changes

- `TokenStore` now supports `revokeUserTokens(userId)`.
- Self-service password change revokes all sessions for the affected user after persisting the new password and security notification.
- Administrator password reset revokes all sessions for the affected user.
- Disabling a user revokes that user's sessions.
- Deleting a user revokes that user's sessions before clearing role and teaching-scope relations.

## Three-Terminal Collaboration

- Student/teacher/admin recipient terminal: after password change or reset, old browser sessions must re-authenticate.
- Admin terminal: disabling or deleting an account immediately invalidates active sessions for that account.
- Audit terminal: account notification audit and operation logs remain available while token state reflects the security-sensitive account transition.

## Verification

Local quality gates assert the user-token revocation API and all account lifecycle call sites.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
