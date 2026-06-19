# 251. Score Appeal Submit Serialization

## Goal

Make score appeal creation safe under repeated clicks, retries, and concurrent requests from the same student attempt.

## Scope

- Keep the existing business rule: an active whole-paper appeal blocks all active question appeals for the same attempt, and an active question appeal blocks another active appeal for that same question.
- Lock the released, finalized `exam_attempt` row during appeal submission with `FOR UPDATE`.
- Run the active-duplicate check and insert inside the same transaction after the lock is acquired.
- Extend quality gates so the row-lock guard is preserved.

## Three-End Coordination

- Student end: duplicate submits or retry storms cannot create overlapping active appeals for the same answer sheet.
- Teacher end: the review panel receives one authoritative pending/recheck appeal chain instead of duplicate work items.
- Admin/audit end: appeal logs remain readable because one student action produces one active appeal target at a time.

## Acceptance Notes

- This change does not alter score visibility rules or the appeal window policy.
- The row lock is scoped to the student's finalized attempt, not to the whole exam, so unrelated students can still submit appeals concurrently.
- The database index remains a lookup accelerator; the cross-target overlap rule is enforced by transactional serialization plus the existing duplicate query.
