# 340 Teacher Review Status Boundary

## Problem
The pending teacher review count used `sys_user.status = 0`, which mixed two different cases:
- a self-registered teacher waiting for administrator approval;
- an already approved teacher account that was later disabled.

That made the administrator review queue noisy and weakened the account lifecycle state machine.

## Changes
- Teacher self-registration now creates `teacher_profile.status = 0`.
- Administrator-created teacher accounts remain approved with `teacher_profile.status = 1`.
- Enabling a teacher account is the only place that promotes a pending teacher profile to `status = 1`.
- Disabling an approved teacher account no longer turns it back into a pending review.
- Pending teacher counts now require:
  - `sys_user.status = 0`
  - `sys_role.role_code = 'TEACHER'`
  - `teacher_profile.status = 0`
- User listing supports `teacherStatus=0|1`, so the admin dashboard review link opens the exact review queue:
  - `/system/users?role=TEACHER&status=0&teacherStatus=0`
- User management displays pending teachers as `待审核` and labels the enable action as `审核通过`.

## Workflow
1. Teacher self-registers.
2. Account is inactive and teacher profile is pending.
3. Admin dashboard pending teacher count increments.
4. Admin opens the pending teacher review queue.
5. Admin clicks `审核通过`.
6. User account is enabled, teacher profile is approved, and the teacher receives the existing account-enabled notification.

## Validation
- Source smoke checks assert that pending teacher review counts use `teacher_profile.status`.
- Frontend build verifies URL hydration and pending teacher display helpers.
