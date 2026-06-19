# 249. Wrong Book Persistence Snapshot Identity

## Goal

Align the legacy `wrong_question_book` persistence table with the snapshot-based wrong-book identity. Even though the current student wrong-book reads from released `answer_record` data, future learning tasks or exports must not inherit the old mutable `user_id + question_id` identity.

## Scope

- Add `exam_id` to `wrong_question_book` in `schema.sql`.
- Replace the old unique key with `user_id + exam_id + question_id`.
- Add an exam/question lookup index for future learning and export paths.
- Add migration self-healing for old databases:
  - Create the table if missing.
  - Add `exam_id` if missing.
  - Backfill `exam_id` from released, finalized, wrong answer records where possible.
  - Drop the legacy `user_id + question_id` unique key.
  - Add the new snapshot-identity unique key and lookup index.
- Update submit finalization to write wrong-question persistence with `exam_id`.
- Narrow test-fixture cleanup to delete wrong-question rows by exam instead of deleting every row for the same student.

## Three-End Coordination

- Student end: persisted wrong-question state now matches the visible wrong-book snapshot identity.
- Teacher end: reused or edited question-bank items no longer collapse persisted wrong-question counts across exams.
- Admin/audit end: cleanup and future exports can target the affected exam instead of wiping unrelated student learning history.

## Acceptance Notes

- Current visible wrong-book behavior remains backed by released `answer_record` rows.
- Existing old rows can only be backfilled to the best known released exam for the same student/question pair; new writes are exact.
- Legacy cleanup by student is intentionally removed to avoid deleting unrelated exam evidence.
