# 229. Approval Risk Snapshot Scoring

## Goal

Align approval and overview risk indicators with the published exam snapshot. When an exam already has a frozen question snapshot, risk checks must not be distorted by later paper edits.

## Scope

- Update approval queue `questionCount` and `totalScore` to prefer `exam_question_snapshot`.
- Keep `paper_question` as the fallback for pending or legacy exams without snapshots.
- Update approval queue `NO_QUESTIONS` and `PASS_SCORE_OVER_TOTAL` risk flags to use the same snapshot-first totals.
- Update approval queue risk filters so filtered results match the displayed risk flags.
- Update admin overview pending-approval risk flags with the same snapshot-first expressions.
- Add quality-gate anchors for approval/overview snapshot-first risk checks.

## Three-End Coordination

- Admin end: approval queue and dashboard risk cards now judge published or migrated exams against the frozen paper state.
- Teacher end: exam approval feedback is less likely to change unexpectedly after a paper is edited for a later exam.
- Student end: no direct UI change, but downstream exam status and approval decisions now align with the exam snapshot students actually receive.

## Acceptance Notes

- Exam approval workflow is unchanged.
- Pending exams without snapshots still use current `paper_question` data.
- Published exam snapshots are authoritative for risk indicators whenever snapshot rows exist.
