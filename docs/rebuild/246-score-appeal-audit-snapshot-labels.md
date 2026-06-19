# 246. Score Appeal Audit Snapshot Labels

## Goal

Make score appeal and review-score audit question labels use the exam snapshot before falling back to the current question bank. Post-exam edits to the question bank must not change what students, teachers, or administrators see as the appeal/review target.

## Scope

- Update score appeal list/detail queries to return snapshot-first `questionStem` and `questionType`.
- Update administrator score-appeal audit list/export to return snapshot-first question labels.
- Update administrator review-score audit list/export to return snapshot-first question labels.
- Update audit keyword search so snapshot stems are searchable.
- Add quality-gate anchors for snapshot-first appeal and audit labels.

## Three-End Coordination

- Student end: appeal records show the exact question text from the released exam snapshot when available.
- Teacher end: appeal handling views use the same frozen question label as the student-facing record.
- Admin/audit end: audit lists, exports, and keyword searches align with the snapshot-first exam evidence trail.

## Acceptance Notes

- Snapshot-backed exams use `exam_question_snapshot.stem` and `question_type`.
- Legacy exams without snapshots continue to fall back to `question`.
- This batch changes display/search evidence only; scoring, recheck, and release state transitions are unchanged.
