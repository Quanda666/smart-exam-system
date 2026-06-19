# 441 Closeout Acceptance Checklist

## Current Closeout Scope
- This closeout focuses on the most important operational exam lifecycle:
  - exam publication snapshot safety
  - active attempt finalization
  - manual review completion
  - missing score repair
  - score release readiness
  - audit traceability
- This is not a claim that every long-term rebuild item is finished. It is the current highest-value delivery set for near-term handoff.

## Core Completed Closures
- Snapshot evidence and repair:
  - export exam snapshot evidence without answers, correct answers, analysis, stems, or unreleased scores
  - repair missing candidate/question snapshots without deleting submitted work
  - operation audits for export and repair
- Score release blockers:
  - recalculate missing scores for completed attempts with answer records and no pending review
  - force-submit active attempts after an exam has ended or been closed
  - keep subjective answers in manual review
  - reject repair/finalization actions after scores are already published
- Review to release handoff:
  - review submission returns exam-level release handoff state
  - ReviewPanel exposes `Score Readiness` after an exam review queue is clear
  - ExamManagement auto-opens release readiness from `scoreReadiness=1`
- Operator navigation:
  - Lifecycle Health, Score Safety, Readiness, Snapshot, Review, Recheck, Appeals, and Audit actions now link across the main teacher/admin workflows.

## Acceptance Flow
1. Admin or teacher publishes or approves an exam with a candidate snapshot.
2. Students enter, save drafts, and submit attempts.
3. Operator closes an ended exam or finalizes remaining active attempts.
4. Teacher completes standard review and recheck review where needed.
5. Teacher uses the ReviewPanel handoff to open score release readiness.
6. Operator resolves remaining blockers:
   - active/non-final attempts
   - pending review answers
   - missing completed scores
   - pending appeals or open rechecks
7. Operator publishes scores only when backend readiness is clear.
8. Student score visibility remains gated by published score state.

## Verification Commands
- Fast gate:
  - `powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1 -SkipFrontendBuild -SkipBackendCompile`
- Frontend build:
  - `cd frontend`
  - `npm run build`
- Full gate:
  - `powershell -ExecutionPolicy Bypass -File scripts\run-quality-gates.ps1`

## Expected Warnings
- `.env.example` uses weak sample MySQL passwords.
- Git may warn that LF will be replaced by CRLF.
- Rollup may warn about `/* #__PURE__ */` comments from `@vueuse/core`.
- Maven may be unavailable; the quality gate falls back to rough backend javac filtering.

## Current Non-Blocking Follow-Up
- Full database migration hardening with real Flyway/Liquibase migration versioning.
- Broader end-to-end automated test data for the complete admin-teacher-student flow.
- Production-grade Redis/object-storage rollout verification under real infrastructure.
- UI copy cleanup for legacy mojibake strings.
- Formal load test against a deployed MySQL/Redis stack instead of local source gates only.
