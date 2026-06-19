# 322. Admin And Teacher Exam Management Deep Link

## Background

Student exam entry links now preserve `attemptId`, but admin and teacher dashboard recent-exam rows still opened the generic exam management page. Because exam management is the shared workbench for publish, approval, snapshot, score release, revoke, and audit actions, losing the concrete exam id made the three-end workflow harder to trace.

## Change

- Add optional `examId` to `GET /api/exams/teacher`.
- Keep the existing admin/teacher data scope checks and apply `examId` inside that scope.
- Extend the frontend `listTeacherExams` API query type with `examId`.
- `ExamManagement` now reads `route.query.examId`, sends it to the backend, and highlights the matching row.
- `AdminDashboard` and `TeacherDashboard` recent exam rows navigate to `/exam-tasks?examId=<id>` when an id exists.
- Rows without an id still fall back to `/exam-tasks`.
- Extend quality gates to protect the backend filter, frontend query consumption, and both dashboard entry links.

## Three-End Impact

- Admin end: recent-exam cards land on the exact exam management row for follow-up actions and audits.
- Teacher end: recent-exam cards land on the teacher-scoped exam row without bypassing teaching scope.
- Student end: no behavior change; this aligns staff-side navigation with the student-side attempt deep-link pattern.

## Acceptance Criteria

- `GET /api/exams/teacher?examId=<id>` returns only the requested exam when the current user can see it.
- `ExamManagement` consumes `examId` from the route and highlights the returned exam row.
- Admin dashboard recent exams navigate to `/exam-tasks?examId=<id>`.
- Teacher dashboard recent exams navigate to `/exam-tasks?examId=<id>`.
- Quality gates fail if the scoped `examId` filter or dashboard deep-links are removed.
