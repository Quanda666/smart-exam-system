# 432 Lifecycle Score Ready Publish

## Scope

- Close the loop from lifecycle health discovery to score publication handling.
- Add score publication actions inside the Lifecycle Health workbench for exams that are already server-marked as score-release ready.
- Keep publication authoritative on the existing backend `publishScores` guard.

## Frontend

- `ExamManagement.vue` Lifecycle Health drawer adds:
  - `Publish Ready` toolbar button.
  - Row-level `Publish` button for ready exams.
- `Publish Ready` only publishes ready exams on the current Lifecycle Health page.
- Batch publish:
  - asks for one confirmation
  - calls the existing `publishExamScores(examId)` endpoint for each ready exam
  - collects successes and failures independently
  - refreshes exam list, lifecycle health rows, score safety rows, and handoff data when visible
- Row-level publish still uses the existing readiness drawer/check flow through `publishScores(...)`.

## Backend Contract

- No new state-changing backend path was introduced.
- Every publication still calls `POST /api/exams/{id}/scores/publish`.
- Backend still enforces:
  - exam ownership/scope
  - score release transition lock
  - exam ended
  - no active or non-final attempts
  - no pending review
  - no pending answer review
  - no pending appeal or open recheck
  - no unscored completed attempts
  - at least one scored completed attempt
  - not already published

## Coordination Impact

- Teachers can handle several ready exams from the lifecycle workbench without jumping between tables.
- Administrators can clear global ready-score queues faster while preserving per-exam audit logs.
- Students still see scores only after the normal score release path succeeds and notifications are sent.

## Safety Invariants

- Batch publish does not bypass backend score-release guards.
- Failed exams stay failed and are reported without blocking successfully published exams.
- No student answers, correct answers, analysis, or unreleased raw scores are exposed.
- Publication still creates the existing score release audit log and student notifications per exam.

## Acceptance Checks

- Lifecycle Health drawer shows `Publish Ready` when the current page has ready exams.
- Row-level `Publish` appears only on ready rows.
- Batch publish confirms once and publishes each ready row through the existing API.
- Success and failure counts are shown after batch handling.
- Workbench data refreshes after publish.
- Frontend build passes.
- Full quality gate passes.
