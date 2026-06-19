# 393 Material Library Operation Audit

## Scope
- Add audit evidence to material library upload, RAG draft generation, and soft delete.
- Surface copyable audit IDs and audit links in the teacher/admin material library workbench.
- Reuse existing operation log and question review audit channels instead of adding a new table.

## Backend
- `MaterialLibraryController` now records operation logs for:
  - `UPLOAD_MATERIAL`
  - `GENERATE_MATERIAL_QUESTIONS`
  - `DELETE_MATERIAL`
- Mutating material endpoints return `operationLogId`.
- RAG draft generation now returns:
  - `questions`
  - `generatedCount`
  - `operationLogId`
- `deleteMaterial` keeps the resolved title and filename in the response so the operation log has useful evidence.

## Frontend
- `frontend/src/api/material.ts` defines audited result types:
  - `CourseMaterialUploadResult`
  - `MaterialQuestionGenerateResult`
  - `MaterialDeleteResult`
- `MaterialLibraryPanel.vue` shows a material operation audit banner after:
  - upload material
  - generate material drafts
  - delete material
  - save selected material drafts to question bank
- Saving selected drafts surfaces existing question review audit IDs, because the persisted questions enter the question review workflow.

## Three-Terminal Value
- Teacher/admin operations on materials are traceable from the system log.
- Generated drafts remain reviewable question-bank drafts; AI/RAG output is not treated as automatically approved content.
- Administrators can use copied deep links to inspect material-related actions during audits.

## Verification
- `scripts/run-quality-gates.ps1` checks backend operation logging, API result types, frontend audit banner, copy helpers, and generation response handling.
