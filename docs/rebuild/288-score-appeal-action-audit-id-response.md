# Step 288 - Score Appeal Action Audit ID Response

## Background

Score appeal audit rows can be searched globally and copied from the administrator System Log page. The teacher review workbench, however, still handled appeal replies and recheck closures as generic business actions. Teachers could not immediately identify which `score_appeal_log` rows were created by a reply, recheck reopen, or recheck close operation.

## Changes

- `ScoreAppealService` now returns generated `score_appeal_log.id` values after appeal transitions.
- Student appeal submission adds `scoreAppealLogId` and `scoreAppealLogIds` to the returned appeal object for structural consistency.
- Teacher appeal replies return the reply audit ID, and when recheck is required also return the `RECHECK_OPEN` audit ID.
- Recheck close returns the generated `CLOSE_RECHECK` audit ID.
- Frontend review and student appeal types expose optional `scoreAppealLogId` and `scoreAppealLogIds`.
- `ReviewPanel` success messages now include appeal audit log IDs.
- The teacher appeal log drawer now exposes copyable score appeal audit IDs and global audit deep links.
- `scripts/run-quality-gates.ps1` guards the backend response fields, API types, teacher success hints, and copy-link controls.

## Acceptance

- Appeal submit, reply, recheck-open, and recheck-close actions return generated audit log IDs.
- Teacher appeal handling feedback includes audit evidence IDs.
- Teacher appeal log rows can copy the raw audit ID.
- Teacher appeal log rows can copy a global audit deep link using `scoreAppealLogId`.
- Quality gates fail if the audit ID response or teacher-side copy controls are removed.
