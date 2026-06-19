# 216. Server Rules Confirmation Cache Sync

## Goal

Keep the student-side local rules confirmation cache aligned with the backend audit state when APIs already return `rulesConfirmedAt`.

## Scope

- Add `syncRulesConfirmationFromServer` to the shared rules confirmation storage helper.
- Sync the cache after `ExamTaking.vue` receives a `startExam` response.
- Sync the cache for confirmed attempts loaded by `ExamList.vue`.
- Keep sync one-way: server-confirmed writes local cache, but missing server confirmation does not clear local cache.
- Add quality-gate anchors for both sync call sites.

## Three-End Coordination

- Student end: confirmed rules are remembered locally after either exam-list refresh or exam-entry response.
- Teacher end: monitor reminders still rely on backend `rules_confirmed_at`; this change only improves student-side continuity.
- Admin/audit end: server state remains the source of truth and local cache remains a convenience hint.

## Acceptance Notes

- `ExamTaking.vue` calls `syncRulesConfirmationFromServer(props.attemptId, response.data.rulesConfirmedAt)`.
- `ExamList.vue` syncs each loaded exam with `syncRulesConfirmationFromServer(exam.attemptId, exam.rulesConfirmedAt)`.
- Empty or missing `rulesConfirmedAt` values do not erase local confirmation.
- Storage failures remain non-blocking.
