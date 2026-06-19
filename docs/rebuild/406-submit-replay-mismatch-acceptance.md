# 406 Submit Replay Mismatch Acceptance

## Scope
- Turn submit replay mismatch observability into an acceptance-tested workflow.
- Ensure disposable submit replay checks generate both mismatch outcomes before metrics smoke validates them.

## Scripts
- `scripts/verify-attempt-resilience.ps1` now performs three replay checks after the first accepted submit:
  - same token and same payload: normal idempotent replay
  - different token and same payload: `submitTokenMismatch`
  - same token and different payload: `submitPayloadMismatch`
- `scripts/check-exam-metrics-smoke.ps1` adds `-RequireSubmitReplayMismatchOutcomes`.
- The new metrics smoke switch requires:
  - `submitExam:replay_payload_mismatch`
  - `submitExam:replay_token_mismatch`

## Nightly
- `.github/workflows/nightly-acceptance.yml` enables `-RequireSubmitReplayMismatchOutcomes` when `submit_replay=true`.
- Normal non-destructive nightly runs still only require the safe operation metrics.
- Submit replay mode remains scoped to disposable fixtures.

## Why This Matters
- Backend metrics and student warnings are not enough unless acceptance runs prove the full path.
- This keeps idempotent retry behavior separate from suspicious or buggy retry behavior.
- The server still keeps the first accepted submission and still does not expose scores before release.

## Verification
- Quality gates check the verifier, metrics smoke script, workflow, and metrics smoke documentation.
- The metrics smoke script can also be validated offline with `-PrometheusFile`.
