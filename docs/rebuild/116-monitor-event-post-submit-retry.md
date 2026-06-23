# 116. Monitor Event Post Submit Retry

## Background

Batch 114 kept unflushed monitor events in local storage when answer submission succeeded. Batch 115 allowed the backend to accept bounded late monitor events after an attempt is submitted. One gap remained: the taking page emitted `submit-success` immediately after answer submission, so preserved events might not get another upload attempt before the page leaves.

This batch connects the frontend preservation behavior to the backend late-reporting window.

## Scope

- Keep answer submission non-blocking.
- Retry monitor event upload once more after answer submit succeeds.
- Clear the local monitor queue only if the pre-submit or post-submit upload confirms the queue is empty.
- Preserve remaining monitor events when both upload attempts fail.

## Frontend Contract

- `ExamTaking.submit`
  - Calls `flushMonitorEvents(true)` before answer submission.
  - Submits answers even when the pre-submit monitor upload fails.
  - Calls `retryLateMonitorEventsAfterSubmit` after answer submission succeeds when monitor events remain.
  - Clears monitor storage only when `lateMonitorFlushed` is true.

- `retryLateMonitorEventsAfterSubmit`
  - Returns true if the queue is already empty.
  - Otherwise calls `flushMonitorEvents(true)` so the backend late reporting window can accept events generated during the attempt.

## Three-End Collaboration

- Student end:
  - Does not get blocked from submitting by monitor upload instability.
  - Gets one immediate post-submit retry before leaving the taking screen.

- Teacher end:
  - Has a better chance of seeing final-window monitor events such as offline/online recovery or submit-time heartbeat failure.

- Admin end:
  - Audit trails lose fewer submit-time monitor records while still treating events as risk records, not automatic violation verdicts.

## Invariant

Answer submission success must trigger a post-submit monitor upload retry when monitor events remain:

```text
answer_submit_success
AND pre_submit_monitor_flush_failed
AND local_monitor_queue_not_empty
=> retry late monitor upload before leaving the taking page
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `ExamTaking.submit` stores the pre-submit flush result.
- The post-submit path calls `retryLateMonitorEventsAfterSubmit`.
- Clearing monitor storage depends on `lateMonitorFlushed`.
- Flush failure paths still preserve the local queue.

## Acceptance Points

- Normal submit succeeds even if monitor upload fails.
- If pre-submit upload fails but post-submit retry succeeds, local monitor storage is cleared.
- If both uploads fail, local monitor storage remains.
- The frontend now uses the backend bounded late-reporting support added in batch 115.
