# 421. Exam Lifecycle Timeline

## Scope

This batch adds a unified lifecycle view for each exam.

Before this step, teachers and administrators had to open separate approval logs, score release logs, operation logs, and monitor evidence to understand what happened to one exam. The new lifecycle endpoint and drawer combine those records into one ordered evidence stream.

## Backend Changes

- Added `GET /api/exams/{id}/lifecycle`.
- The endpoint is available to `ADMIN` and `TEACHER` through the same ownership boundary used by exam management.
- `ExamService.getExamLifecycle` returns:
  - exam metadata, paper, subject, creator, schedule, snapshot counts
  - attempt summary, including active, pending review, completed, manual, timeout, and forced submissions
  - monitor summary, including online/offline/high-risk sessions and event volume
  - approval logs
  - score release logs
  - operation logs scoped to `EXAM#{id}`
  - monitor actions and forced submission evidence
  - a unified ascending `timeline`
- The timeline is capped at 500 returned events while keeping the first creation event and the latest evidence.

## Frontend Changes

- `frontend/src/api/exam.ts` now exposes `getExamLifecycle` and typed lifecycle DTOs.
- `ExamManagement` adds a `Timeline` action per exam row.
- The lifecycle drawer shows:
  - exam header and current phase
  - attempt, monitor, approval, score, and risk summary counters
  - truncation warning when the server hides older events
  - ordered timeline items with actor, status transition, related object, note, and details

## Coordination Impact

- Admin side: can audit one exam without jumping between multiple log tabs.
- Teacher side: can explain publish, close, score release, monitor action, and forced submit history from the exam list.
- Student side: no data exposure change. The lifecycle view does not include answer content or unreleased score details.

## Acceptance Checks

- `GET /api/exams/{id}/lifecycle` returns one ordered evidence stream.
- Timeline includes approval, score release, operation, monitor action, and forced submit records when present.
- Teachers remain limited to owned exams; administrators can inspect all exams.
- Frontend build passes after adding the lifecycle drawer.
- Full local quality gate passes.
