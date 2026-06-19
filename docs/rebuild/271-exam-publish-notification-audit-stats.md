# 271. Exam Publish Notification Audit Stats

## Background

Exam publication creates candidate snapshots, attempt records, and student notifications in one workflow. Before this step, the approval log only recorded the publish action, not how many candidates or attempt-linked notifications were involved. This made administrator audit weaker than the score-release audit path.

## Change

- Add publish notification counters to `exam_approval_log`:
  - `candidate_count`
  - `notified_student_count`
  - `notified_attempt_count`
- Backfill old databases with zero-default columns during startup migration.
- Return these fields from approval log queries.
- Record counts for direct admin publish and approval publish actions.
- Keep non-publish approval actions at zero counts.

## Three-End Impact

- Admin end: approval audit shows the scale of each publish action and whether attempt-linked notifications were emitted.
- Teacher end: approval history becomes clearer when an exam is published by an administrator.
- Student end: no behavior change; publication notifications remain idempotent and linked to `EXAM_ATTEMPT`.

## Acceptance Criteria

- Fresh schema and startup migration both include the three counter columns.
- Direct publish and approval publish logs persist candidate and notification counts.
- `listApprovalLogs` exposes the counters.
- Quality gates fail if the counters, migration, or service wiring are removed.

## Follow-Up

Later iterations can split notification counters into created versus reused rows once notification audit needs that distinction.
