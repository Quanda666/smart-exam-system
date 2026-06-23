# 348 Taking Navigation Monitor Events

## Context

The student taking page already records focus loss, visibility hiding, copy, paste, fullscreen exit, network changes, and heartbeat failures. A few navigation-adjacent actions were still implicit: closing or refreshing the page, using browser back navigation, and opening the context menu. These are useful proctoring signals in a formal online exam, but they should remain risk evidence rather than automatic violation decisions.

## Changes

- `beforeunload` now records `PAGE_UNLOAD_ATTEMPT` before persisting the local monitor queue.
- Browser back navigation now records `HISTORY_BACK_ATTEMPT` before reopening the leave confirmation.
- Context menu usage now records `CONTEXT_MENU`.
- Context-menu listener binding and cleanup are included with the existing exam-taking guards.
- Quality gates now assert that these monitor events remain wired.

## Three-End Impact

- Student end: normal answering is not blocked; the actions are recorded when they happen.
- Teacher end: monitor event history can show page-leave and navigation attempts alongside focus/copy/network events.
- Administrator end: audit exports have stronger evidence for incident review without introducing automatic cheating judgments.

## Acceptance

1. Open an in-progress attempt in the student taking page.
2. Use right-click/context menu; a `CONTEXT_MENU` event is queued and uploaded.
3. Use browser back; a `HISTORY_BACK_ATTEMPT` event is queued before the leave confirmation.
4. Refresh or close the tab; a `PAGE_UNLOAD_ATTEMPT` event is persisted for later upload.
