# 373. Question Core Text Boundary

## Background

Question stem, reference answer, analysis, and options are core exam evidence. They flow from teacher
entry or AI draft saving into `question`, then into `question_version`, paper binding, exam snapshots,
review details, wrong-book records, and administrator audit views. DTO validation already defined
lengths, but service-layer callers could still bypass controller validation.

## Changes

- Added service-layer limits for core question text:
  - `stem`, `correctAnswer`, `analysis`: 4000 characters.
  - option label: 16 characters.
  - option content: 1000 characters.
- Added service-layer normalization for required and optional question text.
- `createQuestion` and `updateQuestion` now normalize core text before writing `question`.
- Objective question options are validated before correctness rules are checked.
- `replaceOptionsInDatabase` now writes normalized option labels and content.
- The quality gate now blocks direct `trim(request.getStem())`, `trim(request.getCorrectAnswer())`,
  `trim(request.getAnalysis())`, and direct option text writes.

## Three-Terminal Impact

- Teacher terminal: manual question entry gets consistent service-layer validation even if a future entry
  point bypasses controller annotations.
- Teacher terminal: AI generated drafts saved through the question service cannot silently push invalid
  stems, answers, analysis, or options into the bank.
- Administrator terminal: question version and review audit evidence is based on bounded, normalized
  question text.
- Student terminal: exam snapshots generated from approved questions are less likely to contain hidden
  invalid or database-truncated content.

## Acceptance

- Blank or overlong stems are rejected before creating or updating a question.
- Overlong answers and analysis are rejected before creating or updating a question.
- Blank or overlong objective option labels and content are rejected before writing `question_option`.
- Core question text writes no longer use direct raw `trim(...)` calls at the database boundary.
- Full quality gates must pass after this step.
