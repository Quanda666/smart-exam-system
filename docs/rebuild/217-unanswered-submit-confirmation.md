# 217. Unanswered Submit Confirmation

## Goal

Reduce accidental manual submissions when a student still has unanswered questions. The final submit payload already includes every question, and the backend records unanswered questions, but the student needed a clearer last check.

## Scope

- Add `unansweredCount` to the exam-taking page.
- Show the unanswered count in the manual submit confirmation dialog.
- Keep timeout auto-submit behavior unchanged.
- Keep backend answer-record finalization as the authoritative scoring and audit path.
- Add a quality-gate anchor for the unanswered-question warning.

## Three-End Coordination

- Student end: manual submit now warns when unanswered questions remain.
- Teacher end: review and score statistics still receive the backend `unansweredCount`.
- Admin/audit end: answer records remain generated through the backend submit finalization flow.

## Acceptance Notes

- The confirmation dialog says `There are N unanswered questions. Submit anyway?` when `N > 0`.
- The normal confirmation is still shown when all questions are answered.
- `normalizeFinalAnswers` continues to emit every question with an empty string for unanswered items.
- Backend submit finalization still upserts `answer_record` for each paper question.
