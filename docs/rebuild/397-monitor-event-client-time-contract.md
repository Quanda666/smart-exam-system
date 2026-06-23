# 397 Monitor Event Client Time Contract

## Scope
- Make monitor event timestamps an explicit API contract.
- Continue treating monitor events as audit/risk records, not automatic cheating decisions.

## Backend
- `CheatEventRequest.clientEventTime` is now required with `@NotBlank`.
- `MonitorService.recordCheatEvents` now calls `requireClientEventTime` before any attempt lookup or insert.
- Missing `clientEventTime` is rejected with `clientEventTime is required for monitor event reporting`.
- Existing parsing still accepts ISO-8601 offset/instant strings and local date-time strings.

## Why This Matters
- Student-side monitor collection already sends `clientEventTime` for every event.
- Submitted-attempt late uploads need a client timestamp to prove the event happened inside the attempt window.
- Requiring the timestamp for all events keeps active, offline, and late monitor ingestion under one consistent contract.

## Verification
- `scripts/run-quality-gates.ps1` checks both DTO validation and service-level enforcement for required monitor client timestamps.
