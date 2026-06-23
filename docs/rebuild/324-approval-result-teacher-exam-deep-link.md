# 324. Approval Result Teacher Exam Deep Link

## Background

Admin approval queue entries can now deep-link to exact approval rows, and exam management can focus an exam by `examId`. However, approval result notifications sent to teachers still used the old generic `/exam/tasks` path. That path does not match the current frontend route and also loses the exact exam context.

## Change

- Add `ExamService.teacherExamLink(examId)`.
- Approval success notifications now link to `/exam-tasks?examId=<examId>`.
- Approval rejection notifications now link to `/exam-tasks?examId=<examId>`.
- Keep notification audit relation unchanged:
  - `type = EXAM_APPROVAL`
  - `related_type = EXAM`
  - `related_id = examId`
- Extend quality gates to reject the old `/exam/tasks` path and require exact teacher exam links.

## Three-End Impact

- Admin end: after approve/reject, notification audit still points to `EXAM + examId`.
- Teacher end: clicking an approval result notification lands on the exact exam row in exam management.
- Student end: no behavior change; only approved exams continue into the publish snapshot and student notification workflow.

## Acceptance Criteria

- Approving an exam notifies the creator with `/exam-tasks?examId=<id>`.
- Rejecting an exam notifies the creator with `/exam-tasks?examId=<id>`.
- The notification remains related to `EXAM + examId`.
- Quality gates fail if approval result notifications use `/exam/tasks` or lose the exact exam link.
