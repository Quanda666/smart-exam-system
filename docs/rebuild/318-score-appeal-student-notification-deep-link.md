# 318. Score Appeal Student Notification Deep Link

## Context

Students can inspect and export the lifecycle logs for their own score appeals. The remaining coordination gap was notification routing: when teachers replied to an appeal or closed a recheck, the student notification still linked only to `/student/results`. Students then had to manually find the relevant exam, appeal row, and log drawer.

A production dispute workflow should take the student directly to the exact appeal evidence from the notification.

## Changes

- Score appeal reply notifications now link to `/student/results?appealId=<id>`.
- Score appeal recheck-completed notifications now link to `/student/results?appealId=<id>`.
- `StudentResultsPanel` now reads `route.query.appealId`.
- When `appealId` is present, the student page:
  - loads grades and appeals
  - finds the owned appeal
  - opens the matching exam result drawer
  - opens the score appeal lifecycle log drawer for that appeal
- App session restore now preserves `route.fullPath`, so refresh/deep-link entry keeps the `appealId` query.
- Quality gates now protect the backend notification link, frontend route hydration, and App query preservation.

## Three-End Coordination

- Teacher side: appeal handling actions continue to create lifecycle logs and now send precise student links.
- Student side: clicking a notification lands on the exact appeal log trail instead of a generic results page.
- Administrator side: copied log IDs and deep links still resolve in the global score appeal audit view.

## Acceptance Notes

- Replying to a score appeal sends a notification with `appealId`.
- Closing a recheck-required appeal sends a notification with `appealId`.
- Opening `/student/results?appealId=<id>` as the owning student opens the related result and lifecycle log drawer.
- If the appeal is missing or not owned by the student, the page shows a warning and does not expose logs.
