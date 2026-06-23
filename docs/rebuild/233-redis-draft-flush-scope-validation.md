# 233. Redis Draft Flush Scope Validation

## Goal

Extend draft answer scope validation to Redis write-back. The asynchronous flush path and the pre-submit flush path must not persist cached answers that contain forged or stale question ids.

## Scope

- Validate cached Redis draft answers before batch write-back to `exam_answer_draft`.
- Validate cached Redis draft answers before the single-attempt flush performed during submit.
- Delete invalid dirty cache entries and record the flush as skipped instead of retrying the same bad payload forever.
- Continue using `safeAnswers` for database writes and `markClean`.
- Add quality-gate anchors for both Redis flush paths.

## Three-End Coordination

- Student end: stale or malformed cached drafts cannot override a valid database draft during recovery.
- Teacher end: timeout and force-submit flows see cleaner draft state when Redis write-back is enabled.
- Admin/audit end: draft cache metrics now count invalid cached payloads as skipped rather than silently persisting them.

## Acceptance Notes

- The normal HTTP draft-save path already validates draft answer scope.
- Submitted attempts and missing attempts still clean cache entries as before.
- Final submission validation remains the last line of defense.
