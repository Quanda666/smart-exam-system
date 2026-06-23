# 110. Learning-Side Recheck Visibility

## Background

Batches 107 and 109 protected student score list, exam center, and result detail during open recheck. Another side channel remained: learning features such as wrong question review, knowledge mastery, and teacher student insight also read answer records and scores.

Those features should not surface disputed answers, explanations, or score trends while a `RECHECK_REQUIRED` appeal is still open.

## Scope

- Exclude open-recheck attempts from the student wrong question book.
- Exclude open-recheck attempts from student knowledge point mastery.
- Exclude open-recheck attempts from teacher-side student insight counts, averages, exam rows, and summaries.
- Add quality-gate checks for these learning-side paths.

## Backend Changes

- `StudentService.getWrongQuestions`
  - Still requires released scores and finalized attempts.
  - Adds `NOT EXISTS` against open `RECHECK_REQUIRED` score appeals.
  - Prevents disputed answers, correct answers, and analysis from entering the student wrong question book before recheck close.

- `StudentService.getKnowledgePointMastery`
  - Adds the same open-recheck exclusion before aggregating mastery.

- `StudentInsightService.listClassStudents`
  - Excludes open-recheck attempts from completed exam count.
  - Excludes open-recheck attempts from average score.

- `StudentInsightService.studentInsight`
  - Excludes open-recheck attempts from the exam trend table.
  - Excludes open-recheck attempts from summary count, average, max, and min.

## Invariant

Learning-side score and answer-derived data requires:

```text
score_release.status = 1
AND exam_attempt.status = 5
AND NOT EXISTS open RECHECK_REQUIRED score appeal
```

This applies to:

- Student wrong question book.
- Student knowledge mastery.
- Teacher student insight.

## Three-End Collaboration

- Student end:
  - Wrong questions and mastery no longer include disputed answers during recheck.
  - After recheck closes, released and finalized attempts can re-enter learning features.

- Teacher end:
  - Student insight avoids using scores still under recheck.
  - Teachers still have review and appeal audit screens for in-progress recheck work.

- Admin end:
  - Audit views remain complete.
  - Operational analytics avoid treating disputed scores as final learning data.

## Quality Gate

`scripts/run-quality-gates.ps1` now checks:

- `StudentService` wrong book and mastery methods include open-recheck exclusion.
- `StudentInsightService` uses released-score joins and excludes open rechecks.

## Acceptance Points

- Released finalized attempt without open recheck appears in wrong book/mastery/insight.
- Attempt with open `RECHECK_REQUIRED` appeal is excluded from wrong book.
- Attempt with open `RECHECK_REQUIRED` appeal is excluded from mastery aggregation.
- Attempt with open `RECHECK_REQUIRED` appeal is excluded from teacher student insight counts and score statistics.
