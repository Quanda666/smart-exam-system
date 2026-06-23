# 434 Lifecycle Review Fast Path

## Scope
- Extend the Lifecycle Health drawer with fast paths for review, recheck, and score appeal handling.
- Keep all state-changing work inside existing guarded review, appeal, and score release APIs.
- Improve ReviewPanel deep-link feedback so operators can see and clear the active queue filters.

## Frontend Changes
- `ExamManagement.vue`
  - Added drawer toolbar actions:
    - `Review Queue`
    - `Recheck Queue`
    - `Appeals`
  - Added row-level actions when lifecycle evidence says an exam has:
    - pending standard review work
    - open recheck-required appeal work
    - pending score appeals
  - Reused `/reviews` deep links:
    - standard review: `reviewTaskType=STANDARD`
    - recheck review: `reviewTaskType=RECHECK&appealStatus=1&appealHandlingResult=RECHECK_REQUIRED`
    - pending appeals: `appealStatus=0`
  - Hardened exam id resolution for lifecycle rows that may carry either `id` or `examId`.

- `ReviewPanel.vue`
  - Added an active filter banner for deep-linked queues.
  - Added one-click clearing for lifecycle review filters.
  - Highlighted focused review rows in the progress and pending review tables.

## Safety Notes
- No student answer content, correct answers, analysis, or unreleased score data is added to lifecycle health.
- No new write endpoint is introduced.
- Review, recheck, and appeal handling still use the existing backend authorization and lifecycle checks.

## Verification
- Run fast quality gate.
- Run frontend build.
- Run full quality gate at the batch milestone.
