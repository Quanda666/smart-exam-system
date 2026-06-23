# 238. Score Release Readiness Contract

## Goal

Make score publishing readiness a backend-owned contract instead of a teacher/admin page guess. The exam list now carries a compact blocker summary that frontends can display consistently before users try to publish scores.

## Scope

- Add `scoreReleaseReady` and `scoreReleaseBlockers` to teacher/admin exam list rows.
- Compute blockers from the same lifecycle facts already enforced by `publishScores`: exam ended, no active/non-final attempts, no pending review, no pending appeals, no open recheck appeals, no unscored completed attempts, and at least one completed scored attempt.
- Keep released exams blocked with `ALREADY_RELEASED`.
- Update frontend API types and `ExamManagement` to prefer backend readiness before falling back to local checks.
- Add quality-gate anchors so readiness fields and blocker handling do not disappear.

## Three-End Coordination

- Admin end: global exam management can show why score publishing is unavailable without invoking a failing mutation.
- Teacher end: score release buttons now follow backend-owned readiness fields and still keep local fallback logic for compatibility.
- Student end: visibility is unchanged; scores remain hidden until the formal release action succeeds.

## Acceptance Notes

- The publish mutation still performs authoritative server-side validation.
- `scoreReleaseBlockers` is a comma-separated code list for stable UI handling and future audit/export use.
- This batch does not expose scores before release and does not change the recheck appeal visibility invariant.
