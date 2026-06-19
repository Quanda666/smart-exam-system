# 248. Student Overview Wrong Count Snapshot Identity

## Goal

Align the student dashboard wrong-question count with the wrong-book snapshot identity introduced in batch 247. The dashboard should count released wrong-question records by exam snapshot, not collapse reused question-bank rows.

## Scope

- Audit the student result appeal matching path; it already filters current appeals by `attemptId`.
- Update `OverviewService.studentOverview` wrong-question count from `COUNT(DISTINCT question_id)` to `COUNT(DISTINCT exam_id + question_id)`.
- Preserve existing score visibility constraints: released scores only, finalized attempts only, scored attempts only, and no open recheck appeal.
- Add a quality-gate anchor so overview wrong-question counts cannot regress to mutable question-bank identity.

## Three-End Coordination

- Student end: dashboard wrong-question totals now agree with the wrong-book rows.
- Teacher end: reused or edited question-bank items no longer hide repeated wrong evidence across separate exams.
- Admin/audit end: learning-feedback statistics remain aligned with score release and recheck visibility rules.

## Acceptance Notes

- This batch changes only the dashboard aggregate count.
- Wrong-book list/detail and AI explanation behavior remain as implemented in batch 247.
- Legacy exams still count by their exam/question pair even when no question snapshot exists.
