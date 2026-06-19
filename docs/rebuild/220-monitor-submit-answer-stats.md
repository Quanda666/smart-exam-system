# 220. Monitor Submit Answer Stats

## Goal

Make backend-confirmed submit answer statistics visible to teachers in the exam monitor panel. Students already see submitted answer counts; teachers also need quick visibility during monitoring and export review.

## Scope

- Add `questionCount`, `answeredCount`, and `unansweredCount` to monitor session queries.
- Add the same fields to monitor session CSV export.
- Extend the frontend `MonitorSession` type.
- Show `answered/total answered, unanswered unanswered` in the monitor submit column when stats are available.
- Add quality-gate anchors for backend fields, export columns, frontend types, and display helper.

## Three-End Coordination

- Student end: no behavior change; submitted stats still come from backend submit responses.
- Teacher end: monitor sessions now show answer completeness beside submit time and submit type.
- Admin/audit end: exported monitor session CSVs include the same answer completeness fields.

## Acceptance Notes

- Monitor session rows include answer stat fields.
- Monitor session export includes `Question Count`, `Answered Count`, and `Unanswered Count`.
- The submit column falls back to the old submit text when stats are missing.
- The stats are read-only aggregates over paper questions and answer records.
