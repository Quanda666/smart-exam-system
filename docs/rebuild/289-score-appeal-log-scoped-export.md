# Step 289 - Score Appeal Log Scoped Export

## Background

Teachers can inspect a single score appeal lifecycle in the review workbench, and administrators can export the global score appeal audit table. The scoped teacher drawer still lacked an export action, which made it harder to archive the exact appeal trail for a student-facing dispute or department review.

## Changes

- Added `ScoreAppealService.exportAppealLogs`.
- Added `GET /api/reviews/appeals/{id}/logs/export`.
- The export reuses the existing appeal access check, so teachers can only export appeals in their teaching scope.
- Added frontend `exportScoreAppealLogs`.
- Added an Export button to the teacher appeal log drawer.
- Added quality-gate checks for the backend route, CSV generation, frontend API, and drawer export controls.

## Acceptance

- A teacher can export the lifecycle logs for an accessible score appeal.
- The CSV contains log ID, time, appeal ID, exam, student, question ID, action, status transition, handling result, actor, and note.
- Appeals outside the teacher's scope remain blocked by the existing appeal access check.
- Quality gates fail if the scoped export route or frontend export action is removed.
