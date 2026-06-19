# 136. Recheck Snapshot Question Type

## Problem

Whole-attempt score recheck reopened fill-blank and subjective answers by
looking at the current `question.question_type`.

Published exams already freeze question content in `exam_question_snapshot`.
If a question is edited, versioned, or repaired after the exam is published,
the recheck workflow must still use the exam-time question type. Otherwise the
teacher review queue can reopen the wrong answers.

## Change

- Updated `ScoreAppealService.reopenAppealReviewTasks`.
- Whole-attempt recheck now joins `exam_attempt` and
  `exam_question_snapshot`.
- The question type decision uses:
  `COALESCE(eqs.question_type, q.question_type, '')`
- Current `question` remains a fallback for legacy data that has no snapshot.
- Quality gates now require snapshot-based question type selection for recheck
  reopening.

## Three-End Impact

- Teacher end: recheck queues match the frozen paper that the student actually
  answered.
- Student end: appeal results are based on the published exam snapshot, not a
  later edited question.
- Admin/audit end: recheck behavior aligns with the snapshot model used by
  exam publishing, review detail, and score calculation.

## Acceptance

- Whole-attempt recheck reopens only frozen fill-blank and subjective answers
  when snapshots exist.
- Legacy attempts without snapshots still fall back to current question type.
- The score release recheck gate from batch 135 continues to block publishing
  while recheck appeals remain open.
