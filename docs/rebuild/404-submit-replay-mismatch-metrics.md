# 404 Submit Replay Mismatch Metrics

## Scope
- Expose abnormal repeated submit replays as backend operation metric outcomes.
- Keep student-facing replay warnings aligned with operations-side observability.

## Backend
- `ExamOperationMetricsAspect.submitOutcome` now classifies submitted-attempt replays with mismatched answer payloads as `replay_payload_mismatch`.
- Submitted-attempt replays with mismatched submit tokens are classified as `replay_token_mismatch`.
- These mismatch outcomes are evaluated before the generic `replayed` and `already_submitted` outcomes, so monitoring can distinguish suspicious or buggy retries from normal idempotent retry traffic.

## Why This Matters
- The student UI already warns when a retry sends different answers or a different submit token after the first accepted submission.
- Teacher, administrator, and operations views also need a stable signal for these cases.
- Metrics should record risk patterns without changing the domain rule: the server keeps the first accepted submission and does not expose raw scores before release.

## Verification
- `scripts/run-quality-gates.ps1` checks that submit replay payload and token mismatch outcomes exist in `ExamOperationMetricsAspect` and are classified before generic replay outcomes.
