# 219. Submitted Replay Answer Stats

## Goal

Make already-submitted and replayed submit responses include the same answer statistics as a first successful submit. The student page now displays backend-confirmed answer stats, so duplicate submit recovery should not lose those fields.

## Scope

- Add `appendSubmittedAnswerStats` in `ExamService`.
- Populate `questionCount`, `answeredCount`, and `unansweredCount` from `paper_question` plus `answer_record`.
- Apply the stats to cached submit-response replays.
- Apply the stats to fallback already-submitted responses when no cached submit response is available.
- Add quality-gate anchors for the stats helper and both call sites.

## Three-End Coordination

- Student end: refresh/retry submit recovery can still show server-accepted answer counts.
- Teacher end: answer statistics remain based on persisted `answer_record` rows.
- Admin/audit end: replayed responses now reflect current persisted answer records without mutating them.

## Acceptance Notes

- Already-submitted responses include `questionCount`, `answeredCount`, and `unansweredCount`.
- Cached response replays are backfilled with current answer stats.
- The helper only reads answer records; it does not rewrite submission data.
- First-submit finalization still writes the original stats from validation.
