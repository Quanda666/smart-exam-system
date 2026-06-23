# 382. AI Generated Question Text Boundary

## Background

AI-generated drafts are reviewed by teachers before they enter the question bank. The question bank service
already rejects question stem, analysis, and non-objective reference answers longer than 4000 characters,
but `AiService` silently truncated those fields while normalizing drafts.

That meant teachers could review a shortened AI output without knowing source text was lost.

## Changes

- Added `MAX_GENERATED_QUESTION_TEXT_LENGTH = 4000`.
- Added `normalizeGeneratedQuestionText(...)`.
- AI draft stem, analysis, and non-objective correct answer now reject overlong text instead of truncating it.
- Added a quality gate that blocks the old 4000-character truncation patterns.

## Three-Terminal Impact

- Teacher terminal: generated drafts are either complete within supported boundaries or rejected before review.
- Administrator terminal: AI/question audit evidence no longer hides server-side text loss before save.
- Student terminal: downstream exam snapshots inherit teacher-reviewed text that was not silently shortened.

## Acceptance

- AI generated stem longer than 4000 characters is rejected before teacher review.
- AI generated analysis longer than 4000 characters is rejected before teacher review.
- AI generated non-objective correct answer longer than 4000 characters is rejected before teacher review.
- Objective option content truncation remains a separate follow-up boundary.
- Full quality gates must pass after this step.
