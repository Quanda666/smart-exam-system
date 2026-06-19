# 298. Question Review Unified Audit

## Scope
- Add question review lifecycle logs to the unified administrator log workbench.
- Keep the existing per-question review log drawer in the question bank.
- Make question review evidence copyable by stable log ID and deep link.

## Backend
- Added administrator monitor endpoints:
  - `GET /api/monitor/question-review-logs`
  - `GET /api/monitor/question-review-logs/export`
- Reused the existing `question_review_log` table.
- Added paged search and CSV export in `QuestionBankService`.
- Supported filters:
  - log ID
  - question ID
  - keyword
  - action type
  - review status
  - subject ID
  - operator ID
  - operated time range

## Frontend
- Added `Question Review Audit` to `SystemLog.vue`.
- Added row-level copy actions:
  - copy question review audit ID
  - copy unified audit deep link
- Added route hydration:
  - `/monitor/logs?questionReviewLogId=<id>`
  - `/monitor/logs?tab=questionReview&logId=<id>`
- Added the same evidence copy actions to `QuestionBankPanel.vue` review log drawer.

## Collaboration Value
- Teachers can still inspect a single question's review timeline directly in the question bank.
- Administrators can audit all question creation, editing, review submission, approval, rejection, online/offline, and delete events from one system log workbench.
- A single copied evidence link can now connect a disputed question approval decision back to the global audit ledger.

## Verification
- Quality gates now check the backend monitor endpoints, `QuestionBankService` query/export implementation, frontend API, unified SystemLog tab, deep-link hydration, clipboard helpers, and local question-bank drawer evidence buttons.
