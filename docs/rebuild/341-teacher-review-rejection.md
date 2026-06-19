# 341 Teacher Review Rejection

## Problem
The teacher registration review workflow only supported approval. Administrators could enable a pending teacher, but they had no explicit way to reject an invalid registration, record the reason, or remove it from the pending review queue without deleting or disabling the account manually.

## Changes
- Added `PUT /api/system/users/{id}/teacher-review/reject`.
- Added `RejectTeacherReviewRequest` with a required rejection reason.
- Extended teacher profile review status usage:
  - `0`: pending review
  - `1`: approved
  - `2`: rejected
- Rejection is only allowed for pending teacher profiles.
- Rejection keeps the user account inactive, sets `teacher_profile.status = 2`, revokes sessions, and sends an `ACCOUNT_REJECTED` notification related to the user.
- User management now:
  - shows pending teachers as `待审核`;
  - shows rejected teachers as `已驳回`;
  - shows `驳回` only for pending teachers;
  - still allows administrators to approve a rejected teacher later via `审核通过`.
- Operation log IDs are returned and surfaced through the existing audit prompt.

## Workflow
1. Teacher self-registers and enters `teacher_profile.status = 0`.
2. Administrator opens the pending teacher queue.
3. Administrator either approves the teacher or enters a rejection reason.
4. Rejected teachers leave the pending queue and are visible as rejected records.
5. A later administrator can still approve the rejected teacher if the registration is corrected.

## Validation
- Quality gate source checks assert the reject endpoint, DTO validation, status transition, notification type, frontend action, and rejected-state display.
