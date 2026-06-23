# Batch 194 - Monitor Detail Export Filenames

## Scope
- Hardened teacher-side monitor detail exports so downloaded CSV files carry traceable context.
- Kept the change narrow to the monitor API/client boundary; no backend contract changed.

## Changes
- Monitor event exports now use an ASCII-safe filename builder with:
  - exam name
  - student name
  - `attempt_<id>`
  - active event filters: event type, risk threshold, start time, end time
  - export date
- Monitor action exports now use an ASCII-safe filename builder with:
  - exam name
  - student name
  - `session_<id>`
  - `attempt_<id>` when the teacher panel has it
  - export date
- Monitor session exports now also use the existing ASCII-safe filename builder for unfiltered exports, removing the last legacy mojibake fallback name from the monitor API client.
- Teacher monitor panel now passes the active attempt id into action export calls.

## Why This Matters
- Exported monitor evidence can be matched back to the exact attempt/session without opening the file.
- Filtered event exports are distinguishable from full event exports in audit workflows.
- This supports the monitoring closed loop in the rebuild plan: events remain risk records, while exports become easier to audit and archive.

## Verification
- Quality gates were extended to require the new filename helpers and attempt/session context.
