# 370. Review Comment Boundary

## Background

Manual review comments are part of the grading evidence chain. The same text is written to
`review_record.comment` and `review_score_log.comment`, then shown in teacher review history and
administrator audit exports. DTO validation already declares a 1000-character limit, but service-layer
callers could still bypass controller validation and write raw comments.

## Changes

- Added `MAX_REVIEW_COMMENT_LENGTH = 1000` in `ReviewService`.
- Added `normalizeReviewComment` to trim optional review comments and reject overlong values.
- `submitReview` now normalizes each comment before any write.
- `review_record` and `review_score_log` now receive the same normalized comment value.
- The quality gate now blocks direct writes of `review.getComment()` to review evidence tables.

## Three-Terminal Impact

- Teacher terminal: manual review comments keep a stable 1000-character boundary even for internal
  service calls.
- Administrator terminal: review score audit logs and exports use the same normalized comment as the
  business review record.
- Student terminal: released score evidence is less likely to diverge between review details and audit
  history after appeals or rechecks.

## Acceptance

- Overlong review comments are rejected before inserting `review_record`.
- Overlong review comments are rejected before inserting `review_score_log`.
- Empty or whitespace-only comments remain optional and are stored as an empty normalized value.
- Full quality gates must pass after this step.
