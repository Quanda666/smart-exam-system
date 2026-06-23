# 268. Notification Dedup Key

## Background

The previous step added application-level idempotency for score-appeal teacher notifications. That prevents ordinary retries from creating duplicate rows, but two concurrent requests can still both pass the read-before-write check. Related workflow notifications need a database-backed idempotency key when the business object requires exactly one pending notification per recipient.

## Change

- Add nullable `notification.dedup_key`.
- Add `uk_notification_dedup_key (dedup_key)`.
- Keep ordinary notifications writing `NULL`, so repeat reminders and broadcasts are unaffected.
- Build a stable dedup key for idempotent related sends:
  - `userId:type:relatedType:relatedId`
- Change `sendOnceAndReturnId` to use atomic insert/upsert:
  - insert a new notification when no dedup key exists.
  - return the existing notification id via `LAST_INSERT_ID(id)` when the dedup key already exists.

## Three-End Impact

- Student end: no behavior change.
- Teacher end: the same score appeal cannot create duplicate pending notification rows for the same teacher, even under retry races.
- Admin end: notification audit remains cleaner while still preserving repeated rows for notification types that intentionally send multiple reminders.

## Acceptance Criteria

- Fresh schema contains nullable `dedup_key` and `uk_notification_dedup_key`.
- Startup migration adds both the column and unique key for old databases.
- `sendOnceAndReturnId` uses `ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)`.
- Ordinary `sendAndReturnId` and `sendBatch` continue to allow repeated notification rows.
- Quality gates fail if the dedup key column, unique key, or atomic upsert behavior is removed.

## Follow-Up

Later modules can adopt `sendOnceAndReturnId` one by one when they have a true one-notification-per-recipient business invariant.
