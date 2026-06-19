# 130. Submitted Replay Draft Cleanup

## Problem

Normal finalization deletes server-side drafts. However, submitted-result replay
paths can also be reached after legacy data, interrupted older flows, or recovery
operations. If a finalized attempt still has a DB draft or Redis draft, replaying
the submitted result did not clean that residue.

Residual drafts are confusing for operations and can interfere with later
recovery logic.

## Change

- `submittedAttemptResult` now calls `cleanupFinalizedAttemptDrafts`.
- Cleanup removes both:
  - `exam_answer_draft` rows for the finalized attempt;
  - Redis draft-cache entries for the finalized attempt.
- The response replay behavior remains unchanged and score-redacted.

## Acceptance

- Any submitted-result replay cleans residual server drafts.
- Re-entering or heartbeat-polling a finalized attempt cannot leave stale server
  draft state behind.
- Quality gates check both DB and Redis cleanup calls.
