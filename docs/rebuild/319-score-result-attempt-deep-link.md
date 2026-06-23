# 319. Score Result Attempt Deep Link

## Context

Score appeal notifications now deep-link students to the exact appeal lifecycle logs. Score release and score revoke notifications still pointed to the generic student results page, even though those notifications are already related to `EXAM_ATTEMPT + attemptId`.

Students should land on the relevant result record directly when a score is published or revoked.

## Changes

- Score release notifications now link to `/student/results?attemptId=<attemptId>`.
- Score revoke notifications now link to `/student/results?attemptId=<attemptId>`.
- Added `ExamService.studentResultLink(attemptId)` so release and revoke use the same link contract.
- `StudentResultsPanel` now reads `route.query.attemptId`.
- When `attemptId` is present and no more specific `appealId` is present, the student page:
  - loads grades
  - finds the matching attempt row
  - opens the result detail when the score is currently visible
  - otherwise shows the existing visibility/revoke warning for that attempt
- Quality gates now protect the backend notification link and frontend `attemptId` route hydration.

## Three-End Coordination

- Teacher side: publishing or revoking scores sends precise result links.
- Student side: clicking the notification opens the relevant result instead of leaving the student to search the table.
- Administrator side: notification audit still uses `relatedType = EXAM_ATTEMPT` and `relatedId = attemptId`, matching the deep link target.

## Acceptance Notes

- Publishing scores notifies students with a URL containing `attemptId`.
- Revoking scores notifies students with a URL containing `attemptId`.
- Opening `/student/results?attemptId=<id>` as the owning student focuses that attempt.
- `appealId` remains higher priority than `attemptId` when both are present, because appeal logs are more specific.
