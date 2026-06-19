# 419. Student Exam Recovery UI

## Scope

This batch makes the student taking page show recovery state instead of hiding it behind autosave, heartbeat, and local storage behavior.

The backend state machine is unchanged. The page now exposes the evidence students need during unstable network or timeout recovery.

## Frontend Changes

- `ExamTaking` now shows a recovery panel during the exam.
- The panel displays:
  - network and heartbeat state
  - server draft revision, source, save time, and save count
  - local backup revision and save time
  - submit retry state
  - pending monitor event queue size
- Added `立即同步` to retry draft save, heartbeat sync, and monitor event upload.
- Added `恢复本机备份` to restore a valid local backup and immediately sync it back to the server.
- Local backup restore still refuses stale local drafts when the server has a newer draft.
- Offline, heartbeat failure, draft retry, submit retry, and monitor queue states now update the visible recovery status.

## API Type Changes

- `examAttemptHeartbeat` frontend type now includes:
  - `lastHeartbeatAt`
  - `draftSavedCount`

## Coordination Impact

- Student side: clearer recovery state and manual recovery actions during network instability.
- Teacher side: no workflow change; monitor events and heartbeat still feed the monitor dashboard.
- Admin side: no workflow change; this improves the student-side reliability surface without weakening server submit constraints.

## Acceptance Checks

- Student can see whether answers are synced, locally backed up, or waiting for retry.
- Student can manually retry recovery sync.
- Student can restore a non-stale local backup.
- Timeout auto-submit retry remains active and visible.
- Monitor event queue is preserved and visible when uploads are delayed.
