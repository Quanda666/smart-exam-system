# 222. Score Export Answer Stats

## Goal

Carry submitted answer completeness into score CSV exports. Teachers and admins need the same context they see in monitor and review screens when they audit released grades offline.

## Scope

- Add `questionCount`, `answeredCount`, and `unansweredCount` to exam score sheet exports.
- Add the same fields to per-student score history exports.
- Count questions from the published exam snapshot first, with the current paper question list as the compatibility fallback.
- Count answered questions from non-empty persisted `answer_record` rows for the finalized attempt.
- Add quality-gate anchors for the SQL fields and CSV headers.

## Three-End Coordination

- Teacher end: exported grade sheets show whether a score came from a complete or incomplete submission.
- Student end: no direct behavior change; exports still require released, finalized, scored attempts and exclude open recheck cases.
- Admin/audit end: offline CSV evidence now aligns with monitor/review answer completeness metrics.

## Acceptance Notes

- Score exports remain blocked before score release.
- Open recheck appeals remain excluded from score exports.
- CSV headers include `Question Count`, `Answered Count`, and `Unanswered Count`.
- The statistics are derived from server records, not frontend state.
