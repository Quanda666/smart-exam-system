# 342 Teacher Review Queue Visibility

## Problem
Teacher registration review now supports pending, approved, and rejected states, but the administrator UI still primarily exposed the pending queue. Rejected teacher registrations were reachable only by manually editing URL filters or searching the user table.

## Changes
- Added `rejectedTeacherReviews` to `/api/system/users/summary`.
- Added `rejectedTeacherReviews` to `/api/overview/admin`.
- Added an admin dashboard quick action and stat card for rejected teachers:
  - `/system/users?role=TEACHER&status=0&teacherStatus=2`
- Added user-management summary cards for:
  - pending teacher reviews;
  - rejected teacher reviews.
- Added a `教师审核` scope tree group in user management:
  - pending teachers use `teacherStatus=0`;
  - rejected teachers use `teacherStatus=2`.
- User-management queue clicks now sync the browser route, so links remain shareable and refresh-safe.

## Workflow
1. Administrator opens the dashboard and sees both pending and rejected teacher review counts.
2. Clicking either count opens the exact queue in user management.
3. The left-side scope tree reflects the selected teacher review queue.
4. Rejected teachers can be reviewed again and approved without mixing them into the pending queue.

## Validation
- Quality gate source checks assert rejected teacher counts, dashboard routes, user-management scope-tree entries, and route hydration.
