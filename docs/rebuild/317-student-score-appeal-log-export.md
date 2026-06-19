# 317. Student Score Appeal Log Export

## Context

Step 316 allowed students to inspect the lifecycle logs for their own score appeals. A formal dispute workflow also needs a portable record: students may need to provide the appeal trail to a teacher, department administrator, or support desk.

Teachers already have scoped appeal log export, and administrators have global score appeal audit export. This step adds the student equivalent with an ownership-only access check.

## Changes

- Added `GET /api/student/appeals/{id}/logs/export`.
- Added `ScoreAppealService.exportMyAppealLogs(id, student)`.
- Student export reuses `requireStudentAppealAccess`, so `sa.id = ? AND sa.user_id = ?` remains the hard boundary.
- Extracted the shared CSV assembly into `buildAppealLogsExport(...)` so teacher and student exports keep the same columns.
- Added frontend `exportMyScoreAppealLogs(id, examName)`.
- Added an `Export` button to the student appeal log drawer.
- Added quality gate checks for:
  - student export endpoint
  - student ownership check reuse
  - shared CSV builder
  - frontend download API and export button wiring

## Three-End Coordination

- Student side: students can export their own appeal lifecycle evidence.
- Teacher side: teacher workbench export remains scoped by teaching access and uses the same CSV format.
- Administrator side: administrator global score appeal audit export remains the cross-exam compliance view.

## Acceptance Notes

- A student can export logs only for an appeal they own.
- Exported CSV includes log ID, time, appeal ID, exam, student, question ID, action, status transition, handling result, actor, and note.
- The student log drawer has a single appeal-level export action.
- Static quality gates fail if the student export endpoint or frontend export wiring is removed.
