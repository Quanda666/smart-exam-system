# 379. Document Extraction Text Boundary

## Background

Course materials are used as evidence for teacher preview, AI question generation, question source
metadata, and later audits. `DocumentTextExtractorService` already had an 80000-character extraction
limit, but the normalized text was silently truncated before being saved and chunked.

Silent truncation makes the uploaded material look successful while losing source evidence.

## Changes

- Added `ensureExtractedTextWithinLimit(...)`.
- The extractor now rejects normalized text longer than 80000 characters.
- `normalizeText(...)` now only normalizes whitespace/control characters and no longer truncates text.
- Added a quality gate that blocks `substring(0, MAX_TEXT_LENGTH)` from returning to the extractor.

## Three-Terminal Impact

- Teacher terminal: oversized materials fail early with a clear split-before-upload requirement.
- Administrator terminal: stored material evidence is no longer silently shortened before audit.
- Student terminal: material-derived questions are based on complete accepted material snapshots.

## Acceptance

- Extracted text longer than 80000 characters is rejected before material rows are inserted.
- Accepted extracted text is not silently truncated by `normalizeText(...)`.
- Existing blank-text and PDF-noise checks still run.
- Full quality gates must pass after this step.
