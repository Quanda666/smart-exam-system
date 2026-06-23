# 388. Document Upload Type Whitelist

## Background

The teacher terminal advertises AI document import and course material generation for txt, Word, PPT,
and PDF documents. The backend extractor previously accepted any unknown extension and attempted to
decode it as plain text.

That fallback made the upload boundary too loose for a real exam system because unsupported files could
enter AI extraction, source evidence, and audit flows before failing unpredictably.

## Changes

- Added an explicit backend document extension whitelist:
  - `txt`, `text`, `md`, `doc`, `docx`, `ppt`, `pptx`, `pdf`.
- Unknown or extensionless uploads are rejected before document extraction.
- Removed the unsafe unknown-extension fallback to plain-text decoding.
- Added a shared frontend supported-extension list used by both hidden file inputs and upload validation.
- Added quality gate checks for backend and frontend document type boundaries.

## Three-Terminal Impact

- Teacher terminal: unsupported AI upload formats are rejected before upload/extraction work starts.
- Administrator terminal: AI usage logs and material source evidence contain only supported document types.
- Student terminal: generated questions continue to come from auditable, bounded source formats.

## Acceptance

- Backend document extraction rejects unsupported extensions instead of decoding them as text.
- AI question document import and material generation use the same frontend accept list.
- Teacher-side validation rejects unsupported extensions before calling the API.
- Full quality gates must pass after this step.
