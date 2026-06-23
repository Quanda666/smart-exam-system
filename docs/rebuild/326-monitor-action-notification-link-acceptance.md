# Batch 326: Monitor Action Notification Link Acceptance

## Background

Batch 325 made monitor warning and force-submit notifications link students to the exact exam attempt. The acceptance script still only checked partial delivery, so a future regression could keep `relatedType = EXAM_ATTEMPT` while silently dropping the concrete student link.

## Changes

- Added `Assert-StudentExamAttemptNotificationLink` to the attempt resilience verifier.
- `CheckMonitorWarnNotification` now verifies:
  - `type = MONITOR_WARNING`
  - `relatedType = EXAM_ATTEMPT`
  - `relatedId = <attemptId>`
  - `link = /student/exams?attemptId=<attemptId>`
- `CheckMonitorForceSubmitTransaction` now queries the student's related notifications and verifies the `MONITOR_FORCE_SUBMIT` notification has the same exact attempt link.
- The local quality gate now checks that the verifier contains these monitor notification link assertions.

## Verification

Run the full local quality gate:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```

For live acceptance against a running environment:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorWarnNotification `
  -SkipSubmit `
  -CleanupAfterRun
```

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-attempt-resilience-acceptance.ps1 `
  -AdminUsername admin `
  -AdminPassword your-password `
  -CheckMonitorForceSubmitTransaction `
  -CleanupAfterRun
```
