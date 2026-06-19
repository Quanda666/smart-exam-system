# 225. Student Result Full Paper Detail

## Goal

Make released result details render the whole paper, including unanswered questions. Showing answer completeness in the summary is not enough if the detail drawer can silently omit questions that have no persisted answer row in older data.

## Scope

- Change `StudentService.getExamResult` answer detail query to start from the expected exam question set.
- Prefer `exam_question_snapshot` for released result details.
- Fall back to current `paper_question` rows only when an older exam has no question snapshot.
- Left join `answer_record` so missing answer rows still appear as unanswered, zero-score rows.
- Keep snapshot option loading and remove the internal `examId` field before returning the response.
- Add quality-gate anchors for full-paper result detail behavior.

## Three-End Coordination

- Student end: result detail now matches the whole released paper and can show every unanswered question.
- Teacher end: review and insight answer-completeness counts align with the student's visible detail.
- Admin/audit end: answer statistics and released detail evidence both come from server-side records and snapshots.

## Acceptance Notes

- Result details are still only available for released, finalized, scored attempts.
- Open recheck appeals still block result detail access.
- Snapshot question content remains the source of truth after publication.
