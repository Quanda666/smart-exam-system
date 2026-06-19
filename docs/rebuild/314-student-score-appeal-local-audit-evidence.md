# 314. Student Score Appeal Local Audit Evidence

## Context

Student score appeals already create `score_appeal_log` rows and the backend response includes generated `scoreAppealLogId` / `scoreAppealLogIds`. Teachers and administrators can search, export, and deep-link those logs, but the student submission page still treated appeal submission as a simple success toast.

For a real exam dispute workflow, the student should immediately have copyable evidence for the exact audit row created by the submission.

## Changes

- `StudentResultsPanel` now stores the latest student-side score appeal audit evidence after `submitScoreAppeal`.
- A local success banner shows the generated score appeal audit IDs.
- The banner exposes copy actions for:
  - all generated score appeal audit IDs
  - `/monitor/logs?scoreAppealLogId=<first-id>` deep link
- The existing appeal list reload still runs after submission, so the student sees both the business appeal row and the audit evidence.
- The quality gate now verifies that student appeal submission keeps the local audit evidence surface wired to the backend response.

## Three-End Coordination

- Student side: after submitting an appeal, the student can retain an exact audit ID for support or dispute follow-up.
- Teacher side: the same appeal continues to appear in the review workbench and can be handled through the existing appeal lifecycle.
- Administrator side: the copied deep link opens the unified score appeal audit search by `scoreAppealLogId`.

## Acceptance Notes

- Submitting an eligible appeal records `lastStudentScoreAppealAudit`.
- The action banner copies raw audit ID values.
- The action banner copies a global score appeal audit deep link.
- Static quality gates fail if the student page stops consuming `response.data.scoreAppealLogIds` after submission.
