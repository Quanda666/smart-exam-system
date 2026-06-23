# 228. Submit Replay Answer Stats Snapshot Count

## Goal

Align submitted-attempt replay summaries with the published exam snapshot. A repeated submit request or an already-submitted response should report the same answer-completeness totals as monitor, review, released results, and score exports.

## Scope

- Update `appendSubmittedAnswerStats` to build the expected question set from `exam_question_snapshot` first.
- Keep `paper_question` as a compatibility fallback only when an older exam has no question snapshot.
- Count `answeredCount` only against the expected question set, so stray or stale `answer_record` rows cannot inflate the submit summary.
- Keep the response fields unchanged: `questionCount`, `answeredCount`, and `unansweredCount`.
- Add quality-gate anchors for snapshot-first submitted/replayed answer statistics.

## Three-End Coordination

- Student end: submit and replay messages now use the frozen exam question set for completion summaries.
- Teacher end: monitor and review statistics stay aligned with student submit summaries after paper edits.
- Admin/audit end: exported score/history statistics and submitted-attempt replay responses now share the same snapshot-first rule.

## Acceptance Notes

- Initial submit scoring and review status behavior is unchanged.
- Idempotent submit replay still cleans draft/cache state before returning.
- Snapshot question count is authoritative when available; current paper question count is only a legacy fallback.
