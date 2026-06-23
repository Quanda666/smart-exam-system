# 112. Exam Taking Answer Redaction Contract

## Background

The score visibility work closed many post-exam leaks. A separate high-risk contract is the active exam-taking payload: while a student is answering, the backend must never return correct answers, answer flags, or analysis.

The current backend already loads a redacted question payload for `startExam`. This batch turns that behavior into an explicit frontend type and quality-gate rule so future changes do not accidentally expose answer data.

## Scope

- Keep student exam-taking payloads free of correct answers and analysis.
- Tighten frontend `ExamDetail` typing so taking screens cannot rely on answer fields.
- Add quality-gate checks around the backend loader and frontend type.

## Backend Contract

- `ExamService.startExam`
  - Uses `loadExamQuestions`.
  - `loadExamQuestions` returns only:
    - question id
    - question type
    - stem
    - difficulty
    - score
    - sort order
  - Objective question options return only label/content/sort order.

- Correct answers remain available only inside backend submit/finalization logic and teacher/admin audit/review paths.

## Frontend Changes

- `frontend/src/api/exam.ts`
  - `ExamDetail.questions` now omits `analysis`.
  - Question options now omit `correct`.

This keeps the taking component type aligned with the real student-facing API contract.

## Invariant

Student exam-taking payloads must not expose:

```text
correctAnswer
correct_answer
analysis
option.correct
```

## Three-End Collaboration

- Student end:
  - Receives only fields needed to answer the exam.

- Teacher/Admin end:
  - Continue to use snapshot, review, and audit APIs for correct answers and analysis.

- Backend:
  - Keeps answer-bearing queries separated from student active-taking queries.

## Quality Gate

`scripts/run-quality-gates.ps1` now checks:

- `ExamService.loadExamQuestions` does not include `correctAnswer`, `correct_answer`, or `analysis`.
- `ExamDetail` frontend type omits `analysis`.
- Exam-taking option types omit `correct`.

## Acceptance Points

- Starting an exam returns redacted questions only.
- Objective options do not reveal correctness flags.
- Submit/finalize logic can still grade using backend-only answer loading.
- Teacher/admin snapshot and review flows remain unaffected.
