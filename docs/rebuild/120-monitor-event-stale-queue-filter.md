# 120. Monitor Event Stale Queue Filter

## Background

The student exam center can now retry locally stored monitor queues. A practical resilience issue remained: one malformed or very old event could make a stored queue fail repeatedly. The queue would stay in local storage and keep retrying on every exam-center load.

This batch adds conservative client-side filtering before retry upload.

## Scope

- Keep valid local monitor events retryable.
- Drop malformed stored monitor events.
- Drop events without a parseable `clientEventTime`.
- Drop events older than the local stored-event retention window.
- Keep server-side ownership, status, and late-reporting checks as the source of truth.

## Frontend Contract

- `monitor.ts`
  - Defines `STORED_MONITOR_EVENT_MAX_AGE_MS`.
  - `readStoredMonitorQueue` filters through `isStoredMonitorEvent`.
  - `isStoredMonitorEvent` requires:
    - numeric `attemptId`
    - string `eventType`
    - string `clientEventId`
    - recent parseable `clientEventTime`
  - `isRecentStoredMonitorEvent` uses `Date.parse(clientEventTime)`.

## Three-End Collaboration

- Student end:
  - Exam center retries are less likely to be stuck by stale local data.
  - Current or recent monitor events still get retry opportunities.

- Teacher end:
  - Receives retryable monitor records without repeated client-side poison queues.

- Admin end:
  - Audit records remain based on server-accepted events.
  - Client cleanup only removes stale local retry payloads; it does not determine violations.

## Invariant

Stored monitor retry must upload only structurally valid, recent events:

```text
stored_monitor_event
AND parseable_client_event_time
AND age <= STORED_MONITOR_EVENT_MAX_AGE_MS
=> eligible for retry upload
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `monitor.ts` defines `STORED_MONITOR_EVENT_MAX_AGE_MS`.
- Stored monitor events are filtered through `isRecentStoredMonitorEvent`.
- Client event time is parsed before retry eligibility.
- Existing stored queue scanning and batch retry behavior remains wired.

## Acceptance Points

- Very old local monitor events do not poison retry forever.
- Malformed local queue entries are ignored.
- Valid recent local monitor events still upload in 100-item batches.
- Backend authorization and late-reporting validation remain authoritative.
