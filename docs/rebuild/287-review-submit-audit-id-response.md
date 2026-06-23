# Step 287 - Review Submit Audit ID Response

## Background

Review score audit rows can now be searched by exact log ID and shared through deep links. The remaining gap was the teacher grading action itself: after submitting a batch of manual review scores, the teacher only saw a generic success message and had no immediate evidence handle for the audit rows created by that submission.

## Changes

- `ReviewService.submitReview` now collects generated `review_score_log.id` values while recording manual grading score changes.
- The submit response includes `reviewScoreLogIds`.
- The frontend review API type now exposes `reviewScoreLogIds?: number[]`.
- `ReviewPanel` success feedback includes the first generated audit IDs for the submitted grading batch.
- The teacher Review Score Logs drawer now shows a copyable `#<id>` column.
- Teachers can copy the raw audit ID or a deep link to `/monitor/logs?reviewScoreLogId=<id>`.
- `scripts/run-quality-gates.ps1` now guards the backend response, frontend API type, and ReviewPanel copy controls.

## Acceptance

- Submitting a complete review batch returns generated review score audit log IDs.
- The teacher receives immediate audit evidence in the success message.
- Existing local review score logs expose copyable IDs and global audit deep links.
- Quality gates fail if the response field or teacher-side copy controls are removed.
