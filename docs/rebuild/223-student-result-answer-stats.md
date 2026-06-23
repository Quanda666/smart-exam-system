# 223. Student Result Answer Stats

## Goal

Show students the same submitted answer completeness that teachers see during review and admins can audit in exports. A released score should carry enough context for the student to understand whether the submitted paper was complete.

## Scope

- Add `questionCount`, `answeredCount`, and `unansweredCount` to `GradeInfo`.
- Populate those fields in the student grade list and released result detail query.
- Prefer the published exam question snapshot for total question count, with the current paper question list as a compatibility fallback.
- Count answered questions from non-empty persisted `answer_record` rows.
- Extend the student frontend API type.
- Display answer statistics in the student score table and result drawer.
- Add quality-gate anchors for backend DTO, SQL, API type, and UI display.

## Three-End Coordination

- Student end: released scores now show `answered/total` and unanswered counts.
- Teacher end: the same statistics remain visible in review worklists and monitor sessions.
- Admin/audit end: CSV exports and backend records now align with the student-facing score context.

## Acceptance Notes

- Unreleased, unscored, revoked, or open-recheck results remain hidden by the existing score visibility rules.
- Result detail access is still blocked unless the score is released and finalized.
- Answer statistics come from server-side persisted records, not local frontend state.
