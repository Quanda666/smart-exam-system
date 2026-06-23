# 236. Submit Time Response Consistency

## Goal

Make first-submit, auto-submit, force-submit, and replayed submit responses expose the same authoritative submission timestamp. Clients should not have to wait for a later replay or list refresh to know when the server accepted the submission.

## Scope

- Add `submitTime` to the initial `finalizeAttempt` response from the database `exam_attempt.submit_time`.
- Keep stored submit response replay redacted and consistent with the first response.
- Add `submitTime` to frontend submit, heartbeat, and force-submit API response types.
- Extend the attempt-resilience verifier to assert first-submit `submitTime` and replay consistency.
- Add quality-gate anchors for backend, frontend, and verifier coverage.

## Three-End Coordination

- Student end: immediate submit and auto-submit responses can display or store the server submission time.
- Teacher end: force-submit responses now include the same submit timestamp shape.
- Admin/audit end: replayed responses and initial responses align with the persisted attempt timestamp.

## Acceptance Notes

- Scores remain redacted before release.
- `submitTime` comes from the database after the attempt status transition.
- Existing replay fallback already returned `submitTime`; this batch aligns first submit with that behavior.
