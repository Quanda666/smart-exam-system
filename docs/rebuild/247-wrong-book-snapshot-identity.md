# 247. Wrong Book Snapshot Identity

## Goal

Make the student wrong-book and AI explanation flow identify wrong questions by exam snapshot, not only by question-bank ID. If the same question-bank item is edited and reused in another exam, old and new released wrong-answer records must stay separate.

## Scope

- Add `examId` to student wrong-question DTO/API payloads.
- Group wrong-book records by `question_id + exam_id`.
- Return the latest wrong record per exam snapshot rather than per mutable question-bank row.
- Require AI wrong-question explanation requests to include `examId`.
- Ground AI explanation lookup in the student's released wrong answer for that exact exam and question.
- Update frontend wrong-book loading/request state to use `examId:questionId`.
- Add quality-gate anchors for snapshot identity in wrong-book and AI explanation paths.

## Three-End Coordination

- Student end: wrong-book rows and AI explanations now refer to the exact released exam snapshot the student got wrong.
- Teacher end: later edits to a reused question-bank item no longer collapse historical wrong-book evidence.
- Admin/audit end: wrong-book learning evidence remains aligned with score-release and appeal snapshot semantics.

## Acceptance Notes

- The frontend still sends no stem, answer, correct answer, or analysis to the AI endpoint.
- The backend reloads all explanation context from released, scored, non-recheck wrong answers.
- Legacy exams still work because `examId + questionId` can fall back to current question/options when no snapshot exists.
