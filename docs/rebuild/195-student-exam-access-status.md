# Batch 195 - Student Exam Access Status

## Scope
- Added the first waiting-room/access-status layer to the student exam center.
- Kept backend `startExam` as the hard enforcement point; this batch improves the list/pre-entry contract and UI gating.

## Changes
- `ExamService.listStudentExams` now returns server-side access fields:
  - `accessStatus`: `READY`, `WAITING`, `IN_PROGRESS`, `CLOSED`, `UNPUBLISHED`, `SUBMITTED`
  - `canStart`
  - `secondsUntilStart`
  - `secondsUntilEnd`
  - `serverTime`
- Frontend `StudentExamInfo` exposes those fields.
- Student exam list now:
  - disables the enter button based on server access state before falling back to local time
  - shows a compact access hint such as `Starts in ...`, `Open, ... left`, or `Exam window closed`
  - keeps the existing result visibility guard unchanged

## Why This Matters
- Students get a clearer waiting/closed reason before attempting to enter an exam.
- The frontend no longer relies only on the browser clock for entry gating.
- Direct API access is still protected by the existing `startExam` server-side time/status/candidate checks.

## Verification
- Quality gates were extended to require the access-status fields and frontend gating helpers.
