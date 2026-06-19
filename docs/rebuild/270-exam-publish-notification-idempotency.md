# 270. Exam Publish Notification Idempotency

## Background

Exam publication notifications are tied to a student's first `exam_attempt`. After adding `notification.dedup_key`, this workflow can use the same idempotent related-notification path as score appeals. This prevents duplicate "new exam" messages for the same student attempt when publish routines are retried.

## Change

- Keep the per-student attempt lookup introduced in step 269.
- Send new exam notifications with `sendOnceAndReturnId`.
- Preserve the business relation:
  - `type = EXAM`
  - `related_type = EXAM_ATTEMPT`
  - `related_id = attemptId`
  - link `/student/exams?attemptId=<attemptId>`
- Leave broadcast notices and other repeatable notifications on the ordinary send path.

## Three-End Impact

- Student end: the same published attempt appears as one new-exam notification instead of duplicate rows.
- Teacher end: retrying a publish operation cannot spam candidates for the same first attempt.
- Admin end: notification audit has one stable row per `EXAM_ATTEMPT` publication notification.

## Acceptance Criteria

- New exam publication notifications are still sent after attempts are created.
- The notification remains related to the concrete `EXAM_ATTEMPT`.
- The send path uses `sendOnceAndReturnId`.
- Quality gates fail if the publish notification returns to unscoped batch sends or non-idempotent sends.

## Follow-Up

Future publish logs should record the number of candidate attempts and notification rows reused or created.
