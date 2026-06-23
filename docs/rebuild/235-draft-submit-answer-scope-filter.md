# 235. Draft Submit Answer Scope Filter

## Goal

Prevent historical dirty drafts from disrupting draft-based submission flows. Timeout submit, force submit, and other server-side draft finalization paths should preserve valid draft answers while ignoring forged or stale question ids.

## Scope

- Update `loadDraftAnswerMap` to filter recovered draft keys against the current attempt question set.
- Skip non-numeric draft keys instead of discarding the entire draft.
- Skip out-of-scope question ids before calling `finalizeAttempt`.
- Reuse the same snapshot-first `expectedQuestionIdsForAttempt` helper used by draft save and recovery sanitization.
- Add quality-gate anchors for draft-based auto/force submission filtering.

## Three-End Coordination

- Student end: timeout auto-submit can still submit valid saved answers even when an old draft has extra bad keys.
- Teacher end: monitor force-submit and administrative force-submit are less likely to fail because of historical dirty draft data.
- Admin/audit end: final `answer_record` rows remain limited to assigned attempt questions.

## Acceptance Notes

- Manual submit payload validation remains strict and still rejects out-of-scope submitted answers.
- This batch does not rewrite persisted draft rows.
- Empty or malformed draft JSON still results in an empty draft answer map.
