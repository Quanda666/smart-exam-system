# 347 Student Taking Payload Redaction

## Context

The student taking endpoint must be treated as a hostile-client boundary. Even when the UI does not render answers, any field returned by `/api/exams/attempt/{attemptId}/start` is visible in browser developer tools. The current backend query was already snapshot-first and did not select answers for taking questions, but the contract needed a stronger guard so future changes cannot accidentally leak `correctAnswer`, `analysis`, or correct option flags.

## Changes

- Added `sanitizeStudentTakingQuestions` before returning the student taking payload.
- The sanitizer strips question-level `correctAnswer`, `correct_answer`, `analysis`, and `correct` fields.
- The sanitizer also strips option-level `correct`, `correctAnswer`, and `correct_answer` fields.
- Replaced the frontend `ExamDetail.questions` type with a dedicated `ExamTakingQuestion` and `ExamTakingOption` contract.
- Quality gates now require both backend response redaction and the frontend no-answer taking type.

## Three-End Impact

- Student end: taking payload only contains the fields needed to render and answer questions.
- Teacher/admin end: audit snapshot APIs still expose correct answers where authorized; this change only protects the student taking boundary.
- Security/audit end: future backend or frontend edits are less likely to reintroduce answer leakage through shared paper/question types.

## Acceptance

1. Start an exam as a student.
2. The `questions` payload contains stem, type, score, sort order, difficulty, and options.
3. The payload does not contain `correctAnswer`, `correct_answer`, `analysis`, or option `correct` flags.
4. Teacher/admin exam snapshot and review flows still keep their authorized answer data.
