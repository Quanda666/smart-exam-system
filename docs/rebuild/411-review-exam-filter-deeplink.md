# 411 Review Exam Filter Deep Link

## Scope
- Add exam-level filtering to the teacher/admin pending review queue.
- Link the review progress table to the pending review queue through `reviewExamId`.
- Keep appeal filters and review filters independent on the same workbench.

## Backend
- `GET /api/reviews/pending` now accepts optional `examId`.
- The pending review response includes `examId`.
- The service validates `examId` before database access and still applies teaching-scope checks.
- The pending queue keeps the same snapshot-first expected answer scope used by review progress and review submission.

## Teacher UI
- `ReviewPanel` reads `reviewExamId` from the route query.
- `Review progress` rows provide a `Focus` action that sets `reviewExamId`.
- The pending review table uses the same `reviewExamId` to show only one exam's pending attempts.
- The queue toolbar shows the active exam filter and provides `Clear filter`.

## Coordination
- Teachers can move from exam-level blockers to the exact pending queue without scanning unrelated exams.
- Score release blockers such as `PENDING_REVIEW` and `PENDING_REVIEW_ANSWERS` are easier to resolve from the review workbench.
- Existing `appealId`, `appealStatus`, and `appealHandlingResult` query filters remain separate from `reviewExamId`.

## Verification
- `scripts/run-quality-gates.ps1` checks backend pending-review filtering, frontend API query support, route parsing, focus/clear actions, and filtered progress/pending loading.
