# 412 Review Recheck Task Filter

## Scope
- Distinguish score-appeal recheck review tasks from standard pending review tasks.
- Let teachers filter the pending review queue by task type.
- Surface recheck backlog in the exam-level review progress table.

## Backend
- `GET /api/reviews/pending` accepts optional `reviewType=RECHECK|STANDARD`.
- Pending review rows now include:
  - `recheckTaskCount`
  - `recheckRequired`
  - `recheckAppealCount`
- Recheck tasks are inferred from active score appeals:
  - `score_appeal.status = 1`
  - `score_appeal.handling_result = 'RECHECK_REQUIRED'`
  - same attempt, and either all questions or the matching appealed question
- `GET /api/reviews/progress` includes `pendingRecheckAnswerCount`, `firstRecheckAttemptId`, and `recheckAppealCount`.

## Teacher UI
- `ReviewPanel` adds an `All / Recheck / Standard` task filter.
- The filter is stored in `reviewTaskType` route query, separate from `reviewExamId` and appeal filters.
- Pending review rows show `Recheck` or `Standard` labels.
- When the filter is `Recheck`, the progress table's `Review` action prefers `firstRecheckAttemptId`.

## Coordination
- A teacher handling a `RECHECK_REQUIRED` appeal can now identify the reopened review task without scanning ordinary pending reviews.
- Score release readiness blockers caused by open recheck work can be resolved from the same review workbench.
- Existing appeal deep links continue to use `appealId`, `appealStatus`, and `appealHandlingResult`.

## Verification
- `scripts/run-quality-gates.ps1` checks backend task classification, `reviewType` filtering, frontend API parameters, route state, UI labels, and preferred recheck navigation.
