# 206. Pending Rules Reminder Tracking

## Goal

After teachers send a `RULES_REMINDER`, the monitor desk should make it obvious which students still have not confirmed the exam rules. This keeps the reminder flow actionable instead of leaving teachers to manually combine columns.

## Scope

- Add a `Pending reminders` metric card to the teacher monitor session board.
- Count sessions where the latest monitor action is `RULES_REMINDER` and `rulesConfirmedAt` is still empty.
- Clicking the metric applies both existing filters:
  - `latestActionType = RULES_REMINDER`
  - `rulesConfirmationStatus = MISSING`
- CSV export keeps using the same filter query built from the active UI filters, so the exported file represents the same pending reminder scope.
- No new database state is introduced; pending reminder status is derived from trusted server fields.

## Three-End Coordination

- Teacher end: quickly finds students who were reminded but still have not completed rule confirmation.
- Student end: continues to rely on `rules_confirmed_at` as the server-side audit source after the student confirms rules.
- Admin/audit end: exported monitor sessions remain based on existing action type and rule confirmation fields, so later review can reproduce the same scope without a separate status column.

## Acceptance Notes

- `Pending reminders` shows only reminder sessions that are still missing rule confirmation.
- Clicking `Pending reminders` narrows the session table to pending reminder students.
- Reset returns both `Rules` and `Latest action` filters to `ALL`.
- Export after applying the metric includes `rules_MISSING` and `action_RULES_REMINDER` in the frontend-generated filename.
- `RULES_REMINDER` remains a process reminder and is not a cheating verdict.
