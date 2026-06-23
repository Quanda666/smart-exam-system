# 207. Rules Reminder Resolution Status

## Goal

Teachers and auditors should not have to mentally combine `latestActionType` and `rulesConfirmedAt` to know whether a rules reminder has been handled. This batch exposes the reminder resolution status directly in the monitor desk and CSV export.

## Scope

- Show a derived rules reminder status inside the latest action cell:
  - `Pending confirmation` when the latest action is `RULES_REMINDER` and `rulesConfirmedAt` is empty.
  - `Confirmed after reminder` when the latest action is `RULES_REMINDER` and `rulesConfirmedAt` is present.
- Add `Rules Reminder Status` to the monitor session CSV export.
- Keep the status derived from existing trusted fields; no new table column or mutable client state is introduced.

## Three-End Coordination

- Teacher end: sees whether a sent rules reminder is still waiting for student action.
- Student end: continues to close the loop only by writing `exam_attempt.rules_confirmed_at` through the server start/confirmation path.
- Admin/audit end: exported monitor sessions now include a direct reminder status for offline review.

## Acceptance Notes

- A `RULES_REMINDER` session without `rulesConfirmedAt` shows `Pending confirmation`.
- A `RULES_REMINDER` session with `rulesConfirmedAt` shows `Confirmed after reminder`.
- Non-`RULES_REMINDER` actions do not display a rules reminder resolution tag.
- CSV export includes `Rules Reminder Status`.
- The status is operational tracking only and is not a cheating verdict.
