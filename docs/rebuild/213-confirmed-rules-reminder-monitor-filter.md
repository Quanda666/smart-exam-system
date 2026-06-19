# 213. Confirmed Rules Reminder Monitor Filter

## Goal

Give teachers a quick way to review rules reminders that have already been closed by student confirmation. Pending reminders were visible, but confirmed reminders still required combining filters manually.

## Scope

- Add a `Confirmed reminders` metric button to the teacher exam monitor panel.
- Count sessions where the latest action is `RULES_REMINDER` and `rulesConfirmedAt` is present.
- Clicking the metric applies `latestActionType = RULES_REMINDER` and `rulesConfirmationStatus = CONFIRMED`.
- Keep the existing pending reminder metric and row-level resolution tag unchanged.
- Add a quality-gate anchor for the confirmed reminder quick filter.

## Three-End Coordination

- Teacher end: teachers can quickly separate unresolved reminders from reminders that students already confirmed in the exam page.
- Student end: no new interaction is added; confirmation continues to write `rules_confirmed_at`.
- Admin/audit end: the same status remains available through monitor session export and notification audit records.

## Acceptance Notes

- `Confirmed reminders` appears beside the existing rules reminder metrics.
- The button is disabled when no matching sessions exist.
- The active state reflects the combined `RULES_REMINDER + CONFIRMED` filters.
- Export behavior remains compatible because the existing query already supports both filter fields.
