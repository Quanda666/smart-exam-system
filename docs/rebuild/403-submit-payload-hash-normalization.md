# 403 Submit Payload Hash Normalization

## Scope
- Reduce false submit replay conflict warnings.
- Keep submit replay mismatch detection aligned with the actual attempt question set.

## Backend
- `ExamService.submitExam` now loads the attempt question list before hashing a submitted payload.
- `answerPayloadHash` now hashes a canonical map built from the expected question ids instead of hashing the raw client map.
- Missing answers, `null`, and blank-only strings are normalized to the same empty value.
- Extra client fields are still rejected by submission validation for active attempts, while replay mismatch comparison focuses on expected attempt questions.

## Why This Matters
- Clients may represent unanswered questions differently during retry:
  - omitted key
  - `null`
  - empty string
  - whitespace-only string
- Those forms are all treated as unanswered by grading and answer statistics.
- The submit replay hash should not flag those equivalent unanswered states as conflicting payloads.

## Verification
- `scripts/run-quality-gates.ps1` checks that submit payload hashes are calculated from `paperQuestions`, use `canonicalSubmittedAnswerForHash`, and normalize blank answers before comparing replay payloads.
