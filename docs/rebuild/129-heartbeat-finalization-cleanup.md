# 129. Heartbeat Finalization Cleanup

## Problem

The exam page already cleared local drafts when heartbeat reported that an
attempt had been submitted, auto-submitted, or otherwise finalized. It did not
clear the local submit token or a pending timeout submit retry timer.

That left a small recovery leak after server-side timeout or forced submit:
browser storage could still hold a stale submit token even though the attempt
was no longer active.

## Change

- Heartbeat finalization now clears:
  - local draft storage;
  - submit token storage;
  - pending timeout submit retry timer.
- The existing forced-submit and timeout messages are preserved.

## Acceptance

- Server-side heartbeat finalization leaves no active local submit token.
- Pending client timeout retry cannot fire after heartbeat confirms completion.
- Quality gates check heartbeat cleanup for draft, token, and retry timer.
