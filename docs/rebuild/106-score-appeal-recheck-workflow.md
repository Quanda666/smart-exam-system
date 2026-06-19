# 106. Score Appeal Recheck Workflow

## Background

Before this batch, a teacher could mark a score appeal as `RECHECK_REQUIRED`, but that decision did not create a real review task. The appeal looked active, yet the grading workflow had no enforced path back into review.

Batch 106 connects score appeals to the review queue.

## Scope

- When an appeal is replied with `RECHECK_REQUIRED`, reopen the related answer record(s) for review.
- Put the attempt back into pending-review status.
- Record a `RECHECK_OPEN` appeal log entry.
- Block closing the recheck until the reopened review tasks are completed.
- Add a teacher-side shortcut from the appeal row to the review drawer.

## Backend Changes

- `ScoreAppealService.replyAppeal`
  - Keeps existing reply and notification behavior.
  - If `handlingResult=RECHECK_REQUIRED`, calls `reopenAppealReviewTasks`.
  - Returns `reopenedReviewCount` to the frontend.
  - Writes `score_appeal_log.action=RECHECK_OPEN`.

- `ScoreAppealService.reopenAppealReviewTasks`
  - Question-level appeal: reopens that exact `answer_record`.
  - Whole-paper appeal: reopens `FILL_BLANK` and `SUBJECTIVE` answers first.
  - If the whole-paper attempt has no fill/subjective answers, falls back to reopening all answers.
  - Sets `answer_record.review_status=0`.
  - Sets `exam_attempt.status=4` when the attempt had already been completed.

- `ScoreAppealService.closeRecheck`
  - Calls `countPendingRecheckAnswers`.
  - Rejects close if the related answer(s) are still pending review.

- `MonitorService`
  - Allows administrator score appeal audit filtering by `RECHECK_OPEN`.
  - Displays `RECHECK_OPEN` as a distinct audit action.

## Frontend Changes

- `ReviewPanel.vue`
  - Shows `复核批阅` for recheck-required appeals.
  - Clicking it opens the normal review drawer for the attempt.
  - After replying with `RECHECK_REQUIRED`, refreshes pending review tasks.
  - Appeal logs display `RECHECK_OPEN`.

- `SystemLog.vue`
  - Adds `RECHECK_OPEN` to score appeal audit action filters and action labels.

- `frontend/src/api/review.ts`
  - Adds `reopenedReviewCount` to `ScoreAppeal`.
  - Adds `RECHECK_OPEN` to score appeal log action typing.

## Three-End Collaboration

- Student end:
  - Student still submits appeals only after scores are released.
  - Student does not directly access review logs or pending review tasks.

- Teacher end:
  - Teacher handles the appeal.
  - Choosing `需要复核` immediately creates a pending review task.
  - Teacher must complete review before closing the recheck.

- Admin end:
  - Admin can see `RECHECK_OPEN` in global appeal audit logs.
  - Admin can correlate the recheck with review score audit records from batches 104 and 105.

## Acceptance Points

- `RECHECK_REQUIRED` reopens at least one answer for review.
- The related attempt returns to pending-review status.
- The pending review list shows the reopened attempt.
- Closing recheck fails while related answers are still pending.
- After review is submitted, closing recheck succeeds.
- Appeal logs include `RECHECK_OPEN`.
