# 309. Monitor Operation Audit Evidence

## Context

Monitor actions and monitor-triggered force-submit are high-risk exam operations. The backend already records monitor action rows and sends student notifications, but the operator did not receive the exact operation log evidence in the response. That made immediate admin review harder after a warning, note, acknowledge, rules reminder, or force-submit.

## Changes

- `MonitorController` now injects `OperationLogService`.
- `POST /api/monitor/sessions/{sessionId}/actions` returns `operationLogId` with the monitor action payload.
- `POST /api/monitor/sessions/{sessionId}/force-submit` returns `operationLogId` with the transactional force-submit result.
- `frontend/src/api/monitor.ts` exposes `operationLogId` on `MonitorAction` and `MonitorForceSubmitResult`.
- `ExamMonitorPanel` remembers the latest monitor operation audit evidence and provides copy buttons for:
  - operation log ID
  - `/monitor/logs?operationLogId=<id>` deep link
- `scripts/run-quality-gates.ps1` now verifies the backend response contract, frontend API type, UI evidence banner, and clipboard wiring.

## Three-End Collaboration Impact

- Teacher/admin monitor operators can immediately prove which account executed a monitor action or forced submission.
- Admin audit review can open the exact operation log without searching by time, session, or operator.
- Student-facing notification evidence remains separate from operation evidence: notification IDs prove delivery, while operation log IDs prove the monitor-side action.

## Verification

- Static quality gates assert that monitor actions and force-submit keep operation audit evidence.
- Force-submit still depends on the existing transaction flow: the monitor action is only recorded after `ExamService.forceSubmitAttempt` returns `submitType=FORCED`.
