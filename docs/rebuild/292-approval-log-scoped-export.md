# Step 292 - Approval Log Scoped Export

## Background

Exam approval history already records submit, resubmit, approve, reject, and direct publish transitions. It also carries publish notification counts. The history was visible in both exam management and the approval queue, but neither drawer could export the scoped approval trail for offline review.

## Changes

- Added `ExamService.exportApprovalLogs`.
- Added `GET /api/exams/{id}/approval-logs/export`.
- The export reuses the existing exam access check from approval log listing.
- Added frontend `exportExamApprovalLogs`.
- Added Export buttons to approval log drawers in:
  - `ExamManagement.vue`
  - `ExamApprovalQueue.vue`
- Added quality-gate checks for the backend CSV, controller route, frontend API, and both drawer export controls.

## Acceptance

- A teacher or administrator can export approval lifecycle logs for an accessible exam.
- The CSV contains log ID, time, exam, action, status transition, actor, candidate count, notification counts, and note.
- Both exam management and approval queue expose the scoped export.
- Quality gates fail if the scoped export route or either frontend export action is removed.
