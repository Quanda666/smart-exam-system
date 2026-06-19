# 214. Rules Reminder Action Guard

## Goal

Make the teacher monitor action UI match the server-side rules reminder state machine. The backend already rejects a `RULES_REMINDER` after a student has confirmed rules or after the attempt is no longer active, but the teacher UI needed clearer guidance before submission.

## Scope

- Add a disabled-button title for unavailable `Rules` monitor actions.
- Show an in-dialog info alert explaining why a rules reminder cannot be sent.
- Guard `saveAction` locally when `actionType = RULES_REMINDER` is no longer valid.
- Keep backend validation as the authoritative enforcement point.
- Add quality-gate anchors for the new explanation and local guard.

## Three-End Coordination

- Teacher end: teachers see why a rules reminder cannot be sent instead of discovering it only after an API failure.
- Student end: no extra notification is sent after rules have already been confirmed.
- Admin/audit end: invalid or duplicate rules reminder attempts are reduced before they can create noisy operational failures.

## Acceptance Notes

- Confirmed students show `Rules already confirmed by the student.`
- Non-active attempts show `Rules reminders require an active attempt.`
- The save action does not call the monitor action API when the selected rules reminder is locally invalid.
- Backend validation still remains the final source of truth for stale or forged requests.
