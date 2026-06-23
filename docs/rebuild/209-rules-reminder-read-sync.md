# 209. Rules Reminder Read Sync

## Goal

When a student confirms exam rules from inside the exam-taking page, the related `MONITOR_RULES_REMINDER` notification should also move to read state. Otherwise the teacher monitor desk may show the reminder as unread even though the student already completed the rule confirmation.

## Scope

- Import the existing `markRead` notification API in `ExamTaking`.
- Pass the active `MONITOR_RULES_REMINDER` notification into the in-exam rules confirmation handler.
- After the backend `startExam(attemptId, { rulesConfirmed: true })` call succeeds, mark the reminder notification as read.
- Keep read-state sync non-blocking; if the read update fails, answering and the server-side rule confirmation still continue.

## Three-End Coordination

- Student end: confirms the rule reminder without leaving the exam page.
- Teacher end: monitor session notification status can change from unread to read after the student handles the reminder.
- Admin/audit end: `rules_confirmed_at` remains the authoritative rule-confirmation evidence, while notification read state records student touch/visibility.

## Acceptance Notes

- In-exam `MONITOR_RULES_REMINDER` confirmation calls the existing start endpoint with `rulesConfirmed = true`.
- Successful confirmation then calls `markRead(notificationId)`.
- Failure to mark the notification read does not block answering.
- The reminder remains a process correction, not a cheating verdict.
