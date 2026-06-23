# 237. Monitor Force Submit Response Contract

## Goal

Make teacher/admin force-submit and monitor force-submit responses carry the same completion signals that the student answering page already relies on. A forced submit should be easy for clients and acceptance scripts to distinguish from timeout auto-submit or manual replay.

## Scope

- Add explicit `forcedSubmitted` and `submitted` flags when `forceSubmitAttempt` returns a real `FORCED` submission.
- Keep the flags conditional on `submitType = FORCED`, so already-manual submissions are not mislabeled as forced.
- Extend teacher force-submit and monitor force-submit frontend API types with `submitted`, `forcedSubmitted`, `submitTime`, replay, and answer completeness fields.
- Extend the attempt-resilience verifier to assert monitor force-submit first call and idempotent replay return forced flags, authoritative `submitTime`, and answer statistics.
- Add quality-gate anchors for backend, frontend, and verifier coverage.

## Three-End Coordination

- Admin/teacher end: force-submit actions now receive a clear forced completion contract and answer completeness counts immediately.
- Student end: submitted attempts still keep score redacted until score release and retain the existing submitted cleanup semantics.
- Monitor end: the transactional force-submit endpoint can verify both the exam submission and monitor action record without inferring forced status only from `submitType`.

## Acceptance Notes

- The response does not expose scores before release.
- Replay responses continue to reuse stored/fallback submit metadata and now preserve forced-submit semantics.
- Answer statistics remain snapshot-first through the shared submitted-answer statistics path.
