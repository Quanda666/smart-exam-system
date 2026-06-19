# 227. Monitor Answer Stats Snapshot Count

## Goal

Align monitor-session answer completeness with the published exam snapshot. The teacher monitor dashboard and monitor CSV export should use the same total-question count as review, student results, and score exports.

## Scope

- Update monitor session `questionCount` to count `exam_question_snapshot` rows first.
- Keep `paper_question` as the compatibility fallback for older exams without snapshots.
- Update monitor session `unansweredCount` to use the same snapshot-first total.
- Keep `answeredCount` based on non-empty persisted `answer_record` rows.
- Add quality-gate anchors for snapshot-first monitor statistics.

## Three-End Coordination

- Teacher end: monitor dashboard submit status now uses snapshot-first answer completeness.
- Student end: released result detail and score summary already use the same published-paper context.
- Admin/audit end: monitor session CSV exports now align with review and score exports.

## Acceptance Notes

- Monitor event/risk behavior is unchanged.
- The CSV header and frontend display remain unchanged.
- Snapshot question count is authoritative when available; current paper question count is only a legacy fallback.
