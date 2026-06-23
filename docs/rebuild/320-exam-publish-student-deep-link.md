# 320. Exam Publish Student Deep Link

## Background

Exam publish notifications were already idempotent and related to `EXAM_ATTEMPT + attemptId`, but the student-visible link still opened the generic exam center. Students had to manually find the newly published exam, and the notification audit relation was more precise than the actual click target.

## Change

- Add `ExamService.studentExamLink(attemptId)`.
- New exam publication notifications now link to `/student/exams?attemptId=<attemptId>`.
- Keep the notification relation unchanged:
  - `type = EXAM`
  - `related_type = EXAM_ATTEMPT`
  - `related_id = attemptId`
- Reuse the existing `ExamList` route hydration, which switches to the correct list tab and highlights the target attempt row.
- Extend quality gates so publication notifications must keep the exact attempt deep-link.

## Three-End Impact

- Student end: clicking a new-exam notification lands on and highlights the exact exam attempt.
- Teacher end: publishing behavior remains transactional and idempotent; only the student link becomes more precise.
- Admin end: notification audit relation and student click target now point to the same business object.

## Acceptance Criteria

- Publishing an exam sends `EXAM` notifications with `/student/exams?attemptId=<id>`.
- Notification rows remain related to `EXAM_ATTEMPT + attemptId`.
- `/student/exams?attemptId=<id>` focuses the matching row through existing route handling.
- Quality gates fail if the publish notification returns to the generic `/student/exams` link.
