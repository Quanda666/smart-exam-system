# 410 Review Progress Workbench

## Scope
- Add an exam-level review progress view for teachers and administrators.
- Connect pending review work to the score release gate introduced in the previous batch.
- Keep progress counts scoped to the same expected question set used by review submission.

## Backend
- `GET /api/reviews/progress` returns scoped review progress grouped by exam.
- Optional `examId` filtering is validated before database access.
- Each row includes:
  - attempt counts: total, pending review, completed, active
  - answer counts: pending, reviewed, total reviewable
  - `progressPercent`
  - `firstPendingAttemptId`
  - `reviewState`
  - `blocksScoreRelease`
- The query uses the existing snapshot-first expected answer condition so stale or non-paper answer records do not distort progress.

## Teacher UI
- `ReviewPanel` now shows a compact "Review progress" table above the pending review queue.
- Teachers can see which exam blocks score release and jump to the first pending attempt.
- The progress view refreshes after review submission and after recheck-related appeal transitions.

## Three-End Coordination
- Teacher review progress explains why score release readiness may return `PENDING_REVIEW` or `PENDING_REVIEW_ANSWERS`.
- Student score visibility remains unaffected until scores are published.
- Administrators use the same scoped backend endpoint when they enter the review workbench.

## Verification
- `scripts/run-quality-gates.ps1` checks the progress endpoint, service query fields, frontend API, UI workbench, and refresh-after-review flow.
