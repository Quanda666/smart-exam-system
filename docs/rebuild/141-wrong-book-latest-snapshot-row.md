# 141. Wrong Book Latest Snapshot Row

## Problem

Batch 139 made wrong-book options use the latest wrong exam snapshot, but the
wrong-book text fields still used aggregate expressions such as
`MAX(eqs.stem)`.

When the same question appeared in multiple released exams, the stem, correct
answer, analysis, and options could come from different historical snapshots.

## Change

- `StudentService.getWrongQuestions` now builds a `wrong_answers` CTE with one
  row per visible wrong answer.
- A `ranked_wrong` CTE uses `ROW_NUMBER()` to select the latest wrong answer for
  each `question_id`.
- The visible wrong-book stem, type, correct answer, analysis, latest wrong
  time, and option snapshot now all come from that same latest row.
- `COUNT(*) OVER (PARTITION BY question_id)` keeps the cumulative wrong count.
- Quality gates now reject returning to `MAX(eqs.*)` snapshot aggregation.

## Three-End Impact

- Student end: wrong-book details are internally consistent for reused
  questions across multiple exams.
- Teacher end: later exam snapshots no longer partially overwrite the review
  context of another historical attempt.
- Admin/audit end: wrong-book evidence follows one concrete released attempt
  snapshot instead of a mixed aggregate.

## Acceptance

- Wrong-book display fields and options are sourced from the same latest wrong
  answer snapshot.
- Wrong counts remain cumulative per question.
- Open recheck attempts remain excluded from wrong-book results.
