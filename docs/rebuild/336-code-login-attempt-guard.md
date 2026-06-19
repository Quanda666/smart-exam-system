# Batch 336: Code Login Attempt Guard

## Background

Password login already used `LoginAttemptGuard` to temporarily lock an account after repeated failures. Email code login only wrote failure audit logs, so repeated wrong-code attempts were not throttled by the same lock policy.

## Changes

- Sending a login code now checks `LoginAttemptGuard` for the email account.
- Code login now checks the same lock guard before verification.
- Failed code login attempts call `recordFailure(account)`.
- Successful code login calls `recordSuccess(account)`.
- Operation logs for `CODE_LOGIN_FAILED` remain unchanged, so audit and lock behavior now work together.

## Three-Terminal Collaboration

- Student/teacher/admin recipient terminal: repeated incorrect code-login attempts are temporarily locked like password login.
- Admin/audit terminal: failed code logins are still visible in operation logs.
- Backend security foundation: password and code login now share one lockout model.

## Verification

Local quality gates assert that code login and login-code sending use `LoginAttemptGuard` for lock, failure, and success tracking.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
