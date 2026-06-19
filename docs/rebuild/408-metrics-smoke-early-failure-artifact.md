# 408 Metrics Smoke Early Failure Artifact

## Scope
- Extend metrics smoke failure artifacts to early setup and fetch failures.
- Keep nightly diagnostics useful even when Prometheus cannot be read successfully.

## Scripts
- `scripts/check-exam-metrics-smoke.ps1` adds `Fail-WithMetricsResult`.
- Early failures now write a `success=false` JSON result before exiting:
  - no required operation/outcome was provided without `-AllowEmptyRequirements`
  - `-PrometheusFile` points to a missing file
  - `/actuator/prometheus` request fails
  - `/actuator/prometheus` returns a non-2xx status
  - the response is not Prometheus text exposition

## Why This Matters
- Metrics acceptance can fail before any metric parsing happens.
- CI artifacts should still show the failure reason instead of leaving the summary with a missing metrics file.
- This makes production-like acceptance easier to triage when the backend is down, Actuator is misconfigured, or Prometheus export is disabled.

## Verification
- Offline smoke checks cover missing-file and invalid-Prometheus failure artifacts.
- `scripts/run-quality-gates.ps1` checks the early-failure artifact contract.
