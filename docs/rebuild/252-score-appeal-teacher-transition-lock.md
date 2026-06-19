# 252. Score Appeal Teacher Transition Lock

## Goal

Make teacher-side score appeal handling deterministic when multiple teachers or administrators operate on the same appeal at nearly the same time.

## Scope

- Route `replyAppeal` through a locked appeal read.
- Route `closeRecheck` through the same locked appeal read.
- Keep the existing teaching-scope permission check after the row is loaded.
- Preserve existing status rules:
  - only pending appeals can be replied to;
  - only replied `RECHECK_REQUIRED` appeals can be closed;
  - recheck tasks must be completed and the attempt must be finalized before close.
- Extend quality gates so both teacher status transitions must use the locked loading path.

## Three-End Coordination

- Student end: one appeal receives one authoritative handling timeline instead of conflicting replies or duplicated close notifications.
- Teacher end: concurrent operations on the same appeal serialize at the database row, so the second operator sees the updated status rule instead of creating parallel logs.
- Admin/audit end: `score_appeal_log` stays a readable state-transition history with a single active transition at a time.

## Acceptance Notes

- The lock is scoped to the single `score_appeal` row.
- Listing appeals and reading logs remain unlocked read operations.
- This builds on batch 251, which serialized student-side appeal submission by attempt row.
