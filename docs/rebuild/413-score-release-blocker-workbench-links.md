# 413. Score Release Blocker Workbench Links

## Scope

This batch closes the navigation gap between score release blockers, score appeals, and recheck review tasks.

Before this step, teachers could see that scores were blocked by pending review, pending appeals, or open recheck work, but they still had to manually find the related exam and task type in the review workbench.

## Backend Changes

- `GET /api/reviews/appeals` now accepts optional `examId`.
- `ScoreAppealService.listAppeals(...)` keeps the old four-argument overload for compatibility and adds a five-argument overload with `examId`.
- The exam-scoped appeal filter still applies teaching-scope rules:
  - global admins can view all matching appeals
  - teachers only see appeals for exams they own or students inside their teaching scope
- Invalid `examId` values are rejected before SQL execution.

## Frontend Changes

- `ReviewPanel` supports `appealExamId` as a route query filter for score appeals.
- The appeal toolbar shows the active appeal exam filter and can clear it independently.
- Recheck appeal rows now use `openRecheckReview(...)`:
  - sets `appealId`
  - sets `reviewExamId`
  - sets `reviewTaskType=RECHECK`
  - opens the matching attempt review drawer
- When a teacher replies to an appeal with `RECHECK_REQUIRED`, the workbench automatically focuses the recheck queue for that exam.
- `ExamManagement` now shows a `Resolve` action for score-release blockers caused by:
  - pending review
  - pending answer review
  - pending score appeals
  - open recheck appeals

## Route Contract

- Standard/pending review blocker:
  - `/reviews?reviewExamId=<examId>`
- Pending appeal blocker:
  - `/reviews?appealExamId=<examId>&appealStatus=0`
- Open recheck blocker:
  - `/reviews?reviewExamId=<examId>&reviewTaskType=RECHECK&appealExamId=<examId>&appealStatus=1&appealHandlingResult=RECHECK_REQUIRED`

## Three-Side Coordination

- Teacher side: release blockers now lead directly to the work queue that can remove the blocker.
- Student side: no score visibility rule changes; scores remain hidden until release readiness passes and the teacher publishes.
- Admin side: unchanged global audit behavior; score appeal and review score audit links continue to resolve through `/monitor/logs`.

## Acceptance Checks

- A blocked score release row exposes `Resolve` when the blocker is review/appeal/recheck related.
- `Resolve` routes to a scoped review or appeal queue.
- A `RECHECK_REQUIRED` appeal opens the exact attempt review drawer and leaves the page filtered to recheck tasks.
- Appeal exam filtering does not replace `reviewExamId`; the two filters remain independent.
- Existing exact `appealId` deep links still focus a single appeal row.
