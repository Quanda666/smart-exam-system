# Batch 333: Self-Service Account Security Notifications

## Background

Batch 332 covered administrator password resets. Two self-service account security events still only wrote operation logs: changing the current password and binding or changing email. In a real exam system, these events should be visible to the affected user and auditable through the same account notification relation.

## Changes

- Self-service password change now sends `ACCOUNT_PASSWORD_CHANGED`.
- Email binding or change now sends `ACCOUNT_EMAIL_BOUND`.
- Both notifications are related to `USER + userId`.
- Both notifications link to `/account/profile?panel=security`.
- Email binding now checks the update row count before reporting success.

## Three-Terminal Collaboration

- Student/teacher/admin recipient terminal: account security changes create visible in-app security notifications.
- Admin terminal: the existing per-user `通知审计` action can verify delivery for these self-service events.
- Audit terminal: account lifecycle notifications now share a consistent `USER + userId` relation across admin-driven and self-service changes.

## Verification

Local quality gates assert that `AuthService` sends self-service account security notifications with the correct relation and security-center link.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
