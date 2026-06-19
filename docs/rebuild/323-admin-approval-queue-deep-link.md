# 323. Admin Approval Queue Deep Link

## Background

The admin dashboard shows concrete pending approval exams with their ids and risk flags, but each row still opened the generic approval queue. That made the approval SLA panel useful as a count, but weak as an operational entry point.

## Change

- Add optional `examId` to `GET /api/exams/approvals`.
- Apply `examId` inside the existing admin-only approval queue query.
- Extend `listExamApprovalQueue` with an `examId` query parameter.
- `ExamApprovalQueue` now reads `route.query.examId`, sends it to the backend, and highlights the matching row.
- `AdminDashboard` pending approval rows navigate to `/exam-approvals?examId=<id>` when an id exists.
- Rows without an id still fall back to `/exam-approvals`.
- Extend quality gates to protect the backend filter, frontend route consumption, row highlight, and dashboard deep-link.

## Three-End Impact

- Admin end: SLA pending approval cards become actionable and land on the exact approval request.
- Teacher end: no behavior change; teachers still receive approval decisions through the existing workflow.
- Student end: no behavior change until approval; after approval the existing publish snapshot and notification flow continues.

## Acceptance Criteria

- `GET /api/exams/approvals?examId=<id>` returns only the requested approval row for admins.
- `/exam-approvals?examId=<id>` filters and highlights that row.
- Admin dashboard pending approval rows open `/exam-approvals?examId=<id>`.
- Quality gates fail if the approval queue loses scoped `examId` filtering or dashboard deep-linking.
