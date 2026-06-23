# 133. Answer Record Dedup Migration

## Problem

Batch 132 made `answer_record` unique per `attempt_id` and `question_id`.
That is the right invariant for a real exam submission pipeline, but old
databases may already contain duplicate answer rows from retries or historical
submit paths.

Adding `uk_answer_attempt_question` directly would fail on those databases and
leave the startup migration unable to enforce the new constraint.

## Change

- Added `deduplicateAnswerRecordsBeforeUniqueIndex` before
  `ensureAnswerRecordUniqueIndex` adds the unique key.
- The migration keeps the highest `answer_record.id` for each duplicate
  `(attempt_id, question_id)` group.
- `review_record.answer_record_id` and
  `review_score_log.answer_record_id` are remapped to the kept answer row before
  duplicate answer rows are deleted.
- The cleanup runs on one JDBC connection so the temporary table is visible to
  every statement in the migration batch.
- Quality gates now require the dedup migration, reference remapping, duplicate
  deletion, and ordering before the unique index DDL.

## Acceptance

- Old databases with duplicate answer rows can be cleaned before the unique key
  is added.
- Review and score audit references continue to point at an existing
  `answer_record`.
- New submissions remain idempotent through the unique key plus
  `ON DUPLICATE KEY UPDATE`.
