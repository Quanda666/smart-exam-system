# 211. Existing Unread Rules Reminder Recovery

## Goal

When a student enters or refreshes the exam-taking page, an existing unread `MONITOR_RULES_REMINDER` for the same attempt should still be handled. The page should not silently baseline the notification ID and wait only for future reminders.

## Scope

- During exam-taking notification initialization, load current-attempt monitor notifications.
- Keep the existing latest notification ID baseline for normal monitor messages.
- Specifically recover the latest unread `MONITOR_RULES_REMINDER`.
- Reuse the same confirmation flow used for newly arrived rules reminders.
- Keep ordinary old warnings and force-submit notifications from being replayed during initialization.

## Three-End Coordination

- Student end: refreshing or entering the exam page still surfaces an unread rules reminder.
- Teacher end: a reminder sent shortly before the student loads the exam page can still be confirmed and marked read.
- Admin/audit end: the closure remains anchored to `rules_confirmed_at` and notification read state.

## Acceptance Notes

- Initialization filters notifications by the current `EXAM_ATTEMPT`.
- Existing unread `MONITOR_RULES_REMINDER` is handled once during initialization.
- Existing read rules reminders are ignored.
- Normal notification polling still handles future monitor notifications by ID.
