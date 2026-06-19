# 437 Snapshot Repair

## Scope
- Add a controlled repair path for lifecycle `SNAPSHOT_RISK`.
- Repair missing candidate and question snapshots after publication without touching answer content or score release state.

## Backend Changes
- `POST /api/exams/{id}/snapshot/repair`
  - Requires `ADMIN` or `TEACHER`.
  - Reuses exam ownership checks.
  - Allows only published or closed exams.
  - Rebuilds question snapshots from the exam paper.
  - Resolves current exam targets into active students.
  - Inserts missing candidate snapshots.
  - Inserts missing first attempts.
  - Records operation log `REPAIR_EXAM_SNAPSHOT`.

## Safety Boundary
- Does not delete existing candidate snapshots.
- Does not delete or change attempts, answers, review records, appeals, score release records, or monitor events.
- Does not notify students.
- Does not auto-publish scores.

## Frontend Changes
- Snapshot drawer adds `Repair Snapshot`.
- Lifecycle Health rows with snapshot blockers add `Repair`.
- After repair, the page refreshes:
  - snapshot drawer
  - exam list
  - lifecycle health
  - score safety
  - lifecycle handoff

## Verification
- Run fast quality gate.
- Run frontend build.
- Run full quality gate.
