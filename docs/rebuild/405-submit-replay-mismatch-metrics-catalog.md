# 405 Submit Replay Mismatch Metrics Catalog

## Scope
- Keep the exam business metrics catalog aligned with submit replay mismatch observability.
- Make the new outcomes discoverable for teacher, administrator, and operations dashboards.

## Documentation
- `docs/rebuild/43-exam-business-metrics.md` now lists `submitExam` outcomes:
  - `replay_payload_mismatch`
  - `replay_token_mismatch`

## Why This Matters
- Metrics are only useful if dashboards, acceptance checks, and runbooks know the exact tag values.
- Payload mismatch and token mismatch replays are different from ordinary idempotent submit retries.
- The catalog keeps the operational vocabulary stable while the backend continues to keep the first accepted submission.

## Verification
- `scripts/run-quality-gates.ps1` checks both `ExamOperationMetricsAspect` and the metrics catalog for the replay mismatch outcomes.
