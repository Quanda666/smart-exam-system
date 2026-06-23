# 212. In-Exam Rules Reminder Title

## Goal

Make the student in-exam rules reminder visually explicit when it is shown in the exam-taking page. A `MONITOR_RULES_REMINDER` should not share the generic monitor notice title used by ordinary warnings.

## Scope

- Add a stable `rulesReminderNoticeTitle` for in-exam rules reminders.
- Use the same title for the top in-exam monitor notice and the confirmation dialog.
- Keep existing warning and force-submit monitor notices unchanged.
- Add a local quality-gate anchor so this branch cannot silently regress.

## Three-End Coordination

- Student end: rules reminders are clearly labeled as a rules confirmation requirement while answering.
- Teacher end: reminders sent from the monitor panel produce a more specific student-facing notice.
- Admin/audit end: this remains presentation-only; audit truth is still `rules_confirmed_at` plus notification read state.

## Acceptance Notes

- `MONITOR_RULES_REMINDER` renders `Rules confirmation required` in the in-exam notice title.
- The confirmation modal uses the same shared title.
- The change does not alter force-submit handling, notification read sync, or answer drafting.
