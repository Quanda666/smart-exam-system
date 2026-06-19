# 396 Monitor Active Event Time Boundary

## Scope
- Harden student monitor event ingestion while an attempt is still active.
- Keep monitor events as audit/risk records only; this step does not auto-decide cheating.

## Backend
- `MonitorService.requireMonitorEventReportableAttempt` now validates active-attempt `clientEventTime` when the client provides it.
- Active attempt events are rejected when their client time is:
  - earlier than `start_time - 60 seconds`
  - later than server `now + MONITOR_LATE_EVENT_GRACE_SECONDS`
- Submitted attempts continue to require `clientEventTime` and remain bounded by `submit_time + MONITOR_LATE_EVENT_GRACE_SECONDS`.
- The shared start-window check is extracted to `requireMonitorEventAfterAttemptStart`.

## Why This Matters
- The student terminal already reports concrete events such as blur, visibility hidden, full-screen exit, offline/online, copy/paste, back navigation, and page unload attempts.
- Before this step, active attempts could still submit monitor events with implausible future or pre-start client timestamps.
- The monitor dashboard and exported audit logs are more trustworthy when event timestamps stay inside the attempt lifecycle.

## Verification
- `scripts/run-quality-gates.ps1` checks that active monitor events keep the start-window validation and future-time guard.
