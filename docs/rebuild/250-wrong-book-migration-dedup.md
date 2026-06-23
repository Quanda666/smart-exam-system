# 250. Wrong Book Migration Deduplication

## Goal

Make the `wrong_question_book` snapshot-identity migration reliable for dirty legacy databases. A database that once missed the old unique key, or already accumulated duplicate rows, must still be able to add the new `user_id + exam_id + question_id` unique key.

## Scope

- Add a pre-index cleanup step in `DatabaseMigrationRunner`.
- Merge duplicate `wrong_question_book` rows by `user_id + exam_id + question_id`.
- Preserve the newest retained row id while carrying forward:
  - summed `wrong_count`;
  - latest `last_wrong_time`;
  - earliest `created_at`.
- Delete duplicate rows before adding `uk_wrong_user_exam_question`.
- Extend quality gates so this migration cannot regress silently.

## Three-End Coordination

- Student end: wrong-book learning history remains visible under the same released exam/question identity after legacy cleanup.
- Teacher end: repeated or edited question-bank items no longer risk collapsing across exams because the database constraint can now be applied reliably.
- Admin/audit end: old installations with inconsistent learning records can start successfully and converge toward the snapshot identity model.

## Acceptance Notes

- The cleanup only touches exact duplicate snapshot identities.
- New writes are still handled by `ExamService.finalizeAttempt` with explicit `exam_id`.
- If cleanup itself fails, startup continues and logs the migration error, matching the existing migration-runner failure posture.
