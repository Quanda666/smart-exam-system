# 125. Timeout Submit Retry

## Problem

When the client timer reached zero, `submit(true)` stopped the active timers before
sending the final answers. If the submit request failed because of a temporary
network problem, the normal manual-submit recovery path did not restart because
`timeLeft <= 0`. The page could remain open with a failed submit message and no
client-side retry loop.

In a real exam, timeout submission must be eventually retried from the client
while the server heartbeat path remains able to finalize the attempt.

## Change

- Added an in-flight submit guard so duplicate submit calls cannot race each
  other from rapid clicks, timer expiry, or retries.
- Added a timeout submit retry timer.
- If auto-submit fails, or the deadline has already passed, the exam page now:
  - schedules another auto-submit attempt after a short delay;
  - restarts heartbeat so the server can still report forced or timeout
    finalization;
  - restarts monitor flushing so pending proctoring events keep moving.
- Retry timers are cleared with the other exam timers during cleanup.

## Acceptance

- Manual submit failures before the deadline still return the student to the
  active answering flow.
- Timeout submit failures no longer leave the page with no retry path.
- Duplicate in-flight submit calls are ignored.
- Quality gates check the retry timer, retry branch, cleanup, and in-flight
  guard.
