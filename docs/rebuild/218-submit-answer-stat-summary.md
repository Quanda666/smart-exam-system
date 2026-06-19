# 218. Submit Answer Stat Summary

## Goal

After submission, show students the answer statistics confirmed by the backend. The previous step warned before manual submission, but the success feedback did not show what the server actually accepted.

## Scope

- Add a `submitResultSummary` helper in `ExamTaking.vue`.
- Read `questionCount`, `answeredCount`, and `unansweredCount` from the submit response.
- Show a non-blocking info message after successful submit when all stats are present.
- Keep the existing success, timeout, and replay submission messages unchanged.
- Add quality-gate anchors for the backend-stat summary.

## Three-End Coordination

- Student end: students see the final server-accepted answered/unanswered counts.
- Teacher end: the same backend counts continue to feed review and score analysis.
- Admin/audit end: the summary is display-only; authoritative answer records remain in `answer_record`.

## Acceptance Notes

- The page shows `Server accepted answers: X/Y answered, Z unanswered.` when submit stats are returned.
- Missing stats do not block submission or produce a misleading message.
- Existing submit cleanup, draft cleanup, monitor upload retry, and submit-token behavior are unchanged.
