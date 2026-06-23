# 117. Monitor Event Force Drain

## Background

The monitor batch API accepts at most 100 events per request. The student client keeps up to 200 local monitor events. After batches 114-116, submit and leave flows preserved and retried monitor events, but `flushMonitorEvents(true)` still uploaded only one batch. If the local queue contained more than 100 events, the page could leave with remaining events still stored locally.

This batch makes forced monitor flushes drain all local batches while keeping normal periodic flushes lightweight.

## Scope

- Keep backend batch size at 100 events.
- Keep normal automatic monitor flushes to one batch per call.
- Drain all queued batches when `force = true`.
- Preserve remaining events if any forced batch fails.

## Frontend Contract

- `ExamTaking.flushMonitorEvents(false)`
  - Sends at most one batch.
  - Still skips upload when the browser is offline.

- `ExamTaking.flushMonitorEvents(true)`
  - Sends batches of `monitorEventQueue.slice(0, 100)`.
  - Repeats while `force && monitorEventQueue.length > 0`.
  - Persists the queue after each accepted batch.
  - Returns true only when the local queue is empty.
  - Returns false and preserves remaining events on failure.

## Three-End Collaboration

- Student end:
  - Submit and leave flows make a stronger best effort to upload all local monitor events.
  - Answer submission remains non-blocking.

- Teacher end:
  - Receives more complete final-window monitor risk records, especially after offline bursts or repeated focus events.

- Admin end:
  - Audit exports become less likely to miss locally queued monitor records due to the 100-event batch limit.

## Invariant

Forced monitor flushes must respect the backend batch limit and keep sending until no local events remain or a batch fails:

```text
force = true
AND local_monitor_queue > 100
=> send multiple 100-event batches until queue is empty or upload fails
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- Forced monitor upload uses a loop.
- Each batch uses `monitorEventQueue.slice(0, 100)`.
- The loop condition depends on `force && monitorEventQueue.length > 0`.
- Unflushed events are still preserved on failure.

## Acceptance Points

- Periodic monitor upload remains lightweight.
- Submit/leave forced upload can drain up to the full local queue.
- A failed later batch leaves remaining events in local storage.
- The frontend stays aligned with the backend 100-event batch contract.
