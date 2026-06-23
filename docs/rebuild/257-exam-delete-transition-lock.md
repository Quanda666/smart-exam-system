# 257. Exam Delete Transition Lock

## Goal

Make exam deletion deterministic when it races with editing, approval, close, or student entry.

## Scope

- Route `deleteExam` through the locked exam status check.
- Preserve existing deletion rules:
  - exams cannot be deleted after a student has entered;
  - published exams cannot be deleted after start time.
- Keep existing cleanup:
  - soft-delete the exam;
  - delete not-started attempts;
  - delete exam target rows.
- Extend quality gates so delete joins the same locked lifecycle-transition family as approve, reject, resubmit, and close.

## Three-End Coordination

- Teacher end: delete and edit/resubmit operations serialize on the same exam row.
- Student end: deletion cannot race past lifecycle checks while a student is entering or has entered the exam.
- Admin end: approval and close operations no longer compete with a concurrent delete using stale state.

## Acceptance Notes

- The lock is scoped to one `exam` row.
- This change does not broaden which exams may be deleted.
- Attempt-level submit/finalize locking remains handled by the existing attempt path.
