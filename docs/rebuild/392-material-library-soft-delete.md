# 392. Material Library Soft Delete

## Background

The course material library supported upload, list, detail, RAG generation, and teacher-selected draft
saving. It still lacked a lifecycle action for removing obsolete or mistakenly uploaded materials.

The database already had `course_material.deleted`, and existing list/detail/generation queries only use
non-deleted materials, so a soft-delete workflow fits the current model.

## Changes

- Added backend soft delete for course materials:
  - administrators can delete any material.
  - teachers can delete only materials they uploaded, using the existing material access scope.
  - deleted materials are marked `deleted = 1` and `status = 0`.
- Added `DELETE /api/materials/{id}`.
- Added frontend `deleteCourseMaterial(...)` API.
- Added delete action in the material library table with confirmation.
- When the active detail or generation dialog references a deleted material, the page closes that state and
  refreshes the material list.
- Added quality gate checks for soft-delete behavior and frontend confirmation.

## Three-Terminal Impact

- Teacher terminal: teachers can remove obsolete uploaded course materials from their working library.
- Administrator terminal: administrators can clean up incorrect or unsafe materials across the library.
- Student terminal: downstream questions keep their existing material provenance; soft delete hides the
  reusable material workflow without physically deleting generated question audit references.

## Acceptance

- Deleting a material updates `course_material.deleted` instead of physically removing records.
- Deleted materials no longer appear in list/detail/generation flows.
- The frontend asks for confirmation before deletion and refreshes the list afterward.
- Full quality gates must pass after this step.
