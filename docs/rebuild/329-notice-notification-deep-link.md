# Batch 329: Notice Notification Deep Link

## Background

Basic notices are part of the three-terminal communication loop. Before this batch, a published notice sent `NOTICE` notifications to the right users, but the link only opened the generic basic data page. Recipients had to find the notice manually, and notification audit had no business relation to the notice row.

## Changes

- Notice notifications now link to `/basic/notices?noticeId=<id>`.
- Notice notifications are related to `NOTICE + noticeId`.
- The notice list API accepts optional `noticeId` filtering and still applies the existing visibility check.
- `BasicDataPanel` reads `route.query.noticeId`, loads the exact notice, and highlights the matched row.

## Three-Terminal Collaboration

- Admin/teacher terminal: publishing a notice creates precise recipient notifications.
- Student/teacher/admin recipients: clicking the notification lands on the exact notice row.
- Admin audit: notification relation and click target now point to the same business object.

## Verification

Local quality gates assert the backend relation/link contract and the frontend row focus behavior.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
