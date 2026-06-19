# 376. Material Chunk Boundary

## Background

Material chunks are the searchable units used by the teacher material library and material-based AI
question generation. Chunk headings and keywords are stored in `course_material_chunk` with fixed
database limits. The service previously truncated these fields during insert, and a very long single line
could create an empty chunk before the real content was appended.

## Changes

- Added service-layer limits for material chunk evidence:
  - heading: 200 characters.
  - keywords: 500 characters.
- Added `normalizeMaterialChunkHeading` and `normalizeMaterialChunkKeywords`.
- Chunk inserts now write normalized heading and keywords and reject overlong values.
- Long-line chunk flushing now only happens when the buffer already contains content, preventing empty
  chunks from being created.
- The quality gate now blocks the old chunk `truncate(...)` insert pattern and checks the long-line guard.

## Three-Terminal Impact

- Teacher terminal: material chunk headings and keywords are stored as deliberate searchable evidence.
- Teacher terminal: material preview and AI generation are less likely to include empty chunks.
- Administrator terminal: material audit/search fields no longer depend on silent truncation.
- Student terminal: material-derived questions inherit cleaner source snippets from the teacher workflow.

## Acceptance

- Overlong chunk headings are rejected before inserting `course_material_chunk`.
- Overlong chunk keywords are rejected before inserting `course_material_chunk`.
- Long single-line material content does not create an empty chunk before the content chunk.
- Chunk insert no longer silently truncates heading or keywords.
- Full quality gates must pass after this step.
