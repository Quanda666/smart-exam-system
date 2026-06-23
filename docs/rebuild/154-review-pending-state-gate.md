# 154 Review Pending State Gate

## Scope

This batch tightens the review workflow state machine. Teachers can only open review details or submit review scores for attempts that are currently in pending-review state.

## Changes

- Added `ReviewService.requireAttemptPendingReview`.
- `getReviewDetails` now rejects attempts whose `exam_attempt.status` is not `4`.
- `submitReview` now rejects attempts whose `exam_attempt.status` is not `4` before applying score changes.
- Added a quality gate so review logic cannot rely only on `answer_record.review_status = 0`.

## Why This Matters

The review workflow should be driven by the attempt state machine, not just by loose answer-row flags. Without this gate, direct API calls or inconsistent data could let a teacher modify scores for an attempt that is not actively pending review.

This keeps normal review and recheck review inside the same explicit `PENDING_REVIEW -> FINALIZED` transition.

## Verification

- Java source hygiene
- Frontend source hygiene
- PowerShell parser smoke
- Local quality gates

