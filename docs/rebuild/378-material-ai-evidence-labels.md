# 378. Material AI Evidence Labels

## Background

Material-based AI generation relies on two pieces of textual evidence:

- `sourceDetail`, which is later stored with generated question drafts.
- The material context prefix, which tells the AI which page and paragraph each chunk came from.

Both fields had mojibake labels in `MaterialLibraryService`. The values still compiled, but they weakened
question traceability and made teacher review evidence look unreliable.

## Changes

- Changed generated question source detail to `Material library: {title}`.
- Changed material context chunk labels to `[page N paragraph M]`.
- Added quality gate checks so material evidence labels stay stable and mojibake does not return.

## Three-Terminal Impact

- Teacher terminal: AI-generated question drafts have readable source details during review.
- Administrator terminal: question and AI evidence audits can be interpreted without decoding corrupted text.
- Student terminal: downstream source explanations inherit clearer teacher-side evidence.

## Acceptance

- Material-based generated questions use a readable `sourceDetail`.
- AI context includes readable page and paragraph labels.
- Known mojibake fragments are blocked by the quality gate.
- Full quality gates must pass after this step.
