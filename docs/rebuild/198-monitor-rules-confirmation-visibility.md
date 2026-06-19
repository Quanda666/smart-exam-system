# Batch 198 - Monitor Rules Confirmation Visibility

## Scope
- Surfaced student rules confirmation timestamps in the teacher monitoring workflow.
- Builds on Batch 197, where `exam_attempt.rules_confirmed_at` became the server-side audit source.

## Changes
- Monitor session list now selects `rulesConfirmedAt` from `exam_attempt`.
- Monitor session CSV export includes `Rules Confirmed At`.
- Frontend `MonitorSession` includes `rulesConfirmedAt`.
- Teacher monitor table shows a `Rules` column with the confirmation time.

## Why This Matters
- The student-side rules confirmation is now visible to teachers during live monitoring.
- Exported monitor evidence includes whether/when the student acknowledged exam rules.
- This closes another small loop between student entry controls and teacher-side audit.

## Verification
- Quality gates require the backend projection, CSV column, frontend type, and monitor table display.
