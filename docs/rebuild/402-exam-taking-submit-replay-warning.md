# 402 Exam Taking Submit Replay Warning

## Scope
- Surface submit replay conflicts in the student terminal.
- Keep the first finalized submit as the source of truth.

## Frontend
- `ExamTaking.submit` now reads the backend replay flags after a submit response.
- Added `submitReplayWarning`:
  - `submitPayloadMismatch` warns that the retry sent different answers and the server kept the first submitted answers.
  - `submitTokenMismatch` warns that the retry used a different submit token and the server kept the first submitted answers.
- The existing success flow still clears local draft and submit token after the server confirms finalization.

## Why This Matters
- Students can experience network retries, browser refreshes, or delayed submit attempts.
- After backend step 401, the server can distinguish clean replay from conflicting replay.
- This step closes the user-facing half of that loop so a conflicting retry is not presented as an ordinary successful retry.

## Verification
- `scripts/run-quality-gates.ps1` checks that `ExamTaking` calls `submitReplayWarning` and surfaces both payload and token mismatch messages.
