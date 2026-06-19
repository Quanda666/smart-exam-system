# 123. Draft Stale Conflict Lock

## Background

Batch 122 prevented stale local drafts from being restored over newer server drafts on exam entry. Another conflict path remained during active answering: when `saveDraft` returns `stale`, the frontend updated `draftRevision` but did not block later auto-save attempts. A later auto-save could generate a newer timestamp revision and overwrite the server draft that had just been protected.

This batch locks draft saving after a server stale conflict.

## Scope

- Detect server stale responses from `saveExamDraft`.
- Stop later auto-save/manual-save attempts from overwriting the newer server draft.
- Keep the current local answers in local storage for recovery context.
- Prompt the student to re-enter the exam to merge the newest server draft.

## Frontend Contract

- `ExamTaking`
  - Adds `draftConflictLocked`.
  - Shows `服务器已有更新草稿，请重新进入考试后继续` when locked.
  - Returns early from `persistDraft` while locked.
  - On `response.data.stale`:
    - updates `draftRevision`
    - sets `draftConflictLocked = true`
    - clears retry timer
    - writes the current local draft
    - warns manual save users to re-enter the exam

## Three-End Collaboration

- Student end:
  - Does not accidentally overwrite a newer server draft after a stale conflict.
  - Keeps local answers available while being guided to reload/re-enter.

- Backend:
  - Existing stale revision rejection remains authoritative.
  - Frontend no longer turns a rejected stale save into a later timestamp-based overwrite.

- Teacher/Admin end:
  - Receives answer submissions based on deliberate student action rather than silent stale draft rollback.

## Invariant

Once the server reports a stale draft conflict, the active page must stop draft saves:

```text
save_draft_response.stale = true
=> draftConflictLocked = true
=> later persistDraft returns before saveExamDraft
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `ExamTaking` has `draftConflictLocked`.
- Locked state has a user-facing server-update message.
- `persistDraft` returns early while locked.
- Stale responses set the lock, clear retry timer, and keep local draft context.

## Acceptance Points

- Server stale rejection cannot be bypassed by the next auto-save timestamp.
- Manual save after stale conflict shows a warning instead of sending another draft.
- Local answers remain stored for recovery context.
