# Batch 328: Teacher Score Appeal Notification Deep Link

## Background

When a student submits a score appeal, teacher notifications were already related to `SCORE_APPEAL + appealId`. The click target still opened the generic review workbench filtered to pending appeals, which could hide the target after another teacher handled it or force the teacher to search manually.

## Changes

- New score appeal notifications to teachers now link to `/reviews?appealId=<id>`.
- The review appeal list API accepts an optional `appealId` filter while still applying teaching-scope access checks.
- `ReviewPanel` reads `route.query.appealId`, loads the exact appeal independently of status/handling filters, and highlights the matched appeal row.
- Existing student appeal result notifications continue to link to `/student/results?appealId=<id>`.

## Three-Terminal Collaboration

- Student terminal: submitting an appeal creates the appeal and its audit log.
- Teacher terminal: the notification lands directly on the exact appeal row, so the teacher can handle it or inspect logs immediately.
- Admin terminal: global score appeal audit links remain available through the system log page.

## Verification

Local quality gates assert that:

- Teacher score appeal notifications use `teacherAppealLink(appealId)`.
- The controller/service/API accept exact `appealId` filtering.
- `ReviewPanel` consumes `appealId` and highlights the target row.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1
```
