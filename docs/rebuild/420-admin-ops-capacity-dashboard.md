# 420. Admin Ops Capacity Dashboard

## Scope

This batch adds an operations capacity section to the administrator dashboard.

Before this step, administrators could see business counts and approval SLA, but not enough runtime evidence to judge whether the exam system was under operational pressure.

## Backend Changes

- `OverviewService.adminOverview` now returns `opsCapacity`.
- `opsCapacity` includes:
  - database health and latency
  - Redis/MySQL draft cache status
  - active attempts, running exams, timeout pressure, and deadline-passed active attempts
  - monitor sessions, offline active sessions, high-risk sessions, and recent monitor event volume
  - daily submit throughput split by manual, timeout, and forced submit
  - normalized operation alerts
- Admin overview is now protected with `@RequireRoles("ADMIN")`.

## Frontend Changes

- `AdminDashboard` now shows an `运维容量` panel on the first screen.
- The panel displays:
  - database status
  - draft cache dirty/error counters
  - active attempt capacity
  - monitor pressure and event storm evidence
  - submit throughput
  - current operations alerts
- Quick actions link to system config and real-time monitoring.

## Coordination Impact

- Admin side: can triage capacity pressure without opening logs first.
- Teacher side: no workflow change; monitor data feeds the admin capacity panel.
- Student side: no workflow change; draft and submit resilience remain backend-authoritative.

## Acceptance Checks

- Admin overview contains `opsCapacity`.
- Dashboard shows database, draft cache, exam runtime, monitor runtime, submit runtime, and alerts.
- Alert levels distinguish `OK`, `INFO`, `WARN`, and `HIGH`.
- Dashboard links to system config and real-time monitoring for follow-up action.
