# 311. Score Release Local Audit Evidence

## Context

Score publish and revoke APIs already return `scoreReleaseLogId`, and unified system logs support `/monitor/logs?scoreReleaseLogId=<id>`. The remaining usability gap was local evidence: after a teacher or administrator published or revoked scores, the ID was only appended to the transient success message. Operators had no stable copy buttons unless they opened the score release history drawer.

## Changes

- `ExamManagement` now keeps the latest score release audit evidence after:
  - `publishExamScores`
  - `revokeExamScores`
- A local success banner shows the latest score release audit ID.
- The banner can copy:
  - score release audit ID
  - `/monitor/logs?scoreReleaseLogId=<id>` deep link
- Existing score release history drawer and admin-only audit buttons remain unchanged.
- `scripts/run-quality-gates.ps1` now verifies the banner, copy helpers, and publish/revoke wiring.

## Three-End Collaboration Impact

- Teacher/admin side: score visibility changes have immediate, shareable audit evidence.
- Admin side: unified logs can be opened directly from the exact release/revoke action result.
- Student side: score visibility semantics remain unchanged; students still only see scores after the release state allows it.

## Verification

- Static quality gates require publish and revoke flows to call `rememberScoreReleaseAudit` with `response.data.scoreReleaseLogId`.
- Existing score release state machine checks, release readiness gates, and student visibility gates are unchanged.
