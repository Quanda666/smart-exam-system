# 108. Score Appeal Active Duplicate Guard

## Background

The score appeal workflow now supports reply, recheck, review reopening, recheck close, and audit logs. One lifecycle gap remained: student appeal submission only blocked duplicates with `status=0`.

After a teacher replied, the appeal becomes `status=1`. For `RECHECK_REQUIRED`, that means the appeal is still in the recheck workflow. Without an active duplicate guard, a student could submit another whole-paper or question-level appeal for the same attempt while the previous one was still being handled.

## Scope

- Block overlapping active score appeals on the backend.
- Disable duplicate whole-paper/question appeal actions in the student result detail drawer.
- Add an index for the active appeal lookup.
- Add quality-gate checks for the invariant.

## Backend Changes

- `ScoreAppealService.submitAppeal`
  - Treats `status IN (0, 1)` as an existing active/replied appeal for duplicate prevention.
  - Uses overlap rules:
    - Whole-paper appeal: blocked if any active appeal already exists for the attempt.
    - Question appeal: blocked if a whole-paper active appeal exists or the same question already has an active appeal.
  - Returns a clear conflict error: `An appeal already exists for this attempt or question`.

- `schema.sql`
  - Adds `idx_score_appeal_active_target (attempt_id, user_id, question_id, status)`.

- `DatabaseMigrationRunner`
  - Adds `ensureScoreAppealActiveTargetIndex`.
  - Backfills the same index for upgraded databases with `addIndexIfMissing`.

## Frontend Changes

- `StudentResultsPanel.vue`
  - Adds `canSubmitAppealFor`.
  - Adds `hasActiveAppealOverlap`.
  - Adds `appealSubmitDisabledReason`.
  - Whole-paper and question appeal buttons now use the same overlap rule as the backend.
  - Direct dialog opening also checks the overlap rule, so disabled UI and click handling stay consistent.

## Three-End Collaboration

- Student end:
  - Students can no longer accidentally create multiple active appeals for the same answer scope.
  - Existing appeal status remains visible in the result detail drawer.

- Teacher end:
  - Teachers review one clear appeal chain per attempt/question scope.
  - Recheck queues are not multiplied by duplicate student submissions.

- Admin end:
  - Appeal audit logs remain easier to interpret because duplicate active chains are rejected before creation.

## Invariant

For a given student attempt:

```text
status IN (0, 1)
```

counts as an existing appeal for duplicate prevention.

Overlap rules:

```text
new whole-paper appeal blocks on any active appeal for the attempt
new question appeal blocks on active whole-paper appeal or active same-question appeal
```

## Quality Gate

`scripts/run-quality-gates.ps1` now checks:

- `ScoreAppealService` uses `status IN (0, 1)`.
- `ScoreAppealService` checks whole-paper/question overlap.
- `schema.sql` and `DatabaseMigrationRunner` include `idx_score_appeal_active_target`.
- `StudentResultsPanel.vue` uses `canSubmitAppealFor`, `hasActiveAppealOverlap`, and `appealSubmitDisabledReason`.

## Acceptance Points

- A student can submit the first valid appeal after scores are released.
- A second whole-paper appeal for the same attempt is rejected while an active appeal exists.
- A question appeal is rejected while a whole-paper active appeal exists.
- A whole-paper appeal is rejected while any question active appeal exists.
- A second appeal for the same question is rejected while the first one is pending, replied, or in recheck.
