# 375. Material Outline Boundary

## Background

Material outlines are shown in the teacher material library and help drive material-based AI question
generation. They are stored in `course_material_outline` with fixed database limits. The service
previously truncated outline title, summary, and keywords silently when inserting rows, which could hide
part of the AI/material evidence from teachers and administrators.

## Changes

- Added service-layer limits for material outline evidence:
  - title: 200 characters.
  - summary: 1000 characters.
  - keywords: 500 characters.
- Added `normalizeMaterialOutlineTitle`, `normalizeMaterialOutlineSummary`, and
  `normalizeMaterialOutlineKeywords`.
- `insertOutline` now writes normalized outline fields and rejects overlong values.
- Blank outline titles fall back to `Knowledge point`.
- The quality gate now blocks the old outline `truncate(...)` insert pattern.

## Three-Terminal Impact

- Teacher terminal: material outlines are stored as deliberate evidence rather than silently shortened AI
  output.
- Teacher terminal: question generation from materials is based on visible, bounded outline metadata.
- Administrator terminal: material library audit/search data is less likely to diverge from what the AI
  returned and what teachers reviewed.
- Student terminal: material-derived questions inherit cleaner provenance through the teacher workflow.

## Acceptance

- Overlong outline titles are rejected before inserting `course_material_outline`.
- Overlong outline summaries and keywords are rejected before inserting `course_material_outline`.
- Blank outline titles use the stable `Knowledge point` fallback.
- `insertOutline` no longer silently truncates title, summary, or keywords.
- Full quality gates must pass after this step.
