# 121. Monitor Event Batch Failure Isolation

## Background

Stored monitor queues are uploaded in batches of 100. If one event in a stored batch is permanently rejected by backend validation, the whole batch request returns an error. Before this batch, that meant valid events in the same queue could be retried forever behind one invalid or no-longer-reportable event.

This batch adds conservative failure isolation for stored monitor retries.

## Scope

- Keep normal batch upload as the default path.
- Use single-event isolation only after a 400 validation-style batch rejection.
- Drop permanently rejected single events.
- Preserve the queue on authentication, permission, network, or server failures.

## Frontend Contract

- `flushStoredMonitorQueues`
  - Attempts normal 100-event batch uploads first.
  - If a batch fails with a permanent stored monitor upload reject, calls `isolateStoredMonitorQueueFailures`.
  - Writes back remaining events after isolation.

- `isolateStoredMonitorQueueFailures`
  - Retries events one by one.
  - Removes successfully uploaded events.
  - Drops single events that are rejected with HTTP 400.
  - Stops and preserves remaining events on non-400 failures.

- `isPermanentStoredMonitorUploadReject`
  - Treats only `ApiError` with `status === 400` as permanent local retry rejection.

## Three-End Collaboration

- Student end:
  - A stale or invalid local monitor event no longer blocks valid stored events forever.
  - Temporary backend or network errors still preserve the queue.

- Teacher end:
  - Receives more valid late monitor records even when a nearby event becomes no-longer-reportable.

- Admin end:
  - Audit ingestion remains server-authoritative.
  - Client isolation improves delivery of valid records without inventing violation decisions.

## Invariant

Batch retry isolation is only allowed for permanent validation failures:

```text
batch_upload_failed
AND error.status = 400
=> retry events one by one

batch_upload_failed
AND error.status != 400
=> preserve queue unchanged
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `monitor.ts` imports and uses `ApiError`.
- Stored monitor batch failures can call `isolateStoredMonitorQueueFailures`.
- Permanent retry rejection is limited to `error.status === 400`.
- Isolation retries single events with `recordCheatEventsBatch([remaining[index]])`.

## Acceptance Points

- One invalid stored event does not block valid events in the same batch.
- Valid isolated events are removed after upload.
- Permanently invalid isolated events are dropped.
- Network, auth, permission, and server failures keep remaining events for later retry.
