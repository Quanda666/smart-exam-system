# 208. In-Exam Rules Reminder Confirmation

## Goal

Rules reminders should reach students even when they are already inside the exam-taking page. Before this batch, the in-exam monitor notification polling only handled warning and force-submit notices, so `MONITOR_RULES_REMINDER` could be missed until the student returned to the exam list or opened notifications.

## Scope

- Include `MONITOR_RULES_REMINDER` in the exam-taking page monitor notification polling.
- Keep the notification query scoped to the current `EXAM_ATTEMPT`.
- Show an in-exam reminder message when a rules reminder arrives.
- Prompt the student to confirm the exam rules.
- On confirmation, reuse `startExam(attemptId, { rulesConfirmed: true })` so the backend writes `exam_attempt.rules_confirmed_at`.
- Store the local confirmation hint only after the server confirmation path succeeds.

## Three-End Coordination

- Teacher end: a rules reminder sent from the monitor desk can reach an active student without waiting for navigation.
- Student end: the confirmation happens in the exam page while preserving draft, heartbeat, monitor upload, and answer state.
- Admin/audit end: `rules_confirmed_at` remains the authoritative audit source; the frontend does not invent a separate status.

## Acceptance Notes

- `MONITOR_RULES_REMINDER` is recognized by `ExamTaking`.
- The notification is still filtered by `relatedType = EXAM_ATTEMPT` and current `attemptId`.
- Confirming rules from the exam page calls the existing start endpoint with `rulesConfirmed = true`.
- Submitted/auto-submitted responses from that endpoint still trigger the normal local cleanup path.
- The reminder is a process correction, not a cheating verdict.
