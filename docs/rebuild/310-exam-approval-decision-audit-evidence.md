# 310. Exam Approval Decision Audit Evidence

## Context

Exam approval is the gate between teacher-side publishing and student-side exam access. Approve and reject actions already wrote `exam_approval_log`, but the decision response did not expose the exact approval log ID. Administrators had to reopen the log drawer or search unified system logs to prove the just-finished decision.

## Changes

- `ExamService.recordApprovalLog` now returns the inserted approval log ID.
- `approveExam` returns `approvalLogId` after recording the `APPROVE` log.
- `rejectExam` returns `approvalLogId` after recording the `REJECT` log.
- `frontend/src/api/exam.ts` exposes `approvalLogId` on `ExamInfo`.
- `ExamApprovalQueue` shows a local success audit banner after approve/reject decisions.
- The banner can copy:
  - the approval audit ID
  - `/monitor/logs?examApprovalLogId=<id>` deep link
- `scripts/run-quality-gates.ps1` now verifies the backend log ID return path, frontend type, queue banner, and clipboard wiring.

## Three-End Collaboration Impact

- Admin side: approval decisions now produce immediate copyable evidence without reopening the drawer.
- Teacher side: approval/rejection notifications remain tied to the exam workflow, while the admin can separately prove who made the decision.
- Student side: approved exams still publish through the existing snapshot and notification path; rejected exams remain invisible to students.

## Verification

- Static quality gates require approve/reject responses to carry `approvalLogId`.
- The existing row-level approval status checks remain unchanged: only pending exams can be approved or rejected.
