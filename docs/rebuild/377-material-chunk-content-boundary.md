# 377. Material Chunk Content Boundary

## Background

Material chunk content is the core evidence used by material preview, AI chunk selection, material-based
question generation, and question source excerpts. The service previously truncated each chunk to 4000
characters inside `chunk(...)`, which silently discarded extracted teaching material.

## Changes

- Added `MAX_MATERIAL_CHUNK_CONTENT_LENGTH = 4000`.
- Replaced silent `truncate(content.trim(), 4000)` with explicit content normalization.
- Added `appendContentChunks(...)` so long material content is split into multiple chunks instead of being
  discarded.
- Added `chunkContentSplitEnd(...)` to prefer whitespace boundaries when splitting long content, while still
  enforcing the hard 4000-character database boundary.
- Extended the quality gate to block the old content truncation pattern and require the split helpers.

## Three-Terminal Impact

- Teacher terminal: long uploaded materials keep their searchable evidence for preview and AI generation.
- Teacher terminal: generated questions have a more complete source pool instead of depending on silently
  shortened chunks.
- Administrator terminal: material audit evidence is more faithful to the uploaded source file.
- Student terminal: material-derived questions and explanations are based on less lossy teacher evidence.

## Acceptance

- A long single-line material paragraph is split into multiple non-empty chunks.
- No persisted material chunk content exceeds 4000 characters.
- Material chunk content is no longer silently truncated in `chunk(...)`.
- Existing heading and keyword normalization remains unchanged.
- Full quality gates must pass after this step.
