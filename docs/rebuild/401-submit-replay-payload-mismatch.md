# 401 Submit Replay Payload Mismatch

## Scope
- Harden submit retry and replay semantics.
- Keep submit idempotency while making conflicting retry payloads visible to clients and audits.

## Backend
- `ExamService.submitExam` now computes the request answer payload hash before checking whether the attempt is already finalized.
- Submitted-attempt replay now receives the request payload hash.
- Replayed or fallback submitted responses add `submitPayloadMismatch: true` when the request payload hash differs from the stored `submitPayloadHash`.
- Existing safeguards remain:
  - submit responses still remove raw score
  - score visibility remains `PENDING_RELEASE`
  - the first finalized attempt remains the source of truth

## Frontend
- `submitExam` response typing now includes `submitPayloadMismatch?: boolean`.

## Why This Matters
- Network retries should safely replay the original submit result.
- A duplicate submit with the same attempt but different answers should not silently look identical to a clean retry.
- Teachers/admins can use this flag later for incident diagnosis without treating it as automatic cheating.

## Verification
- `scripts/run-quality-gates.ps1` checks request payload hash propagation, mismatch flagging, and frontend API typing.
