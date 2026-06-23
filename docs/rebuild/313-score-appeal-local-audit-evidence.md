# 313. Score Appeal Local Audit Evidence

## Context

Score appeal handling changes the dispute lifecycle around a released score. The backend already returns `scoreAppealLogId` and `scoreAppealLogIds` for appeal replies and recheck closure, and the review page exposes the full appeal log drawer. The immediate action result still lacked a stable local copy surface.

## Changes

- `ReviewPanel` now stores the latest score appeal audit evidence after:
  - `replyScoreAppeal`
  - `closeScoreAppealRecheck`
- A local success banner shows the generated score appeal audit IDs.
- The banner can copy:
  - all generated score appeal audit IDs
  - `/monitor/logs?scoreAppealLogId=<first-id>` deep link
- Existing appeal log drawer, export, and per-row copy controls remain unchanged.
- `scripts/run-quality-gates.ps1` now verifies the banner, copy helpers, and reply/recheck-close wiring.

## Three-End Collaboration Impact

- Teacher side: appeal decisions and recheck completion have immediate, shareable audit evidence.
- Admin side: unified logs can open the exact score-appeal lifecycle audit row from the action result.
- Student side: score visibility and appeal status behavior remain unchanged; this change improves traceability only.

## Verification

- Static quality gates require `ReviewPanel` to call `rememberScoreAppealAudit` after appeal reply and recheck closure.
- Existing score appeal audit list/export checks continue to verify the history drawer and global audit deep links.
