# 384. AI Material Type Count Boundary

## Background

Material-based AI question generation accepts per-type counts from the teacher terminal. The service already
limits one generation request to 30 questions total, but `normalizedTypeCounts(...)` silently capped each
type count with `Math.min(value, 30)`.

That allowed a request such as 31 single-choice questions to become 30 without telling the teacher.

## Changes

- Added `MAX_MATERIAL_TYPE_COUNT = 30`.
- `normalizedTypeCounts(...)` now rejects per-type counts above 30.
- Removed silent capping of material question type counts.
- Added a quality gate that blocks `Math.min(value, 30)` from returning to this path.

## Three-Terminal Impact

- Teacher terminal: generation requests fail clearly when counts exceed the supported limit.
- Administrator terminal: AI usage and generation audit reflect the teacher's actual accepted request.
- Student terminal: downstream questions are generated from explicit teacher-approved quantities.

## Acceptance

- A material generation request with any type count greater than 30 is rejected.
- Valid counts from 1 to 30 are preserved exactly.
- Total request limit of 30 questions still applies.
- Full quality gates must pass after this step.
