# 215. Shared Rules Confirmation Storage

## Goal

Centralize the student-side local rules confirmation cache used by the exam list and the exam-taking page. Both components previously duplicated the same storage key and write behavior.

## Scope

- Add `frontend/src/utils/rulesConfirmationStorage.ts`.
- Keep the existing `smart_exam_rules_confirmed_` key format.
- Keep the same sessionStorage plus localStorage write strategy.
- Replace local duplicate implementations in `ExamList.vue` and `ExamTaking.vue`.
- Add quality-gate checks so the storage key remains centralized.

## Three-End Coordination

- Student end: exam list confirmation and in-exam reminder confirmation now share the same local cache helper.
- Teacher end: reminders still depend on server-side `rules_confirmed_at`; this change only removes frontend duplication.
- Admin/audit end: backend confirmation remains the authoritative audit source.

## Acceptance Notes

- `ExamTaking.vue` reads the local confirmation through the shared util before calling `startExam`.
- `ExamList.vue` persists confirmation through the shared util before entering the exam.
- Neither component owns the storage key directly.
- Storage failures remain non-blocking because the backend still enforces and records confirmation.
