# Batch 196 - Student Rules Confirmation

## Scope
- Added a first-entry rules confirmation gate before students can start a fresh attempt.
- Kept continuation behavior smooth: attempts already in progress can be resumed without another confirmation.

## Changes
- Added `StartExamRequest.rulesConfirmed`.
- `ExamController.startExam` now accepts an optional request body and passes it to `ExamService`.
- `ExamService.startExam` now rejects a `status = 0` attempt unless `rulesConfirmed = true`.
- Student exam center now shows a confirmation dialog before entering a fresh attempt.
- The confirmation is stored under `smart_exam_rules_confirmed_<attemptId>` and sent by `ExamTaking` when calling the start endpoint.

## Why This Matters
- Directly calling `/api/exams/attempt/{id}/start` can no longer start a fresh attempt without an explicit rules confirmation payload.
- The student flow now has a real pre-entry checkpoint, building on the access-status/waiting-room fields from Batch 195.
- This is still not a full proctoring consent workflow; it is a concrete state-machine guard that makes the final student waiting-room design more true.

## Verification
- Quality gates were extended to require the backend request DTO, first-entry confirmation check, frontend confirmation dialog, and start payload.
