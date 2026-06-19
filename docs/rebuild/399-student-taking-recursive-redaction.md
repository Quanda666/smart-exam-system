# 399 Student Taking Recursive Redaction

## Scope
- Harden the active student exam-taking payload against accidental answer leakage.
- Keep `/api/exams/attempt/{attemptId}/start` safe even if future query fields or option metadata expand.

## Backend
- `ExamService.sanitizeStudentTakingQuestions` now delegates to recursive map/list sanitizers.
- Added a normalized sensitive-key blacklist for active taking payloads:
  - `correct`, `isCorrect`, `is_correct`
  - `correctAnswer`, `correct_answer`
  - `rightAnswer`, `standardAnswer`, `referenceAnswer`
  - `answer`, `answerContent`
  - `analysis`, `explanation`, `solution`
- Key matching ignores case and separators, so camelCase and snake_case variants are both removed.

## Why This Matters
- The student terminal is a hostile-client boundary: every field returned by the start endpoint is visible in browser developer tools.
- Earlier redaction removed the known answer fields, but it was shallow and depended on exact field names.
- Recursive redaction makes the contract stronger for nested option metadata and future DTO changes.

## Verification
- `scripts/run-quality-gates.ps1` checks that student taking questions pass through the recursive sanitizer and that the sensitive-key blacklist covers answer, correctness, and analysis fields.
