# 339 Pending Teacher Review Entry

## Problem
Teacher self-registration now creates administrator review notifications, but the administrator surface still lacked a persistent review queue indicator. Admins could open a precise notification deep-link for one user, yet there was no dashboard or user-list entry for the whole pending teacher review backlog.

## Changes
- Added `pendingTeacherReviews` to `/api/system/users/summary`.
- Added `pendingTeacherReviews` to `/api/overview/admin`.
- Added an admin dashboard quick action for `/system/users?role=TEACHER&status=0`.
- Added a clickable admin dashboard stat card for pending teacher reviews.
- Hydrated `UserManagement` filters from route query parameters:
  - `role=TEACHER`
  - `status=0`
  - existing exact `userId=<id>` links still take priority.

## Workflow
1. Teacher self-registers and remains inactive.
2. Administrators see pending teacher review count on the dashboard.
3. Administrators click the dashboard entry.
4. User management opens with teacher + disabled/pending filters already applied.
5. Admin can enable the teacher account, which sends the existing account-enabled notification and revokes stale status assumptions.

## Validation
- Source hygiene checks cover Java and frontend files.
- Full quality gate asserts that pending teacher counts and role/status deep-links remain wired.
