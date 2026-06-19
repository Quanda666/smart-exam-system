# 149 Analysis Completed Count Release Gate

## Scope

This batch tightens the administrator and teacher analysis dashboards so score analytics use one consistent sample:

- released scores only
- finalized attempts only
- non-null attempt scores only
- attempts without an open `RECHECK_REQUIRED` score appeal

## Changes

- Updated `AnalysisService.overview.completedCount` to count only released, finalized, non-rechecking scored attempts.
- Updated `AnalysisService.teacherOverview.completedCount` with the same release and recheck gates, scoped to the teacher's exams.
- Updated global and teacher `subjectStats.attemptCount` to count the same scored analysis sample used by `avgScore`.
- Added a quality gate to prevent the analysis dashboard from regressing to raw `exam_attempt.status = 5` counts.

## Why This Matters

Before this batch, the analysis cards could show all completed attempts while average score, score distribution, and subject averages only used released scores. That made the same dashboard mix operational completion data with student-visible score analytics.

After this batch, the analysis page treats `completedCount` as the count of attempts that are actually eligible for score analytics. Raw attempt volume remains available through `attemptCount`.

## Verification

- Java source hygiene
- Frontend source hygiene
- PowerShell parser smoke
- Local quality gates

