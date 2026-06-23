# 435 Snapshot Evidence Export

## Scope
- Add an exam snapshot evidence export for administrators and teachers.
- Keep the export focused on audit evidence, not answer disclosure.
- Reuse the existing snapshot ownership checks and CSV download pipeline.

## Backend Changes
- `GET /api/exams/{id}/snapshot/export`
  - Requires `ADMIN` or `TEACHER`.
  - Reuses `requireOwnedExam` before exporting.
  - Exports a single CSV evidence package with:
    - exam metadata
    - target summary
    - candidate snapshot rows
    - question structure snapshot rows

## Safety Boundary
- The export intentionally excludes:
  - student answers
  - correct answers
  - analysis
  - question stems
  - option content
  - unreleased scores
- Candidate rows include only identity, source, latest attempt status, submit type, and active attempt id.
- Question rows include only question id, type, order, score, option count, and snapshot time.

## Frontend Changes
- The exam snapshot drawer now has `Export Evidence`.
- The button downloads the guarded backend CSV and shows loading/success/failure states.

## Verification
- Run fast quality gate.
- Run frontend build.
- Run full quality gate.
