# 374. Material Upload Name Boundary

## Background

Course materials are the entry point for material-based AI question generation. The material title and
original filename are shown in the teacher material library, used for search, and copied into AI question
source details. The service previously truncated titles and filenames silently before writing
`course_material`, which could make teacher-visible evidence differ from the uploaded source.

## Changes

- Added `MAX_MATERIAL_TITLE_LENGTH = 200` and `MAX_MATERIAL_FILENAME_LENGTH = 255`.
- Added `normalizeMaterialTitle` and `normalizeMaterialFilename`.
- `uploadMaterial` now normalizes the original filename and final material title before AI outline
  generation and database writes.
- Overlong titles and filenames are rejected instead of being silently shortened.
- The quality gate now blocks the old upload-entry truncation pattern.

## Three-Terminal Impact

- Teacher terminal: uploaded material names remain deliberate and searchable; too-long names fail fast
  with a clear validation error.
- Teacher terminal: AI generated question source details refer to the same material title that was
  stored in the library.
- Administrator terminal: material audit/search evidence no longer depends on silently shortened names.
- Student terminal: generated exam questions that reference material-derived sources inherit cleaner
  provenance from the teacher workflow.

## Acceptance

- Overlong material titles are rejected before inserting `course_material`.
- Overlong original filenames are rejected before inserting `course_material`.
- Material upload no longer uses `truncate(materialTitle, 200)` or `truncate(filename, 255)`.
- Full quality gates must pass after this step.
