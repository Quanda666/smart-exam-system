# 245. Score Appeal Recheck Expected Set

## Goal

Keep score-appeal recheck tasks inside the authoritative exam question set. A student appeal or teacher recheck must not reopen stale, forged, or out-of-scope `answer_record` rows.

## Scope

- Validate a question-specific appeal against the expected question set before accepting it.
- Reopen a question-specific recheck only when the answer belongs to the exam snapshot, with `paper_question` fallback only for legacy exams without snapshots.
- Reopen whole-attempt rechecks only for expected fill-blank or subjective answers.
- Remove the old full-answer fallback that could reopen every answer record on an attempt.
- Count pending recheck answers with the same expected-set condition before allowing appeal closure.
- Add quality-gate anchors for the expected-set recheck contract.

## Three-End Coordination

- Student end: a score appeal can no longer target a dirty answer row that is outside the real exam paper.
- Teacher end: recheck-required appeals create review work only for answers that belong to the released exam snapshot.
- Admin/audit end: open recheck blockers now reflect real recheck work instead of historical or forged answer rows.

## Acceptance Notes

- Snapshot-backed exams use `exam_question_snapshot` as the authoritative membership source.
- Legacy exams without snapshots continue to fall back to `paper_question`.
- Whole-attempt recheck remains focused on manually reviewable fill-blank and subjective answers.
- Objective-only disputes should be handled with structured appeal outcomes instead of reopening unrelated answer records.
