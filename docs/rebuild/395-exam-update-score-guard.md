# 395 Exam Update Score Guard

## Scope
- Harden exam update/resubmit against invalid pass scores.
- Keep update validation aligned with the score source used by published exams.

## Backend
- `ExamService.updateExam` now validates `passScore` before saving edits.
- Validation uses snapshot-first total score:
  - sum of `exam_question_snapshot.score` when the exam already has a frozen question snapshot
  - otherwise the current `paper.total_score`
- Updates fail when `passScore` is greater than the resolved exam total score.

## Why This Matters
- Exam creation and approval already reject pass scores above paper total.
- Before this step, a rejected or pending exam could be edited into an invalid state and only fail later during approval.
- Published future exams keep using their frozen snapshot total, so later question-bank changes do not silently change the scoring contract.

## Verification
- `scripts/run-quality-gates.ps1` checks that `updateExam` calls the score guard and that the guard resolves total score from exam snapshots before falling back to paper totals.
