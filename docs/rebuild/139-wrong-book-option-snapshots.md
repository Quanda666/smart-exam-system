# 139. Wrong Book Option Snapshots

## Problem

Student wrong-question records already preferred frozen exam snapshots for
stem, type, correct answer, and analysis. The option list still came from the
current `question_option` table.

If a teacher edited a question after the exam was published, the student could
review a wrong question with options that were not the options shown during the
exam.

## Change

- `StudentService.getWrongQuestions` now carries `latest_exam_id` for each
  wrong question group.
- Added `wrongQuestionOptions`.
- Wrong-book options now prefer `exam_question_option_snapshot` for the latest
  released wrong attempt.
- The option `correct` flag is derived from the frozen
  `exam_question_snapshot.correct_answer`.
- Legacy data without option snapshots still falls back to current
  `question_option`.
- Quality gates now require snapshot-backed wrong-book options.

## Three-End Impact

- Student end: wrong-question review matches the exam-time paper.
- Teacher end: later question edits no longer rewrite a student's historical
  review context.
- Admin/audit end: student learning records are consistent with the published
  exam snapshot model.

## Acceptance

- Wrong-book question text and options use exam snapshots when present.
- Current question options are only used as a legacy fallback.
- Open recheck attempts remain excluded from wrong-book and mastery results.
