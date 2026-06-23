# 269. Exam Publish Attempt Notification

## Background

Exam publication is the first student-facing step in the exam lifecycle. The previous publish notification was sent as an unscoped batch message, so administrators could not trace it to a concrete exam attempt and students could not be filtered by the same business object used by the exam-taking page.

## Change

- Replace unscoped `sendBatch` for new exam publication with per-student notifications.
- Resolve each student's first `exam_attempt` after candidate and attempt snapshots are created.
- Send the notification with:
  - `type = EXAM`
  - `related_type = EXAM_ATTEMPT`
  - `related_id = attemptId`
  - link `/student/exams?attemptId=<attemptId>`
- Keep the existing student-visible title and content.

## Three-End Impact

- Student end: new exam notifications are tied to the exact attempt that the student will enter.
- Teacher end: publishing still creates candidate snapshots, attempt records, and student notifications in one transaction.
- Admin end: notification audit can trace exam publication by `EXAM_ATTEMPT + attemptId`, matching monitor and score notifications.

## Acceptance Criteria

- Publishing an approved exam creates attempt records before notifications are sent.
- Each published candidate with an attempt receives an `EXAM` notification related to `EXAM_ATTEMPT`.
- The old unscoped `sendBatch("New exam")` path is not used for exam publication.
- Quality gates fail if the attempt lookup or related notification metadata is removed.

## Follow-Up

Later iterations can add notification counts to publish responses and approval logs, matching the score-release notification statistics.
