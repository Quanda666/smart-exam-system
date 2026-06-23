# 391. Material Library Draft Review

## Background

Material-library RAG generation could produce drafts and save them into the question bank, but the first
workflow saved the generated batch as a whole. For a real exam system, AI/RAG output must remain teacher
review material, not an automatic publishing decision.

Teachers need to see answers and choose which generated drafts are worth saving.

## Changes

- Added selectable generated drafts in the material library dialog.
- Generated drafts are selected by default after generation, but teachers can clear or reselect the batch.
- Save now submits only selected drafts to the question bank.
- Draft preview now shows:
  - question type.
  - difficulty.
  - score.
  - stem.
  - options.
  - correct answer.
  - analysis.
  - material source detail with page and paragraph evidence.
- Correct objective options are visually marked for teacher review.
- Added quality gate checks that prevent reverting to unreviewed full-batch saving.

## Three-Terminal Impact

- Teacher terminal: teachers explicitly confirm which RAG drafts enter the question bank.
- Administrator terminal: material-generated questions remain auditable as selected teacher/admin actions.
- Student terminal: students are less likely to receive unreviewed AI material because generated questions
  still enter through draft review.

## Acceptance

- Material-library generation defaults drafts to selected but allows clearing and reselection.
- Saving submits only selected drafts.
- Draft preview exposes correct answers and material evidence to the teacher before saving.
- Full quality gates must pass after this step.
