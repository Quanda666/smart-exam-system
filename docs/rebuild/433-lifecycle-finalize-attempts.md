# 433 Lifecycle Finalize Attempts

## Scope

- Close the `FINALIZE_REQUIRED` lifecycle blocker from the Lifecycle Health workbench.
- Let operators jump from lifecycle risk rows to the exam snapshot and force-submit active attempts.
- Add batch force-submit for active attempts inside the snapshot drawer.

## Frontend

- `ExamManagement.vue` Lifecycle Health row actions now show `Finalize` when a row has:
  - `FINALIZE_REQUIRED`
  - `ACTIVE_ATTEMPTS`
  - `NON_FINAL_ATTEMPTS`
  - `DEADLINE_PASSED_ACTIVE`
  - active attempts or deadline-passed active attempts
- The `Finalize` action opens the existing exam snapshot drawer.
- The snapshot drawer now shows the number of active attempts.
- The snapshot drawer adds `Force Submit Active`.
- Batch force-submit:
  - asks for confirmation once
  - calls the existing `forceSubmitAttempt(attemptId)` API for each active attempt
  - reports successful and failed attempts separately
  - refreshes snapshot, exam list, lifecycle health, score safety, and handoff data when visible

## Backend Contract

- No new state-changing backend API was introduced.
- Every forced submission still calls the existing backend `POST /api/exams/attempt/{attemptId}/force-submit`.
- Backend remains authoritative for:
  - attempt ownership/scope
  - attempt state
  - draft recovery and unanswered-question completion
  - submit idempotency and finalization rules

## Coordination Impact

- Administrators and teachers can clear active/deadline-passed attempts from lifecycle health without manually hunting through the monitor or snapshot views.
- Once active attempts are finalized, the same workbench can proceed to review or score release readiness.
- This improves the chain: lifecycle risk -> snapshot evidence -> forced finalization -> refreshed lifecycle/readiness.

## Safety Invariants

- Forced submission remains explicit and confirmed.
- Batch force-submit does not create a new bypass around attempt finalization.
- Student answer content, correct answers, analysis, and unreleased scores are not exposed by lifecycle health.
- Failed forced submissions do not block successful ones from being reported and refreshed.

## Acceptance Checks

- Lifecycle Health rows with finalization blockers show `Finalize`.
- `Finalize` opens the snapshot drawer for that exam.
- Snapshot drawer shows active attempt count.
- `Force Submit Active` is disabled when there are no active attempts.
- Batch force-submit refreshes snapshot and lifecycle health data.
- Frontend build passes.
- Full quality gate passes.
