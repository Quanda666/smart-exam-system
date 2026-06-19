# 436 Snapshot Evidence Export Audit

## Scope
- Make snapshot evidence downloads auditable.
- Add a direct operator path from the snapshot drawer to the matching operation log records.

## Backend Changes
- `GET /api/exams/{id}/snapshot/export` now records an operation log:
  - action: `EXPORT_EXAM_SNAPSHOT`
  - target: `EXAM#{id}`
  - detail: states that the export excludes student answers, correct answers, analysis, stems, option content, and unreleased scores
- Logging remains non-blocking because `OperationLogService.record` already ignores log write failures.

## Frontend Changes
- The snapshot drawer now has `Audit Logs`.
- The button opens `/monitor/logs` with operation filters:
  - `tab=operation`
  - `action=EXPORT_EXAM_SNAPSHOT`
  - `target=EXAM#{id}`
- `SystemLog.vue` now hydrates operation `action`, `target`, and `keyword` filters from route query.

## Safety Notes
- Export behavior stays unchanged from 435.
- The audit log records that a safe evidence export happened; it does not store exported file content.

## Verification
- Run fast quality gate.
- Run frontend build.
- Run full quality gate.
