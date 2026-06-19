# 124. Draft Stale Local Revision

## Problem

Batch 123 locked the active exam page after the server reported a stale draft save.
One recovery edge remained: the stale handler updated the in-memory `draftRevision`
to the newer server revision and then wrote the rejected local answers to browser
storage. After a refresh, that local draft could appear as current because its
stored revision matched the server revision.

## Change

- `writeLocalDraft` now accepts an optional revision override.
- Normal local draft writes continue to store the current `draftRevision`.
- The stale-save path writes the rejected client revision instead of the newer
  server revision.
- The restart guard from batch 122 can now correctly identify the rejected local
  draft as older than the server draft and clear it.

## Acceptance

- A stale save response still locks further draft saves.
- The rejected local draft is preserved only with its rejected revision.
- Re-entering the exam cannot restore rejected stale answers over newer server
  answers.
- Quality gates check the stale path and the local draft revision override.
