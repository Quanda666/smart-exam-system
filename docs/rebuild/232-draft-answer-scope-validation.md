# 232. Draft Answer Scope Validation

## Goal

Close the draft-saving gap in the student answering lifecycle. Draft answers must obey the same attempt question scope as final submission, so forged or stale question ids cannot be stored in Redis or the database and later replayed through recovery, timeout submit, or force submit.

## Scope

- Validate draft answer JSON before writing to Redis or `exam_answer_draft`.
- Treat empty draft payloads as `{}`.
- Reject invalid JSON, non-numeric question ids, and question ids outside the current attempt question set.
- Reuse `loadQuestionsForSubmit`, so the validation follows the same snapshot-first question scope used by final submission.
- Keep stale-revision conflict behavior unchanged.
- Add quality-gate anchors for draft answer scope validation.

## Three-End Coordination

- Student end: local draft recovery cannot rehydrate server-side forged question ids.
- Teacher end: timeout and forced submissions from drafts now inherit a cleaner answer set.
- Admin/audit end: resilience and incident analysis can trust that persisted drafts only reference questions assigned to the attempt.

## Acceptance Notes

- Final submission validation remains unchanged and still acts as the last line of defense.
- Redis write-back mode and database draft mode both use the validated payload.
- Old persisted drafts are not rewritten by this batch; they are still rejected during final submission if they contain out-of-scope question ids.
