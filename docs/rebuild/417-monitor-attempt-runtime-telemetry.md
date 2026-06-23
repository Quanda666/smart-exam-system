# 417. Monitor Attempt Runtime Telemetry

## Scope

This batch adds live attempt runtime telemetry to the teacher monitoring dashboard.

Before this step, monitoring focused on risk events and teacher actions. Teachers could see heartbeat and submission state, but not enough operational evidence to diagnose draft recovery, timeout pressure, or whether a disconnected student's latest work was saved.

## Backend Changes

- `MonitorService.listExamMonitorSessions` now returns:
  - `lastDraftSavedAt`
  - `draftRevision`
  - `deadlineAt`
  - `remainingSeconds`
- The deadline is server-derived from the stricter of exam end time and per-attempt duration deadline.
- Submitted attempts report `remainingSeconds = 0`.
- Monitor session export now includes:
  - last draft saved time
  - draft revision
  - server deadline
  - remaining seconds

## Frontend Changes

- `MonitorSession` API type now includes draft and deadline telemetry fields.
- `ExamMonitorPanel` adds:
  - `Saved drafts` metric
  - `Time critical` metric for active attempts with five minutes or less remaining
  - `Runtime` table column showing remaining time, server deadline, and draft save revision
- The metric grid is now responsive so the larger operations dashboard remains readable as monitor telemetry grows.

## Coordination Impact

- Teacher side: teachers can decide whether to wait, remind, or force-submit with better evidence.
- Student side: no behavior change; existing heartbeat, autosave, timeout submit, and local monitor queue continue to work.
- Admin side: exported monitor sessions now carry draft/deadline evidence for incident review.

## Acceptance Checks

- Monitor session API includes draft timestamp, draft revision, deadline, and remaining seconds.
- Exported monitor sessions include the same runtime evidence.
- The monitor dashboard displays runtime telemetry without changing risk event or action flows.
- Time critical count only includes active attempts near or past deadline.
