# 400 Draft Answer Value Boundary

## Scope
- Harden student draft saving and recovery.
- Keep draft data safe for Redis write-back, database storage, re-entry recovery, timeout submit, and force-submit.

## Backend
- `ExamService.validateDraftAnswersJson` now validates each draft answer value, not only the question id.
- Draft values may only be:
  - `null`
  - string, number, or boolean scalars
  - arrays of string, number, or boolean scalars
- Nested objects and nested arrays are rejected.
- Normalized draft answer content is bounded by the same `MAX_SUBMITTED_ANSWER_LENGTH` limit used by final submissions.
- Accepted drafts are serialized back through `OBJECT_MAPPER.writeValueAsString(sanitized)` so only scoped, validated question answers are stored.
- Recovered drafts and draft-based auto/force submit now skip old invalid values instead of returning or submitting them.

## Why This Matters
- Drafts flow through local recovery, Redis, database write-back, timeout submit, and teacher/admin force submit.
- Before this step, draft question ids were scoped, but a malformed value could still be stored and later restored.
- This makes the student terminal recovery path more predictable under network failures, stale local data, and malicious direct API calls.

## Verification
- `scripts/run-quality-gates.ps1` checks draft value validation, bounded draft answer content, normalized stored draft JSON, recovered draft filtering, and draft-based auto/force submit filtering.
