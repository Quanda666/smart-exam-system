# 254. Score Release Transition Lock

## Goal

Make score publish and revoke transitions deterministic for a single exam under double-clicks, retries, or concurrent teacher/admin operations.

## Scope

- Lock the `exam` row before score publish and revoke state transitions.
- Reject duplicate publish attempts when scores are already published.
- Reject revoke attempts when scores are not currently published.
- Keep existing publish readiness checks:
  - exam ended;
  - no active or non-final attempts;
  - no pending review;
  - no pending appeal or open recheck;
  - completed attempts must have scores.
- Keep notification and audit-log behavior after the locked state transition checks.
- Extend quality gates so publish and revoke both keep the locked transition path.

## Three-End Coordination

- Teacher end: repeated publish/revoke clicks cannot generate conflicting score release state changes for the same exam.
- Student end: score visibility changes follow one authoritative state transition at a time, avoiding duplicate release/revoke notifications from concurrent operations.
- Admin/audit end: `score_release_log` records real state transitions instead of repeated no-op publishes or revokes.

## Acceptance Notes

- The lock is scoped to one `exam` row, so unrelated exams can still publish or revoke concurrently.
- `score_release` remains the visibility source of truth; this change hardens the transition gate that writes it.
- This complements the review and appeal locks from batches 251-253.
