# 383. AI Generated Option Boundary

## Background

AI-generated objective questions are reviewed by teachers before they are saved into the question bank.
The question bank already rejects option content longer than 1000 characters, but `AiService` silently
truncated generated option content during draft normalization.

That made teacher review operate on shortened options instead of the full AI output.

## Changes

- Added `MAX_GENERATED_OPTION_CONTENT_LENGTH = 1000`.
- Added `normalizeGeneratedOptionContent(...)`.
- TRUE_FALSE custom options and normal objective options now reject overlong option content instead of
  truncating it.
- Extended the quality gate to block the old 1000-character option truncation patterns.

## Three-Terminal Impact

- Teacher terminal: AI objective drafts either contain complete supported option text or fail before review.
- Administrator terminal: AI/question audit evidence no longer hides option text loss before save.
- Student terminal: downstream objective question snapshots inherit teacher-reviewed options that were not
  silently shortened.

## Acceptance

- AI generated TRUE_FALSE option content longer than 1000 characters is rejected.
- AI generated SINGLE_CHOICE/MULTIPLE_CHOICE option content longer than 1000 characters is rejected.
- AI generated option content is no longer silently truncated before teacher review.
- Full quality gates must pass after this step.
