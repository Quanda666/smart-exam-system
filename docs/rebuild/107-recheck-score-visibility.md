# 107. Recheck Score Visibility Guard

## Background

Batch 106 reopens review tasks when an appeal is handled as `RECHECK_REQUIRED`. That moves the related `exam_attempt` back to pending review (`status=4`), but the exam-level `score_release` row can still be published.

Before this batch, the student exam center still used only `score_release.status=1` to show scores and enable result access. A reopened recheck attempt could therefore expose an outdated score from the exam list even though result detail was already protected elsewhere.

## Scope

- Require both published scores and finalized attempts before exposing a student score.
- Return explicit score visibility fields from the student exam list.
- Disable the student result button in the exam center while a recheck is open.
- Add quality-gate checks so this visibility rule does not regress.

## Backend Changes

- `ExamService.listStudentExams`
  - Redacts `score` unless `score_release.status=1` and `exam_attempt.status=5`.
  - Adds `scoreVisible`.
  - Adds `scoreVisibility` values:
    - `RELEASED`
    - `REVOKED`
    - `PENDING_RELEASE`
    - `PENDING_REVIEW`
    - `PENDING_FINALIZE`

## Frontend Changes

- `frontend/src/api/exam.ts`
  - Adds `scoreVisible` and `scoreVisibility` to `StudentExamInfo`.

- `ExamList.vue`
  - Adds `canViewResult`.
  - Enables result viewing only when:
    - `scoreVisible` is true or `1`
    - `scoreVisibility` is `RELEASED`
    - attempt `status` is `5`
  - Blocks direct click handling with the same rule.

## Three-End Collaboration

- Student end:
  - The exam center no longer leaks stale scores while a recheck is open.
  - The result detail route remains protected by `StudentService.getExamResult`.

- Teacher end:
  - Teachers can reopen review tasks through the appeal workflow.
  - Reopened attempts remain visible to review and audit workflows, but not to student score views.

- Admin end:
  - Admin monitoring and score audit views keep access to historical score changes.
  - Student-facing score visibility remains separated from audit visibility.

## Invariant

Student-visible score data requires:

```text
score_release.status = 1 AND exam_attempt.status = 5
```

If an appeal recheck moves an attempt back to `status=4`, the student exam center, grade list, and result detail must all hide score data until review is completed and the attempt is finalized again.

## Quality Gate

`scripts/run-quality-gates.ps1` now checks:

- `ExamService.listStudentExams` redacts scores unless released and finalized.
- `ExamService.listStudentExams` exposes `scoreVisible` and `scoreVisibility`.
- `frontend/src/api/exam.ts` exposes the new visibility fields.
- `ExamList.vue` uses `canViewResult` with released and finalized checks.

## Acceptance Points

- A released exam with attempt `status=5` shows the score and permits result viewing.
- A released exam with attempt `status=4` hides the score and blocks result viewing.
- A revoked or unpublished release hides the score.
- Teacher/admin audit data remains available for investigation.
