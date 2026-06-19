# 407 Metrics Smoke Failure Artifact

## Scope
- Make exam metrics smoke failures diagnosable from CI artifacts.
- Preserve observed operation/outcome samples even when required metrics are missing.

## Scripts
- `scripts/check-exam-metrics-smoke.ps1` now writes a `success=false` result before failing when:
  - required operation arguments are empty without `-AllowEmptyRequirements`
  - the Prometheus file is missing
  - `/actuator/prometheus` cannot be fetched
  - Prometheus text exposition markers are missing
  - no exam operation metrics are found
  - required operations are missing
  - required operation outcomes are missing
  - required operation outcome arguments use an invalid format
- The result now includes:
  - `missingOperations`
  - `missingOperationOutcomes`
  - `invalidRequiredOperationOutcomes`
  - `failureReason`
  - observed operations and outcomes collected before the failure
- `scripts/write-acceptance-summary.ps1` displays the metrics failure reason and missing requirements in the nightly Markdown summary.

## Why This Matters
- Nightly submit replay mode now requires replay mismatch outcomes.
- If a future regression removes those metrics, the artifact should show exactly which outcome disappeared.
- This keeps operational acceptance useful without requiring someone to search raw backend logs first.

## Verification
- Offline Prometheus samples verify both success and missing-outcome failure paths.
- `scripts/run-quality-gates.ps1` checks the failure artifact fields, summary rendering, and documentation.
