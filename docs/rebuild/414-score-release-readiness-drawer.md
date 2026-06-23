# 414. Score Release Readiness Drawer

## Scope

This batch turns score release readiness from a one-time publish preflight message into a reusable teacher workbench view.

Teachers can now inspect why a score release is blocked before pressing publish, and each actionable blocker links to the queue that can resolve it.

## Frontend Changes

- `ExamManagement` adds a `Readiness` action for every exam row.
- The action calls the existing `GET /api/exams/{id}/scores/readiness` endpoint.
- A new `Score Release Readiness` drawer shows:
  - total attempts
  - completed attempts
  - scored attempts
  - pending review attempts
  - pending answer review count
  - active attempts
  - pending score appeals
  - open recheck appeals
  - completed attempts missing scores
- The drawer renders backend `blockerDetails` when present, and falls back to local blocker text/counts if only `scoreReleaseBlockers` is available.
- Pressing publish on a blocked exam now opens this drawer instead of showing only a text alert.

## Blocker Actions

- `PENDING_REVIEW` and `PENDING_REVIEW_ANSWERS`
  - opens `/reviews?reviewExamId=<examId>`
- `PENDING_APPEALS`
  - opens `/reviews?appealExamId=<examId>&appealStatus=0`
- `OPEN_RECHECK`
  - opens `/reviews?reviewExamId=<examId>&reviewTaskType=RECHECK&appealExamId=<examId>&appealStatus=1&appealHandlingResult=RECHECK_REQUIRED`

Other blockers remain informational because they need time to pass, active attempts to finish, or operational investigation rather than a review-queue action.

## Coordination Impact

- Teacher side: teachers can inspect readiness at any time and jump to the exact blocker queue.
- Student side: unchanged; scores still remain invisible until readiness passes and the teacher publishes.
- Admin side: unchanged audit behavior; readiness is not an audit event, while actual score publish/revoke remains logged.

## Acceptance Checks

- The exam row exposes a `Readiness` action.
- Readiness opens a drawer backed by `/api/exams/{id}/scores/readiness`.
- Blocked publish attempts open the same drawer.
- Actionable blocker rows expose a `Resolve` action.
- Resolve routing stays consistent with the score release blocker workbench links introduced in step 413.
