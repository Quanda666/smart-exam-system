# Batch 335: Email Code Atomic Consume

## Background

Email verification codes protect code login and email binding. Before this batch, verification read the latest code and then marked records by `email + code + purpose`, which left a replay window under concurrent requests and could mark multiple records if the same random code appeared again.

## Changes

- Verification now loads the latest `email_verification.id`.
- A valid code is consumed with `UPDATE email_verification SET used = 1 WHERE id = ? AND used = 0`.
- If the update affects zero rows, the code is treated as already used.
- The old broad update by `email + code + purpose` is removed.

## Three-Terminal Collaboration

- Student/teacher/admin recipient terminal: code login and email binding now reject replayed codes more reliably.
- Admin/audit terminal: account security notifications and operation logs remain tied to real successful verification flows.
- Backend security foundation: verification code state transitions are now record-scoped and atomic.

## Verification

Local quality gates assert that email verification codes are consumed by ID with an atomic `used = 0` guard.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
