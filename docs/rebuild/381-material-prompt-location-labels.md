# 381. Material Prompt Location Labels

## Background

Material chunks now enter the AI prompt with labels like `[page 1 paragraph 2]`. The material question
generation prompt still told the model to look for the older page/paragraph marker format, so generated
drafts could miss `sourcePage`, `sourceParagraph`, and `sourceExcerpt`.

## Changes

- Updated the material question prompt to reference `[page 1 paragraph 2]`.
- Bumped `PROMPT_VERSION_MATERIAL_GENERATE` from `material-question-v2` to `material-question-v3`.
- Added a quality gate so the prompt and material context label format stay aligned.

## Three-Terminal Impact

- Teacher terminal: material-generated question drafts are more likely to carry accurate source location.
- Administrator terminal: AI prompt-version audit can distinguish drafts generated before and after this
  provenance fix.
- Student terminal: downstream question/explanation evidence inherits better material source metadata.

## Acceptance

- Material context uses `[page N paragraph M]`.
- Material-generation prompt explicitly tells the model to return source fields from that label format.
- Material-generation prompt version is bumped after the behavior change.
- Full quality gates must pass after this step.
