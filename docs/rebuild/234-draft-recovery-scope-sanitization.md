# 234. Draft Recovery Scope Sanitization

## Goal

Prevent historical dirty drafts from being rehydrated into the student exam-taking page. New draft saves and Redis write-back are already strict; recovery also needs a defensive cleanup layer for old database or cache entries.

## Scope

- Sanitize `startExam` `draftAnswers` before returning them to the frontend.
- Keep blank draft payloads blank.
- Convert malformed draft JSON to `{}` for recovery.
- Remove non-numeric or out-of-scope question ids from recovered draft JSON.
- Reuse the same snapshot-first attempt question set used by save, submit, and Redis write-back validation.
- Add quality-gate anchors so `startExam` cannot directly return raw `bestDraft.answers`.

## Three-End Coordination

- Student end: re-entering an exam no longer rehydrates forged or stale question ids.
- Teacher end: auto-submit and manual submit are less likely to be disrupted by old dirty recovery payloads.
- Admin/audit end: persisted historical bad drafts are contained at recovery time without requiring a destructive migration.

## Acceptance Notes

- This batch does not rewrite old database drafts.
- Strict save and Redis write-back validation remain unchanged.
- Final submission validation remains authoritative.
