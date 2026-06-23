# 132. Answer Record Idempotency

## Problem

`answer_record` represented one submitted answer per attempt/question, but the
database schema only had ordinary indexes on `attempt_id` and `question_id`.
Submit finalization also used a plain insert.

The normal transaction path prevents most duplicates, but a real exam system
should make this invariant explicit in the database so recovery jobs, retries,
or legacy data paths cannot create multiple answer rows for the same question in
one attempt.

## Change

- Added `uk_answer_attempt_question (attempt_id, question_id)` to `schema.sql`.
- Added `ensureAnswerRecordUniqueIndex` to startup database migration.
- Changed answer finalization insert to `ON DUPLICATE KEY UPDATE`.

## Acceptance

- A submitted attempt can have only one `answer_record` per question.
- Re-running a recovery finalization updates the existing answer row instead of
  inserting a duplicate.
- Quality gates check schema, migration, and submit upsert behavior.
