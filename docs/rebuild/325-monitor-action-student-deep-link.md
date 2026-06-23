# Batch 325: Monitor Action Student Deep Link

## Background

Monitor action notifications already use `EXAM_ATTEMPT` as their audit relation, but ordinary warnings and force-submit notices still linked students to the generic exam center. That broke the cross-terminal contract: teachers act on one concrete monitor session, while students land on a broad list and must infer which exam changed.

## Changes

- `MONITOR_WARNING` notifications now link to `/student/exams?attemptId=<attemptId>`.
- `MONITOR_FORCE_SUBMIT` notifications now link to `/student/exams?attemptId=<attemptId>`.
- The notification relation remains `EXAM_ATTEMPT + attemptId`, matching the link target.
- `MONITOR_RULES_REMINDER` keeps its specialized rules-confirmation link: `/student/exams?notice=rules&attemptId=<attemptId>`.
- A small `monitorAttemptLink` helper centralizes the ordinary monitor action link.

## Three-Terminal Collaboration

- Teacher terminal: monitor actions continue to write action records and notify the affected student.
- Student terminal: opening the notification focuses the exact exam attempt instead of only entering the exam center.
- Admin terminal: notification audit relation and student navigation target now describe the same attempt, making delivery and action tracing easier to verify.

## Verification

- Local quality gate checks now reject monitor warning or force-submit notifications that do not use the exact attempt link.
- Full local quality gates should pass with the existing expected warnings only.
