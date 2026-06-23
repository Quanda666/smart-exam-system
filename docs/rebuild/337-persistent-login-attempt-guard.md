# Batch 337: Persistent Login Attempt Guard

## Background

`LoginAttemptGuard` previously stored failure counts in an in-memory `ConcurrentHashMap`. That worked for a single local instance, but it did not survive restarts and could be bypassed when the backend runs multiple instances. This conflicted with the high-availability direction of the rebuild plan.

## Changes

- Added `login_attempt` table to `schema.sql`.
- Added startup self-healing migration `ensureLoginAttemptTable`.
- Reworked `LoginAttemptGuard` to use `JdbcTemplate` instead of in-memory state.
- Failed logins upsert `failure_count` and `locked_until` into `login_attempt`.
- Successful logins delete the account's attempt row.
- Expired locks are cleared when checked.

## Three-Terminal Collaboration

- Student/teacher/admin login: password login and code login now share persistent lock state.
- Backend high availability: lock state survives service restart and is shared by multiple application instances connected to the same database.
- Audit/security: `LOGIN_FAILED` and `CODE_LOGIN_FAILED` logs remain available while lock enforcement becomes durable.

## Verification

Local quality gates assert the table, migration, and database-backed guard implementation.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
