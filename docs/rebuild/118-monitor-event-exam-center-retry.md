# 118. Monitor Event Exam Center Retry

## Background

Batches 114-117 made the taking page preserve, retry, and force-drain monitor event queues. A final client-side gap remained: if all upload attempts failed and the student left the taking page, preserved monitor events stayed in `localStorage` with no follow-up retry path.

This batch adds an opportunistic retry from the student exam center.

## Scope

- Scan locally stored monitor queues outside the taking page.
- Retry upload when the student opens or refreshes the exam center.
- Keep the retry non-blocking for normal exam list loading.
- Keep the monitor queue format centralized in `frontend/src/api/monitor.ts`.

## Frontend Contract

- `flushStoredMonitorQueues`
  - Scans local storage keys with the `smart_exam_monitor_queue_` prefix.
  - Parses and validates stored monitor events.
  - Sends events in 100-item batches using `recordCheatEventsBatch`.
  - Removes a queue only after every batch is accepted.
  - Writes back remaining events if a later batch fails.
  - Returns a small summary for future UI or diagnostics.

- `ExamList`
  - Calls `flushStoredMonitorQueues` before loading the student exam list.
  - Catches retry errors so pending monitor uploads never block the exam center.

## Three-End Collaboration

- Student end:
  - Preserved monitor events get another upload opportunity after leaving the taking screen.
  - The exam center remains usable even if retry upload fails.

- Teacher end:
  - Receives more complete monitoring records after transient submit-time failures.

- Admin end:
  - Audit coverage improves without turning monitor events into automatic violation decisions.

## Invariant

Opening or refreshing the student exam center should attempt to upload locally preserved monitor queues:

```text
student_exam_center_load
AND localStorage contains smart_exam_monitor_queue_*
=> try batched monitor upload before list refresh completes
```

Failures must preserve remaining events and must not block exam list loading.

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `monitor.ts` exposes `flushStoredMonitorQueues`.
- The helper scans `smart_exam_monitor_queue_` keys.
- Stored events are uploaded in 100-item batches.
- Remaining events are written back on failure.
- `ExamList` calls the retry helper through a non-blocking wrapper.

## Acceptance Points

- Taking-page preserved monitor queues are retried from the exam center.
- Fully uploaded queues are removed from local storage.
- Partially uploaded queues keep remaining events.
- Exam center loading still succeeds if monitor retry fails.
