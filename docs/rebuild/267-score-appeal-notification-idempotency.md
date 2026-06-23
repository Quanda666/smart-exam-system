# 267. Score Appeal Notification Idempotency

## Background

Teacher notifications are part of the score-appeal workflow. If an appeal submission path is retried or the same appeal notification routine is triggered twice, teachers should not receive duplicate pending-work rows for the same appeal. The notification table already has `related_type` and `related_id`, so related notifications can be made idempotent without changing unrelated notification scenarios.

## Change

- Add `NotificationService.sendOnceAndReturnId`.
- The method checks an existing notification by:
  - `user_id`
  - `type`
  - `related_type`
  - `related_id`
- If a matching notification exists, return its id.
- If no matching notification exists, insert a new row through the shared generated-id insert helper.
- Score-appeal teacher notifications now use the idempotent method.

## Three-End Impact

- Student end: appeal submission behavior remains unchanged.
- Teacher end: the same appeal does not create repeated pending notification rows for the same teacher.
- Admin end: notification audit remains keyed by `SCORE_APPEAL + appealId`, with fewer duplicate audit rows.

## Acceptance Criteria

- Existing generated notification id behavior is preserved for monitor action audit links.
- Score-appeal teacher notifications use idempotent related sends.
- Idempotency only applies when `related_type` and `related_id` are present.
- Quality gates fail if the idempotent send method, related lookup, or score-appeal usage is removed.

## Follow-Up

If duplicate related notifications become a cross-module issue, add a guarded database-level unique index for non-null related notifications after deduplicating historical rows.
