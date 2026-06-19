# 265. Score Appeal Teacher Notification

## Background

Score appeal is a cross-end workflow. Students can submit appeals after scores are released, but the workflow is incomplete if teachers only discover them by manually refreshing the review page. A real exam system must turn student submissions into teacher workbench tasks.

## Change

- Include the exam owner in the score-appeal submission query.
- After the appeal row and audit log are written, notify the exam owner.
- The notification uses:
  - `type = SCORE_APPEAL`
  - `related_type = SCORE_APPEAL`
  - `related_id = appealId`
  - link `/reviews?appealStatus=0&appealHandlingResult=ALL`
- The notification content names the exam, student, and appeal target.
- If the exam owner is missing or is the same as the submitting student, no notification is sent.

## Three-End Impact

- Student end: submitting an appeal now reliably creates a teacher-side handling signal.
- Teacher end: pending appeals surface through notifications and deep-link into the appeal workbench filter.
- Admin end: notification audit can trace appeal reminders by `SCORE_APPEAL` relation.

## Acceptance Criteria

- Submitting an eligible score appeal still requires released scores, ownership, an open appeal window, and no overlapping active appeal.
- A successful new appeal sends one related notification to the exam owner.
- Teacher notification opens the pending appeal workbench.
- Quality gates fail if the owner lookup, notification helper, link, or related business key is removed.

## Follow-Up

Later iterations should fan out appeal notifications to all authorized co-teachers when teaching assignments support multiple responsible teachers per exam.
