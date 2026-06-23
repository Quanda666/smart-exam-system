# 389. Material Library Workbench

## Background

The backend already exposed `/api/materials` for course material upload, listing, detail, and RAG-based
question generation. The frontend also had an API wrapper, but the main application had no reachable
material-library page.

That left the teacher material library as an incomplete workflow: teachers could generate questions from a
one-off uploaded file, but could not maintain reusable course materials with outlines and chunks.

## Changes

- Added `MaterialLibraryPanel` for administrator and teacher terminals.
- The page supports:
  - subject and keyword filtering.
  - bounded material upload.
  - material list with subject, uploader, outline count, and chunk count.
  - material detail drawer with generated outline and chunk evidence.
- Reused backend upload limits in the frontend:
  - file size: 25MB.
  - title length: 200 characters.
  - filename length: 255 characters.
  - supported extensions: `txt`, `text`, `md`, `doc`, `docx`, `ppt`, `pptx`, `pdf`.
- Wired `/materials` into `App.vue`, administrator/teacher menu defaults, role permission groups, and
  role permission migration seed data.
- Added quality gate checks so the material library cannot regress into an unreachable API-only feature.

## Three-Terminal Impact

- Teacher terminal: teachers can maintain reusable course materials instead of uploading ad hoc files for
  every generation request.
- Administrator terminal: administrators can access the same material library for audit and support.
- Student terminal: downstream AI/RAG questions can now point back to maintained material evidence.

## Acceptance

- Administrator and teacher menus include `/materials` by default.
- Role management can grant or revoke `/materials`.
- The material library page uploads only bounded, supported document files.
- The material library page lists uploaded materials and opens outline/chunk details.
- Full quality gates must pass after this step.
