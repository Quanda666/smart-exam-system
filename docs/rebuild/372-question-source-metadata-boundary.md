# 372. Question Source Metadata Boundary

## Background

Question source metadata connects manual questions, AI generated drafts, material references, question
versions, and review audit logs. These fields later help teachers explain where a question came from and
help administrators audit AI/material usage. The service previously truncated `sourceExcerpt`,
`aiModel`, and `promptVersion` silently before saving, while `sourceDetail` relied only on DTO
validation.

## Changes

- Added service-layer limits for question source metadata:
  - `sourceDetail`: 255 characters.
  - `sourceExcerpt`: 500 characters.
  - `aiModel`: 64 characters.
  - `promptVersion`: 64 characters.
- Added shared normalization for source metadata: trim, blank-to-null, reject overlong values.
- `createQuestion` and `updateQuestion` now use normalized source metadata before writing `question`.
- `question_version` snapshots inherit validated metadata from the question row instead of silently
  truncated values.
- The quality gate now blocks the old silent truncation pattern for question source metadata.

## Three-Terminal Impact

- Teacher terminal: AI/material provenance is either stored completely within the supported boundary or
  rejected with a clear validation error.
- Administrator terminal: question review audit and system exports can trust source fields as deliberate
  evidence, not silently shortened text.
- Student terminal: exam paper snapshots built from approved questions are based on validated question
  metadata, reducing hidden drift between question creation and later exam evidence.

## Acceptance

- Overlong `sourceDetail` is rejected before creating or updating a question.
- Overlong `sourceExcerpt` is rejected before creating or updating a question.
- Overlong `aiModel` and `promptVersion` are rejected before creating or updating a question.
- No question source metadata path uses silent `truncate(blankToNull(...))`.
- Full quality gates must pass after this step.
