# 321. Student Dashboard Exam Deep Link

## Background

New exam notifications now point to `/student/exams?attemptId=<attemptId>`, and the student exam center can focus the matching row. The student dashboard still rendered recent exams with `attemptId` but clicked through to the generic `/student/exams`, losing that precise navigation contract.

## Change

- Keep `OverviewService.studentOverview` recent exams as the authoritative source for `attemptId`.
- Change `StudentDashboard` recent exam rows to call `openRecentExam(exam)`.
- If a valid `attemptId` exists, navigate to `/student/exams?attemptId=<attemptId>`.
- If old or incomplete data lacks `attemptId`, fall back to `/student/exams`.
- Extend quality gates to protect both the backend `attemptId` field and the frontend deep-link behavior.

## Three-End Impact

- Student end: dashboard recent exams, publish notifications, and monitor reminders now share the same attempt-focused exam center behavior.
- Teacher end: no workflow change; published attempts remain the unit students navigate to.
- Admin end: support and audit can tell students to open a concrete attempt link rather than search manually.

## Acceptance Criteria

- `/api/overview/student` recent exams include `attemptId`.
- Clicking a student dashboard recent exam with `attemptId` navigates to `/student/exams?attemptId=<id>`.
- The exam center focuses the matching row through existing route handling.
- Quality gates fail if the dashboard returns to a generic-only recent exam navigation.
