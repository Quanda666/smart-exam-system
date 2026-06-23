# 380. AI Source Detail Boundary

## Background

AI imported questions and AI material-generated drafts carry `sourceDetail` before teachers save them into
the question bank. The question bank service already rejects overlong source metadata, but `AiController`
still truncated upload-based `sourceDetail` to 255 characters.

That controller-level truncation could hide the real filename/provenance before the teacher review and
question audit flow.

## Changes

- Added `MAX_SOURCE_DETAIL_LENGTH = 255` to `AiController`.
- `sourceDetail(...)` now rejects overlong AI provenance instead of truncating it.
- Added a quality gate that blocks `detail.substring(0, 255)` from returning.

## Three-Terminal Impact

- Teacher terminal: AI imported/generated drafts either show complete supported provenance or fail early.
- Administrator terminal: question source audit evidence is no longer shortened before save.
- Student terminal: downstream exam/question snapshots inherit deliberate, validated source metadata.

## Acceptance

- AI document import source detail longer than 255 characters is rejected.
- AI material generation source detail longer than 255 characters is rejected.
- No AI controller path silently truncates `sourceDetail`.
- Full quality gates must pass after this step.
