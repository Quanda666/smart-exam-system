# 255. Exam Approval Transition Lock

## Goal

Make exam approval state transitions deterministic when teachers and administrators operate on the same exam concurrently.

## Scope

- Add a locked exam status loader for approval lifecycle transitions.
- Route admin approve through the locked status check.
- Route admin reject through the locked status check.
- Route teacher update/resubmit through the locked status check before deciding whether to write a `RESUBMIT` approval log.
- Preserve existing rules:
  - only pending exams can be approved;
  - only pending exams can be rejected;
  - rejected exams edited by the creator become pending approval again;
  - editable checks still block started or non-editable exams.
- Extend quality gates so approval, rejection, and resubmission cannot silently return to unlocked status checks.

## Three-End Coordination

- Teacher end: updating a rejected exam cannot race with an administrator's approval or rejection decision.
- Admin end: two administrators approving/rejecting the same exam serialize on the exam row, so the second action sees the updated state.
- Student end: exam publication is driven by one authoritative approval transition, avoiding duplicate or contradictory candidate snapshots and notifications.

## Acceptance Notes

- The lock is scoped to one `exam` row.
- Approval queue list reads remain unlocked.
- This complements score release and review/appeal transition locks from batches 251-254.
