# 140. Result Detail Option Snapshots

## Problem

Student score details showed stem, answer, correct answer, score, and analysis,
but objective questions did not include the option list. After batch 139 fixed
wrong-book options, result details still lacked the same exam-time context.

A real score detail page should let students review the paper they actually
answered, not only the final answer key.

## Change

- `StudentService.getExamResult` now attaches `options` to each answer.
- Result-detail options reuse the snapshot-first option lookup:
  `exam_question_option_snapshot` plus frozen `exam_question_snapshot.correct_answer`.
- The internal `examId` used to load snapshots is removed before returning the
  response.
- `frontend/src/api/student.ts` exposes answer-level `options`.
- `StudentResultsPanel.vue` renders the options in the result drawer and
  highlights correct options.
- Quality gates now require snapshot-backed options for both result details and
  wrong-question review.

## Three-End Impact

- Student end: result details can reconstruct objective questions with their
  exam-time options.
- Teacher end: later question edits no longer change what students see when
  checking historical result details.
- Admin/audit end: result detail, wrong book, and score release visibility now
  follow the same snapshot model.

## Acceptance

- Released result details include answer options for objective questions.
- Options prefer exam snapshots and fall back to current question options only
  for legacy data.
- Unreleased, revoked, pending-review, and pending-recheck results remain
  hidden by existing visibility gates.
