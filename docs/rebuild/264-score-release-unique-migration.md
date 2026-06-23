# 264. Score Release Unique Migration

## Background

Score release is the authoritative switch for whether students can see scores, wrong-book entries, analysis, exports, and appeal entry points. The rebuilt exam flow requires exactly one `score_release` row per exam. If an old database contains duplicate rows for the same `exam_id`, different modules can read different publish or revoke states and break the teacher-student-admin closed loop.

## Change

- Add a startup migration guard that deduplicates historical `score_release` rows before adding the unique index.
- Keep one row per `exam_id`, preferring the most authoritative release row by published status and latest release/revoke/update time.
- Merge key state into the kept row:
  - `status` keeps the maximum release state.
  - `published_at` is retained or backfilled when the merged state is published.
  - `revoked_at` is retained or backfilled when the merged state is revoked.
  - `created_at` keeps the earliest known creation time for audit continuity.
- Delete duplicate rows only after the kept row has been reconciled.
- Add `uk_score_release_exam (exam_id)` and preserve `idx_score_release_status (status, published_at)`.

## Three-End Impact

- Teacher end: publish and revoke actions now target one stable release record per exam, so repeated publish/revoke operations cannot create competing states.
- Student end: score visibility, wrong-book availability, and appeal access read one authoritative release state.
- Admin end: score release audit, export, and monitoring can treat `exam_id` as a stable business key.

## Acceptance Criteria

- `score_release` has at most one row per `exam_id` after startup migration.
- Existing duplicate rows are cleaned before `uk_score_release_exam` is added.
- Fresh schema contains `UNIQUE KEY uk_score_release_exam (exam_id)`.
- Quality gates fail if the dedup migration, unique index, or schema index is removed.

## Follow-Up

The next score-release hardening step should verify notification and export jobs against the same one-row-per-exam invariant, especially after revoke and republish cycles.
