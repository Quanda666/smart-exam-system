# 122. Local Draft Server Conflict Guard

## Background

The taking page supports both server draft recovery and local offline draft recovery. Before this batch, the local recovery prompt did not compare the local draft with the current server draft version. A stale local draft could be restored manually and then saved back, overwriting a newer server draft.

This batch adds a conservative conflict guard.

## Scope

- Store server draft revision metadata with local drafts.
- Compare local draft metadata against the current server draft before showing the restore prompt.
- Silently clear local drafts that are clearly older than the server draft.
- Keep the existing restore prompt for local drafts that are newer or cannot be proven stale.

## Frontend Contract

- `LocalExamDraft`
  - Includes optional `draftRevision`.

- `writeLocalDraft`
  - Persists `draftRevision: draftRevision.value`.

- `restoreLocalDraftIfNeeded`
  - Loads local draft.
  - Skips empty or identical drafts.
  - Calls `localDraftIsOlderThanServer`.
  - Clears stale local drafts before any restore prompt is shown.

- `localDraftIsOlderThanServer`
  - Treats local draft as stale when its stored revision is lower than the server draft revision.
  - Falls back to comparing local `savedAt` with server `draftSavedAt` when revision is insufficient.

## Three-End Collaboration

- Student end:
  - Still gets recovery prompts for useful offline drafts.
  - Is protected from restoring clearly stale local data over newer server data.

- Teacher/Admin end:
  - Receives answer submissions based on the latest recoverable student work instead of accidental local rollback.

- Backend:
  - Existing stale revision checks remain authoritative during draft save.
  - Frontend guard reduces avoidable stale save attempts before they reach the server.

## Invariant

The taking page must not offer to restore a local draft that is known to be older than the server draft:

```text
local_draft_revision < server_draft_revision
OR local_saved_at < server_draft_saved_at
=> clear local draft, do not prompt restore
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- Local drafts carry `draftRevision`.
- `restoreLocalDraftIfNeeded` calls `localDraftIsOlderThanServer`.
- Stale local drafts are cleared before returning.
- `writeLocalDraft` persists `draftRevision.value`.
- The guard compares both revision and server saved time.

## Acceptance Points

- Newer server drafts are not overwritten by stale local drafts through the recovery prompt.
- Equal or newer local drafts can still be restored manually.
- Server-side stale revision rejection remains in place.
