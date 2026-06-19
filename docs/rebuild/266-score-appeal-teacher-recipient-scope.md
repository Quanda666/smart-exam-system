# 266. Score Appeal Teacher Recipient Scope

## Background

Many real courses are handled by more than one teacher. If score appeal notifications only go to the exam creator, assistants or co-teachers who can legally handle the student's review work may miss the task. The teacher-side appeal workbench already uses teaching scope, so notifications should follow the same collaboration model.

## Change

- Keep the exam creator as a score-appeal notification recipient.
- Add teachers assigned to the appealing student's active class courses when the course subject matches the exam paper subject.
- Deduplicate recipients with a stable ordered set.
- Remove the submitting student from recipients as a safety guard.
- Keep notification metadata stable:
  - `type = SCORE_APPEAL`
  - `related_type = SCORE_APPEAL`
  - `related_id = appealId`
  - link `/reviews?appealStatus=0&appealHandlingResult=ALL`

## Three-End Impact

- Student end: an appeal is more likely to reach the teacher group that can actually process it.
- Teacher end: co-teachers and assistants with matching teaching assignments receive the same pending-work signal.
- Admin end: all emitted notifications remain auditable through the existing notification relation fields.

## Acceptance Criteria

- Submitting an eligible appeal notifies the exam creator when present.
- Matching assigned teachers for the student's course subject also receive notifications.
- A teacher receives at most one notification for the same appeal submission.
- The submitting student is never added as a teacher recipient.
- Quality gates fail if the recipient scope query or relation metadata is removed.

## Follow-Up

The next iteration should add notification idempotency for appeal submission retries so rare transport retries cannot create duplicate teacher notification rows for the same `SCORE_APPEAL` relation.
