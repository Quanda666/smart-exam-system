# 242. Student Result Answer Stats Expected Set

## Goal

Make student-facing answer-completeness statistics use the same authoritative question set as monitor, score exports, and teacher insight. Students should not see inflated answered counts caused by stale or out-of-scope `answer_record` rows.

## Scope

- Update `StudentService.getGrades` so `answeredCount` counts distinct answered question IDs inside `exam_question_snapshot`.
- Update `StudentService.getExamResult` with the same scoped answered-count logic.
- Fall back to `paper_question` only for legacy exams without question snapshots.
- Make `unansweredCount` subtract the scoped answered count from the same snapshot-first total.
- Add quality-gate anchors for expected-set joins and distinct answer counting.

## Three-End Coordination

- Student end: result list and result detail now align with the published exam question set.
- Teacher end: student insight, monitor, and review surfaces continue to share the same answer-completeness vocabulary.
- Admin/audit end: score exports and student-visible context now agree on scoped answer counts.

## Acceptance Notes

- Score visibility gates are unchanged: unreleased, revoked, unscored, non-final, and open-recheck results remain protected.
- Result detail still uses the full expected question set with missing answers shown as empty.
- Current-paper fallback remains only for exams without a question snapshot.
