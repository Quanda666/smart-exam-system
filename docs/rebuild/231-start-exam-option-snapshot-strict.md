# 231. Start Exam Option Snapshot Strictness

## Goal

Keep the active student exam-taking payload fully tied to the published exam snapshot. If an exam has question snapshots, its choice options must also come from `exam_question_option_snapshot` instead of falling back to live question-bank options.

## Scope

- Update `loadExamQuestionOptions` to check whether the exam has question snapshots.
- When question snapshots exist, return only `exam_question_option_snapshot` rows for the question.
- Keep live `question_option` fallback only for legacy exams that have no question snapshots at all.
- Add a quality-gate guard so per-question empty option snapshots cannot silently fall back to mutable question-bank options.

## Three-End Coordination

- Student end: active exam options now match the frozen question content served during answering.
- Teacher end: editing the source question bank or paper after publish no longer changes choice options for published attempts.
- Admin/audit end: taking payloads, result details, wrong-book displays, and AI explanations all follow the snapshot-first option model.

## Acceptance Notes

- Correct option flags remain excluded from the active exam-taking payload.
- Legacy exams without question snapshots still use live `question_option` data.
- If a published snapshot is incomplete, the payload exposes that incompleteness rather than masking it with mutable live data.
