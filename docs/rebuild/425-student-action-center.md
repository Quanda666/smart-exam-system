# 425. Student Action Center

## Scope

This batch upgrades the student dashboard from passive learning metrics into an actionable exam and score workbench.

Before this step, students had to open the exam center, result page, and wrong-question book separately to understand what needed attention. The new action center aggregates exam entry, resume, rules confirmation, score visibility, appeal status, and wrong-question practice into one dashboard section.

## Backend Changes

- Extended `GET /api/overview/student` with `actionCenter`.
- `actionCenter` includes:
  - `activeExams`
  - `readyExams`
  - `waitingSoonExams`
  - `releasedScores`
  - `pendingScores`
  - `openAppeals`
  - `wrongQuestions`
  - `total`
  - `items`
- Action item types:
  - `RESUME_EXAM`
  - `CONFIRM_RULES`
  - `ENTER_EXAM`
  - `UPCOMING_EXAM`
  - `SCORE_RELEASED`
  - `SCORE_PENDING`
  - `APPEAL_STATUS`
  - `WRONG_BOOK`
- Existing score visibility invariants are reused:
  - scores are visible only when score release is published
  - attempt is finalized and scored
  - no open recheck-required appeal exists
- The response contains navigation and status metadata only. It does not expose answer content, correct answers, analysis, or unreleased raw scores.

## Frontend Changes

- `StudentDashboard` now renders an `Action Center` section.
- The section shows summary counters and concrete action rows.
- Row navigation reuses existing routes:
  - `/student/exams?attemptId=...`
  - `/student/exams?attemptId=...&notice=rules`
  - `/student/results?attemptId=...`
  - `/student/results?attemptId=...&appealId=...`
  - `/student/wrong-questions`
- The dashboard keeps a safe default action center so older or partial API responses do not break rendering.

## Coordination Impact

- Student side: students can see whether they should enter an exam, resume an active attempt, confirm rules, wait for score release, inspect a released score, track an appeal, or practice wrong questions.
- Teacher side: no direct workflow change, but teacher score release and appeal handling now surface as student-facing status rather than silent waiting.
- Admin side: no data exposure change.

## Safety Invariants

- Action center items are advisory navigation hints.
- Exam entry still goes through the existing student exam center and backend start guard.
- Score details still go through the existing result page and backend score visibility checks.
- Appeal evidence remains controlled by the existing student appeal evidence endpoint.

## Acceptance Checks

- Student overview loads with or without action items.
- Active exam items navigate to the focused exam center.
- Ready exam items with unconfirmed rules navigate with `notice=rules`.
- Released score items navigate to the focused result page.
- Appeal items navigate to the focused result and appeal logs/evidence context.
- Wrong-question items navigate to the wrong-question book.
- Frontend build and full quality gate pass.
