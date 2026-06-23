# 210. Rules Reminder Idempotent Closure

## Goal

If a rules reminder arrives after the student has already confirmed the rules locally, the exam-taking page should close the reminder cleanly instead of leaving it unread or prompting again.

## Scope

- Keep the duplicate prompt guard for `MONITOR_RULES_REMINDER`.
- When local rules confirmation already exists, call `startExam(attemptId, { rulesConfirmed: true })` again as an idempotent server-side audit backfill.
- Mark the reminder notification as read only after the server confirmation path succeeds.
- Keep the notification unread if the server confirmation retry fails, because `rules_confirmed_at` remains the audit source.

## Three-End Coordination

- Student end: avoids repeated confirmation dialogs for the same attempt.
- Teacher end: late or duplicate reminders can still move to read state after the student has already handled the process.
- Admin/audit end: no new state is introduced; closure is still derived from `rules_confirmed_at` and notification read state.

## Acceptance Notes

- A locally confirmed attempt receiving `MONITOR_RULES_REMINDER` calls the start endpoint with `rulesConfirmed = true`.
- Successful server confirmation then marks the reminder notification read.
- Failed server confirmation leaves the notification unread.
- Submitted/auto-submitted responses still use the existing local cleanup path.
