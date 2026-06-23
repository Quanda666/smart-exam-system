# 418. Monitor Attempt Incident Drawer

## Scope

This batch turns the teacher monitor drawer from separate event/action lookups into a single attempt incident view.

Teachers can now open one student attempt and see runtime health, draft evidence, submission evidence, answer statistics, recent monitor events, monitor actions, and force-submit evidence together.

## Backend Changes

- Added `GET /api/monitor/sessions/{sessionId}/incident`.
- The endpoint reuses existing monitor session permission checks for admins, exam creators, and scoped teachers.
- The response includes:
  - monitor session snapshot
  - attempt timing and submit evidence
  - health level and findings
  - draft metadata and draft answer counts
  - answer progress and review counts
  - latest monitor events
  - monitor action history
  - force-submit evidence
- The response does not expose answer content or unreleased scores.

## Frontend Changes

- Added `MonitorIncidentDetail` API type and `getMonitorAttemptIncident`.
- `ExamMonitorPanel` now has a `详情` action per student row.
- The drawer now starts with a `Health` tab showing:
  - runtime deadline and heartbeat
  - draft revision and saved count
  - submission type, reason, and payload hash
  - answer progress and pending review count
  - risk score and latest event
  - force-submit action evidence
- Existing event filtering, event export, action history, action export, reminders, notes, and force-submit flows remain available.

## Coordination Impact

- Teacher side: faster triage during live exams; one drawer contains the evidence needed before reminding or force-submitting.
- Student side: no behavior change; no answers or scores are leaked.
- Admin side: incident evidence remains aligned with monitor action logs and notification audit links.

## Acceptance Checks

- Teachers can open a monitor session and see health, draft, submit, answer, risk, and force-submit evidence.
- Event and action tabs still load and export correctly.
- Force-submit refreshes the incident drawer after completion.
- The incident endpoint rejects unauthorized monitor access.
