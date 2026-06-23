# 424. Teacher Action Center

## Scope

This batch upgrades the teacher dashboard from passive metrics into an actionable workbench.

Before this step, the teacher dashboard showed counts such as pending reviews and appeals, but teachers still had to open separate pages to discover the concrete exams or appeals blocking the workflow. The new action center adds concrete, clickable tasks for review, appeal, recheck, and score release follow-up.

## Backend Changes

- Extended `GET /api/overview/teacher` with `actionCenter`.
- Added role guards:
  - `GET /api/overview/teacher` requires `ADMIN` or `TEACHER`.
  - `GET /api/overview/student` requires `STUDENT`.
- `actionCenter` includes:
  - `pendingReviewExams`
  - `pendingAppeals`
  - `openRechecks`
  - `scoreBlockedExams`
  - `readyToPublishExams`
  - `total`
  - `items`
- Action items include type, title, subject, detail, count, severity, target route, exam id, and optional appeal id.
- Backend task sources:
  - pending manual review by exam
  - pending score appeals
  - open recheck-required appeals
  - ended exams blocked from score release
  - ended exams ready for score publication

## Frontend Changes

- `TeacherDashboard` now renders an `Action Center` section.
- The section shows summary counters and concrete action rows.
- Each action row navigates to the relevant workflow:
  - review queue
  - appeal handling
  - recheck closure
  - focused exam task page for score release work
- The dashboard keeps a safe default action center so older or partial API responses do not break rendering.

## Coordination Impact

- Teacher side: review, appeal, recheck, and score release work now appears as a single prioritized list.
- Admin side: no workflow change, but admins can call the teacher overview safely when acting in a global role.
- Student side: no data exposure change. Student overview is now explicitly role-gated.
- Score release safety remains server-authoritative. The action center only links teachers into the guarded exam task workflow.

## Acceptance Checks

- Teacher dashboard loads with or without open action items.
- Pending review items navigate to `/reviews?reviewExamId=...`.
- Pending appeal items navigate to the appeal queue with the appeal id in query params.
- Recheck items navigate to the recheck review and appeal filters.
- Score release items navigate to the focused exam task.
- Frontend build and full quality gate pass.
