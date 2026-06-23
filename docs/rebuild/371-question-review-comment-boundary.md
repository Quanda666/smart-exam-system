# 371. Question Review Comment Boundary

## Background

Question review comments are part of the question evidence chain. They are stored on the current
question row as `question.review_comment` and also in `question_review_log.comment` for teacher-side
history and administrator audit exports. The service previously used silent `truncate(..., 500)` calls,
which could hide part of a reviewer decision and make the business row differ from the audit trail.

## Changes

- Added `MAX_QUESTION_REVIEW_COMMENT_LENGTH = 500` in `QuestionBankService`.
- Added `normalizeOptionalQuestionReviewComment` for approval comments.
- Added `normalizeRequiredQuestionReviewComment` for rejection comments.
- Approval and rejection now write the same normalized comment to `question.review_comment` and
  `question_review_log.comment`.
- `recordQuestionLog` now rejects overlong comments instead of silently truncating them.
- The quality gate now blocks `truncate(blankToNull(comment), 500)` in question review evidence paths.

## Three-Terminal Impact

- Teacher terminal: reviewers get explicit validation when an approval or rejection comment is too long.
- Teacher terminal: rejection still requires a non-empty review reason before the question returns to the
  creator.
- Administrator terminal: question review audit search and export preserve the same decision text that
  was written to the question row.
- Student terminal: students do not see this comment directly, but published exams depend on reviewed
  and approved questions whose audit trail is now more trustworthy.

## Acceptance

- Overlong approval comments are rejected before updating `question.review_comment`.
- Overlong rejection comments are rejected before updating `question.review_comment`.
- Overlong question review log comments are rejected before inserting `question_review_log`.
- Rejection with an empty or whitespace-only comment is still rejected.
- Full quality gates must pass after this step.
