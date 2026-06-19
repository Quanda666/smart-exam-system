# 109. Recheck Close Score Visibility

## Background

Batch 107 hid scores while a recheck reopened an attempt back to pending review (`exam_attempt.status=4`). A remaining edge case appeared after the teacher resubmitted review scores: `ReviewService.submitReview` can finalize the attempt back to `status=5` before the teacher clicks `完成复核`.

Because the exam score release row is still published, student score views could become visible before the recheck appeal was formally closed.

## Scope

- Hide student scores while a `RECHECK_REQUIRED` appeal is still open.
- Block result detail access during the same open-recheck window.
- Require a finalized attempt before a teacher can close recheck.
- Show a clear student-side `复核中` state.
- Add quality-gate checks for the new invariant.

## Backend Changes

- `StudentService.getGrades`
  - Redacts `visible_score` unless scores are released, the attempt is finalized, and no open recheck appeal exists.
  - Returns `scoreVisibility=PENDING_RECHECK` when an appeal has `status=1` and `handling_result=RECHECK_REQUIRED`.

- `StudentService.getExamResult`
  - Rejects result detail access while an open recheck appeal exists.

- `ExamService.listStudentExams`
  - Applies the same score redaction rule to the student exam center.
  - Returns `scoreVisibility=PENDING_RECHECK` for open recheck appeals.

- `ScoreAppealService.closeRecheck`
  - Calls `requireRecheckAttemptFinalized`.
  - Rejects closing the recheck unless the related attempt is back to `status=5`.

## Frontend Changes

- `frontend/src/api/student.ts`
  - Adds `PENDING_RECHECK` to `GradeInfo.scoreVisibility`.

- `frontend/src/api/exam.ts`
  - Adds `PENDING_RECHECK` to `StudentExamInfo.scoreVisibility`.

- `StudentResultsPanel.vue`
  - Displays `复核中`.
  - Explains that score recheck has not completed.
  - Keeps detail and appeal actions disabled because `canViewResult` still requires `RELEASED`.

- `ExamList.vue`
  - Displays `复核中` in the student exam center.
  - Keeps result access disabled through `canViewResult`.

## Invariant

Student-visible score data requires:

```text
score_release.status = 1
AND exam_attempt.status = 5
AND NOT EXISTS open RECHECK_REQUIRED score appeal
```

An open recheck appeal is:

```text
score_appeal.status = 1
AND score_appeal.handling_result = 'RECHECK_REQUIRED'
```

## Three-End Collaboration

- Student end:
  - Sees `复核中` while the teacher is finalizing the recheck.
  - Cannot open score detail during open recheck.

- Teacher end:
  - Must finish review tasks first.
  - Must have the attempt finalized before closing recheck.
  - Closing recheck is the final step that allows student visibility to return.

- Admin end:
  - Audit views still keep the full appeal and review score trail.
  - Student-facing visibility remains separate from teacher/admin audit access.

## Quality Gate

`scripts/run-quality-gates.ps1` now checks:

- `StudentService` and `ExamService` use `NOT EXISTS` against open `RECHECK_REQUIRED` appeals.
- Both student score surfaces expose `PENDING_RECHECK`.
- `ScoreAppealService.closeRecheck` requires finalized attempts.
- Student frontend types and components understand `PENDING_RECHECK`.

## Acceptance Points

- Recheck opened: score hidden.
- Recheck review submitted but appeal not closed: score still hidden and state is `复核中`.
- Recheck close attempted before attempt finalization: rejected.
- Recheck closed after finalized review: score becomes visible again if score release is still published.
