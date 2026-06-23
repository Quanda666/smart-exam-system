# 294. Exam Approval Unified Audit

Exam approval logs were visible inside exam-management drawers and could be exported per exam, but administrators could not search approval evidence from the unified system log page or deep-link to a specific approval log id. This left approval lifecycle evidence weaker than score release, score appeal, and review score audits.

## Changes

- Added administrator audit APIs:
  - `GET /api/monitor/exam-approval-logs`
  - `GET /api/monitor/exam-approval-logs/export`
- Added filters for:
  - exact approval log id
  - keyword across exam, paper, actor, and note
  - approval action
  - created time range
- Added a unified `Exam Approval Audit` tab to the system log page.
- Added copyable approval audit IDs and deep links:
  - `/monitor/logs?examApprovalLogId=<id>`
- Added approval audit actions in local approval log drawers:
  - exam management approval record drawer
  - approval queue record drawer
- Extended quality gates for backend routes, service filters, frontend API, unified audit page, clipboard helpers, and local drawer deep links.

## Three-End Impact

- Admin end: approval decisions can now be traced from local drawers into a unified audit page and exported across exams.
- Teacher end: scoped approval history remains visible in exam management; admin-only unified audit links are hidden from teacher-only views.
- Student end: no direct UI change, but exam publication governance becomes easier to audit after disputes or SLA reviews.

## Acceptance

- An administrator can open `/monitor/logs?examApprovalLogId=<id>` and land on the exact approval audit record.
- The unified audit page can search and export approval lifecycle logs.
- Local approval record drawers expose the raw log id and admin audit/deep-link controls.
- Approval publish notification counters remain visible in both local drawers and unified audit results.
