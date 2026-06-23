# 115. Monitor Event Late Reporting

## Background

Batch 114 preserved unflushed monitor events on the student client when answer submit succeeded but monitor-event upload failed. The backend still accepted monitor events only while `exam_attempt.status = 1`, which meant preserved events could not be uploaded after the attempt entered submitted, pending-review, or completed states.

This batch closes that server-side gap with a bounded late-reporting window.

## Scope

- Keep active-attempt monitor reporting unchanged.
- Allow submitted attempts to accept late monitor events only when the event carries a valid client event time.
- Reject late monitor events outside the attempt window.
- Prevent late monitor events from downgrading submitted monitor sessions back to online/offline.

## Backend Contract

- `MonitorService.recordCheatEvents`
  - Loads attempts with `status <> 0` for the owning student.
  - For `status = 1`, accepts events as before.
  - For `status >= 2`, requires `clientEventTime`.
  - Rejects submitted-attempt events before the attempt start buffer.
  - Rejects submitted-attempt events after `submit_time + MONITOR_LATE_EVENT_GRACE_SECONDS`.

- `MonitorService.upsertMonitorSession`
  - Uses `monitorStatusForAttemptEvent`.
  - Uses `SUBMITTED` as the session status for late events on submitted attempts.
  - Does not overwrite an existing `SUBMITTED` session status with `ONLINE` or `OFFLINE`.

## Three-End Collaboration

- Student end:
  - Preserved monitor events can still be uploaded after answer submission when they were generated during the attempt window.
  - Answer submission remains non-blocking if monitor upload is temporarily unavailable.

- Teacher end:
  - Monitor dashboard receives late accepted events as audit/risk records.
  - Submitted sessions stay submitted even when late events arrive.

- Admin end:
  - Audit exports include bounded late monitor records.
  - Late event acceptance is constrained by ownership, attempt status, client event time, and submit-time grace.

## Invariant

Late monitor events are accepted only when they plausibly belong to the submitted attempt:

```text
exam_attempt.user_id = current_student
AND exam_attempt.status >= 2
AND client_event_time IS NOT NULL
AND client_event_time <= submit_time + late_event_grace
```

Accepted late events must not change monitor session status away from `SUBMITTED`.

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `MonitorService` has a late event grace constant.
- Monitor event reporting uses `loadStudentMonitorAttempt`.
- Submitted attempts require `clientEventTime`.
- Events outside the late reporting window are rejected.
- Submitted monitor sessions cannot be downgraded by late events.

## Acceptance Points

- Active monitor event reporting still works.
- Submit-time preserved monitor events can be uploaded after answer submission.
- Missing or late fabricated submitted-attempt events are rejected.
- Monitor session status remains `SUBMITTED` after late event ingestion.
