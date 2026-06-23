# 387. Teacher AI Document Upload Boundary

## Background

The backend document extractor rejects uploads larger than 25MB, and AI source evidence rejects
`sourceDetail` values longer than 255 characters. The teacher terminal previously let oversized files or
overlong filenames reach the server before failing.

That made document import and material generation feel unreliable under normal teacher workflows.

## Changes

- Added teacher-side constants matching backend upload boundaries:
  - AI document file size: 25MB.
  - AI source detail length: 255 characters.
- Added shared file validation for both AI question document import and course material generation.
- The filename limit is calculated from the backend source label plus filename, matching the exact
  `sourceDetail` storage boundary.
- Added a visible material question total and disabled material generation when the total is invalid.
- Added quality gate checks so the frontend upload boundary cannot drift from backend AI constraints.

## Three-Terminal Impact

- Teacher terminal: invalid AI document uploads are rejected before the request is sent.
- Administrator terminal: AI usage and source evidence logs receive cleaner, bounded metadata.
- Student terminal: generated questions continue to carry bounded, auditable source evidence.

## Acceptance

- AI question document import blocks files larger than 25MB before calling the API.
- AI material generation blocks files larger than 25MB before calling the API.
- Both AI document flows reject filenames that would overflow the 255-character source detail limit.
- Material generation shows the requested total and disables the upload action when the total is 0 or over 30.
- Full quality gates must pass after this step.
