# 111. Score Export And Analytics Recheck Visibility

## Background

Previous batches closed student-facing and learning-side recheck leaks. The remaining score consumers were exports and dashboards: score sheets, roster exports, score history, global analysis, teacher analysis, and overview widgets.

These surfaces are often used for operational decisions, so they must not treat a score under open recheck as final.

## Scope

- Exclude open-recheck attempts from score exports.
- Exclude open-recheck attempts from global and teacher score analytics.
- Exclude open-recheck attempts from admin, teacher, and student overview score widgets.
- Add quality-gate checks for export and analytics services.

## Backend Changes

- `ExportService.examScoreSheet`
  - Excludes attempts with open `RECHECK_REQUIRED` appeals from exported score sheets.

- `ExportService.classRoster`
  - Excludes open-recheck attempts from completed exam count and average score.

- `ExportService.studentScores`
  - Excludes open-recheck attempts from student score history exports.

- `AnalysisService`
  - Excludes open-recheck attempts from average score, subject statistics, and score distribution.
  - Applies the rule to both global and teacher analysis.

- `OverviewService`
  - Admin trend excludes open-recheck attempts.
  - Teacher average score and score distribution exclude open-recheck attempts.
  - Student wrong count, average score, best score, score trend, and knowledge point cards exclude open-recheck attempts.

## Invariant

Exported or dashboard-visible score metrics require:

```text
score_release.status = 1
AND exam_attempt.status = 5
AND NOT EXISTS open RECHECK_REQUIRED score appeal
```

An open recheck appeal is:

```text
score_appeal.status = 1
AND score_appeal.handling_result = 'RECHECK_REQUIRED'
```

## Three-End Collaboration

- Student end:
  - Overview cards and score trends match the same final-score visibility rule as result detail.

- Teacher end:
  - Dashboards and exports do not include scores still under recheck.
  - Review and appeal audit screens remain the source of truth for in-progress recheck work.

- Admin end:
  - Global analytics no longer mix final scores with disputed scores.
  - Operational exports remain aligned with published, finalized, non-rechecking scores.

## Quality Gate

`scripts/run-quality-gates.ps1` now checks:

- `ExportService` excludes open-recheck attempts from score exports.
- `AnalysisService` excludes open-recheck attempts from score metrics.
- `OverviewService` excludes open-recheck attempts from score metrics.

## Acceptance Points

- A published finalized attempt without open recheck appears in exports and analytics.
- A published finalized attempt with open `RECHECK_REQUIRED` appeal is excluded from score exports.
- The same open-recheck attempt is excluded from analysis score averages and score distribution.
- The same open-recheck attempt is excluded from overview score widgets and trends.
