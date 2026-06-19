# 394 Question Material Source Permission

## Scope
- Harden question creation and update when a question references a course material.
- Prevent forged or stale material provenance from entering the question bank.

## Backend
- `QuestionBankService` validates source material before insert and update.
- `AI_RAG` questions must include `materialId`.
- Referenced materials must:
  - exist
  - not be soft deleted
  - belong to the same subject as the question
  - be accessible to the current user
- Administrators can reference all active materials.
- Teachers can reference only materials they uploaded.

## Why This Matters
- The frontend can be modified by a user, so RAG provenance cannot be trusted only because the official UI generated it.
- Material-based questions now keep a trustworthy chain:
  `course_material -> AI_RAG draft -> question review log -> approved question`.
- Deleted materials cannot be reused as new question evidence.

## Verification
- `scripts/run-quality-gates.ps1` checks that question creation and update call source validation and that validation consults `course_material` with deleted/access/subject constraints.
