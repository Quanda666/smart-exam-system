# 390. Material Library RAG Drafts

## Background

The material library page could upload and inspect reusable course materials, while the backend already
had `/api/materials/{id}/questions/generate` for RAG-style question generation. The teacher workflow was
still incomplete because generated drafts could not be produced and saved from the material library page.

That meant reusable materials were visible but not yet integrated into the teacher question-bank workflow.

## Changes

- Added material-library question generation controls:
  - optional knowledge point name.
  - difficulty.
  - default score.
  - optional requirements.
  - per-question-type counts.
- Reused the backend material-generation count limits:
  - per type: 30.
  - total: 30.
- Added generated-draft preview inside the material library page.
- Added save-to-question-bank action using the existing AI generated question save endpoint.
- Preserved backend provenance fields from material-library generation:
  - `sourceType = AI_RAG`.
  - `materialId`.
  - source page and paragraph evidence.
- Added quality gate checks for material-library generate and save workflows.

## Three-Terminal Impact

- Teacher terminal: uploaded materials now produce reusable question-bank drafts without leaving the
  material library workflow.
- Administrator terminal: administrators can inspect materials and generate auditable drafts for support or
  content operations.
- Student terminal: downstream questions generated from materials carry source evidence for later audit,
  review, and wrong-question explanation safety.

## Acceptance

- A teacher or administrator can open `/materials`, select a material, generate drafts, preview them, and
  save them to the question bank.
- Material-library generation blocks empty or over-30 total count requests before calling the API.
- Saved drafts keep material-library provenance from the backend.
- Full quality gates must pass after this step.
