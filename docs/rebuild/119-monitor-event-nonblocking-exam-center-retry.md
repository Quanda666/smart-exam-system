# 119. Monitor Event Nonblocking Exam Center Retry

## Background

Batch 118 added exam-center retry for locally preserved monitor event queues. The first implementation awaited the retry before loading the student exam list. That contradicted the intended contract: monitor retry is an audit enhancement and must not delay the student exam center.

This batch makes exam-center retry truly non-blocking.

## Scope

- Keep exam-center monitor queue retry.
- Start retry in the background before loading exams.
- Prevent concurrent retry runs from repeated refresh clicks.
- Keep exam list loading independent from monitor retry latency or failure.

## Frontend Contract

- `ExamList.loadExams`
  - Calls `void flushPendingMonitorQueues()`.
  - Immediately requests `listStudentExams`.
  - Does not wait for monitor retry completion.

- `flushPendingMonitorQueues`
  - Uses `monitorFlushInFlight` to avoid concurrent scans/uploads.
  - Catches failures.
  - Clears the in-flight flag in `finally`.

## Three-End Collaboration

- Student end:
  - Exam center loading and manual refresh stay responsive.
  - Stored monitor events still get opportunistic retry attempts.

- Teacher end:
  - Receives late monitor records when retry succeeds.

- Admin end:
  - Audit completeness improves without trading away student workflow responsiveness.

## Invariant

Monitor queue retry must not block exam list loading:

```text
student_exam_center_load
=> start monitor retry in background
AND request exam list immediately
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks that:

- `ExamList` calls `void flushPendingMonitorQueues()`.
- `ExamList` keeps a `monitorFlushInFlight` guard.
- The stored queue helper remains wired through `flushStoredMonitorQueues`.

## Acceptance Points

- Exam list loads even when monitor retry is slow.
- Repeated refresh clicks do not start parallel stored-queue uploads.
- Retry failures remain silent and preserve pending events.
