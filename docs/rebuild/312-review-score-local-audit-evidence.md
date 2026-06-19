# 312. Review Score Local Audit Evidence

## Context

Manual review directly changes student scores. The backend already records `review_score_log` rows and `submitReview` returns `reviewScoreLogIds`; the review page also has a history drawer with copyable audit IDs. The remaining gap was the immediate operation result: after submitting a review, teachers only saw a transient message with the first few IDs.

## Changes

- `ReviewPanel` now stores the latest review-score audit evidence after `submitReview`.
- A local success banner shows the generated `reviewScoreLogIds`.
- The banner can copy:
  - all generated review score audit IDs
  - `/monitor/logs?reviewScoreLogId=<first-id>` deep link
- Existing review score history drawer, export, and per-row copy controls remain unchanged.
- `scripts/run-quality-gates.ps1` now verifies the local banner, copy helpers, and `submitReview` wiring.

## Three-End Collaboration Impact

- Teacher side: manual scoring has immediate, shareable audit evidence.
- Admin side: unified logs can open the exact review-score audit row from the submitted review result.
- Student side: score visibility remains governed by review completion and score release state; this change only improves traceability.

## Verification

- Static quality gates require `ReviewPanel` to call `rememberReviewScoreAudit('Submit review', response.data.reviewScoreLogIds || [])`.
- Existing review score audit list/export checks continue to verify the history drawer.
