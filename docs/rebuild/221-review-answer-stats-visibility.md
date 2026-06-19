# 221. Review Answer Stats Visibility

## Goal

Show teachers the submitted answer completeness while reviewing. Monitor sessions already expose answered and unanswered counts; review worklists and detail drawers should carry the same context.

## Scope

- Add `questionCount`, `answeredCount`, and `unansweredCount` to pending review rows.
- Add the same fields to review detail responses.
- Extend frontend review API types.
- Show answer statistics in the pending review table.
- Show answer statistics in the review drawer header.
- Add quality-gate anchors for backend, API, and UI coverage.

## Three-End Coordination

- Teacher end: reviewers can see whole-paper completeness before and during scoring.
- Student end: no behavior change; stats still come from persisted answer records.
- Admin/audit end: the counts remain read-only context derived from `paper_question` and `answer_record`.

## Acceptance Notes

- Pending review rows expose submitted answer statistics.
- Review details expose the same statistics.
- The UI falls back to `-` when older responses lack stats.
- Existing review score validation and coverage rules remain unchanged.
