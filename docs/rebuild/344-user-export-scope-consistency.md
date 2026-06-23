# 344 User Export Scope Consistency

## Problem
User management list loading already respected the current scope, including teacher review queues, but CSV export still built its own query from only the toolbar fields. This could export the wrong users from pending or rejected teacher review queues, and class-scope export also missed the local class-membership filtering used by the table.

## Changes
- Added a shared user-list query builder in `UserManagement`.
- List loading and CSV export now use the same query source.
- Teacher review queue export preserves:
  - `role=TEACHER`;
  - `status=0`;
  - `teacherStatus=0` or `teacherStatus=2`.
- Class-scope export applies the same local class-membership filter as the table.
- Exported status text now uses the same row status helper as the table, so pending and rejected teachers export as review states rather than generic disabled accounts.
- Export filenames now reflect the active scope, including pending and rejected teacher review lists.

## Workflow
1. Administrator opens a user scope such as pending teachers, rejected teachers, a role, or a class.
2. The table loads using the shared query builder.
3. Export uses the same query builder and any required local filtering.
4. The downloaded CSV matches what the administrator is currently reviewing.

## Validation
- Quality gate source checks assert shared query usage, teacher review export preservation, class filtering, and scoped export filenames.
