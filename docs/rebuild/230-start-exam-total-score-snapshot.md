# 230. Start Exam Total Score Snapshot

## Goal

Align the student exam-taking payload total score with the frozen exam question snapshot. Students should see a total score that matches the question set they actually answer, even if the source paper is edited after publication.

## Scope

- Update `startExam` to calculate `totalScore` from `exam_question_snapshot.score` when snapshot rows exist.
- Keep `paper.total_score` as the legacy fallback for exams without question snapshots.
- Keep the question loading behavior unchanged: `loadExamQuestions` already uses snapshot-first questions.
- Add a quality-gate anchor so the taking payload cannot regress to a live-paper total while questions use snapshots.

## Three-End Coordination

- Student end: exam-taking metadata now matches the frozen question set used for answering.
- Teacher end: published exam edits no longer change the total score shown to students during a later attempt entry.
- Admin/audit end: score exports, result details, approval risk checks, and taking payload totals now follow the same snapshot-first rule.

## Acceptance Notes

- No frontend API shape changes are required.
- Correct answers and analysis remain excluded from the active exam-taking payload.
- Old exams without snapshots still fall back to the current paper total.
