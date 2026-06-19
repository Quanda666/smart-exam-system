# 131. Submitted Replay Monitor Session

## Problem

Batch 130 cleaned residual drafts when a submitted attempt was replayed. A related
runtime state could still remain stale: monitor sessions from older or interrupted
flows might still show `ONLINE` or `OFFLINE` even though the attempt was already
submitted, pending review, or finalized.

That can mislead teacher and administrator monitoring views.

## Change

- Submitted-result replay now runs `cleanupFinalizedAttemptState`.
- The cleanup still removes DB and Redis drafts.
- It also calls `markMonitorSessionSubmitted` so the monitor session is repaired
  to `SUBMITTED`.

## Acceptance

- Replaying a submitted attempt repairs stale monitor session state.
- Submitted replay remains score-redacted.
- Draft cleanup from batch 130 is preserved.
- Quality gates check DB draft cleanup, Redis draft cleanup, and monitor session
  submission marking.
