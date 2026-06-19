import { downloadFile, getJson, postJson } from './request';

export interface PendingReview {
  attemptId: number;
  examId?: number | string;
  examName: string;
  studentName: string;
  pendingCount: number;
  recheckTaskCount?: number | string | null;
  recheckRequired?: boolean | number | string | null;
  recheckAppealCount?: number | string | null;
  questionCount?: number | string | null;
  answeredCount?: number | string | null;
  unansweredCount?: number | string | null;
}

export interface ReviewProgress {
  examId: number;
  examName: string;
  examStatus?: number;
  scoreReleaseStatus?: number;
  attemptCount: number | string;
  pendingAttemptCount: number | string;
  completedAttemptCount: number | string;
  activeAttemptCount?: number | string;
  pendingAnswerCount: number | string;
  pendingRecheckAnswerCount?: number | string;
  reviewedAnswerCount: number | string;
  reviewableAnswerCount: number | string;
  progressPercent: number | string;
  firstPendingAttemptId?: number | string | null;
  firstRecheckAttemptId?: number | string | null;
  recheckAppealCount?: number | string;
  oldestPendingSubmitAt?: string | null;
  latestReviewedAt?: string | null;
  reviewState?: 'PENDING' | 'COMPLETE' | string;
  blocksScoreRelease?: boolean | number | string;
}

export interface ReviewAnswer {
  answerRecordId: number;
  questionId?: number;
  questionType?: string;
  stem: string;
  correctAnswer: string;
  maxScore?: number;
  studentAnswer: string;
  score: number | null;
  isCorrect: boolean | null;
}

export interface ReviewDetail {
  attemptId: number;
  examName: string;
  studentName: string;
  status: number;
  questionCount?: number | string | null;
  answeredCount?: number | string | null;
  unansweredCount?: number | string | null;
  answers: ReviewAnswer[];
}

export interface ReviewPayload {
  answerRecordId: number;
  score: number;
  comment: string;
}

export interface ReviewSubmitResult {
  success: boolean;
  message: string;
  attemptId?: number;
  examId?: number | string;
  examName?: string;
  reviewedCount: number;
  pendingCount: number;
  status: number;
  score: number;
  examPendingReviewAttemptCount?: number | string;
  examPendingReviewAnswerCount?: number | string;
  examActiveAttemptCount?: number | string;
  examUnscoredCompletedAttemptCount?: number | string;
  scoreReleaseStatus?: number | string;
  examReviewComplete?: boolean | number | string;
  scoreReleaseHandoffReady?: boolean | number | string;
  reviewScoreLogIds?: number[];
}

export interface ScoreAppeal {
  id: number;
  attemptId: number;
  examId?: number | string;
  questionId?: number | null;
  userId: number;
  studentName: string;
  studentNo?: string;
  examName: string;
  questionStem?: string;
  questionType?: string;
  reason: string;
  status: number;
  teacherReply?: string;
  handlingResult?: 'MAINTAINED' | 'RECHECK_REQUIRED' | 'ADJUSTED_OFFLINE' | string;
  reopenedReviewCount?: number;
  scoreAppealLogId?: number | null;
  scoreAppealLogIds?: number[];
  handlerName?: string;
  handledAt?: string;
  recheckNote?: string;
  recheckerName?: string;
  recheckedAt?: string;
  createdAt: string;
}

export interface ScoreAppealLog {
  id: number;
  appealId: number;
  attemptId: number;
  examId: number;
  questionId?: number | null;
  userId: number;
  action: 'SUBMIT' | 'REPLY' | 'RECHECK_OPEN' | 'CLOSE_RECHECK' | string;
  statusFrom?: number | null;
  statusTo: number;
  handlingResult?: string | null;
  note?: string | null;
  actorId: number;
  actorName?: string;
  createdAt: string;
}

export interface ScoreAppealRecheckAnswer {
  answerRecordId: number;
  questionId: number;
  questionType?: string;
  stem?: string;
  maxScore?: number | string | null;
  reviewStatus?: number | string | null;
  currentScore?: number | string | null;
  isCorrect?: boolean | number | string | null;
  reviewScoreLogId?: number | null;
  oldScore?: number | string | null;
  newScore?: number | string | null;
  reviewComment?: string | null;
  reviewerId?: number | string | null;
  reviewerName?: string | null;
  reviewedAt?: string | null;
}

export interface ScoreAppealRecheckReadiness extends ScoreAppeal {
  requiredRecheckAnswerCount: number | string;
  pendingRecheckAnswerCount: number | string;
  reviewedRecheckAnswerCount: number | string;
  reviewScoreLogCount: number | string;
  recheckAttemptFinalized: boolean | number | string;
  closeAllowed: boolean | number | string;
  closeBlockers?: string[];
  recheckOpenedAt?: string | null;
  answers: ScoreAppealRecheckAnswer[];
}

export interface ReviewScoreLog {
  id: number;
  attemptId: number;
  answerRecordId: number;
  questionId: number;
  examId: number;
  examName: string;
  userId: number;
  studentName: string;
  oldScore?: number | null;
  newScore: number;
  maxScore: number;
  comment?: string | null;
  reviewerId: number;
  reviewerName?: string | null;
  createdAt: string;
}

export type ReviewTaskType = 'RECHECK' | 'STANDARD';

export function getPendingReviews(examId?: number | string | null, reviewType?: ReviewTaskType | null) {
  const params = new URLSearchParams();
  if (examId !== undefined && examId !== null && String(examId).trim()) params.set('examId', String(examId));
  if (reviewType) params.set('reviewType', reviewType);
  const query = params.toString();
  return getJson<PendingReview[]>(`/api/reviews/pending${query ? `?${query}` : ''}`);
}

export function listReviewProgress(examId?: number | string | null) {
  const params = new URLSearchParams();
  if (examId !== undefined && examId !== null && String(examId).trim()) params.set('examId', String(examId));
  const query = params.toString();
  return getJson<ReviewProgress[]>(`/api/reviews/progress${query ? `?${query}` : ''}`);
}

export function getReviewDetails(attemptId: number) {
  return getJson<ReviewDetail>(`/api/reviews/attempt/${attemptId}`);
}

export function submitReview(attemptId: number, payload: ReviewPayload[]) {
  return postJson<ReviewSubmitResult, ReviewPayload[]>(`/api/reviews/attempt/${attemptId}`, payload);
}

export function listReviewScoreLogs(attemptId: number) {
  return getJson<ReviewScoreLog[]>(`/api/reviews/attempt/${attemptId}/score-logs`);
}

export function exportReviewScoreLogs(attemptId: number, examName = 'exam', studentName = 'student') {
  const safeExam = examName.replace(/[\\/:*?"<>|\s]+/g, '-');
  const safeStudent = studentName.replace(/[\\/:*?"<>|\s]+/g, '-');
  return downloadFile(
    `/api/reviews/attempt/${attemptId}/score-logs/export`,
    `${safeExam}-${safeStudent}-review-score-log.csv`
  );
}

export function listScoreAppeals(
  status?: number | null,
  handlingResult?: string | null,
  appealId?: number | string | null,
  examId?: number | string | null
) {
  const params = new URLSearchParams();
  if (status !== undefined && status !== null) params.set('status', String(status));
  if (handlingResult) params.set('handlingResult', handlingResult);
  if (appealId !== undefined && appealId !== null && String(appealId).trim()) params.set('appealId', String(appealId));
  if (examId !== undefined && examId !== null && String(examId).trim()) params.set('examId', String(examId));
  const query = params.toString();
  return getJson<ScoreAppeal[]>(`/api/reviews/appeals${query ? `?${query}` : ''}`);
}

export function listScoreAppealLogs(id: number) {
  return getJson<ScoreAppealLog[]>(`/api/reviews/appeals/${id}/logs`);
}

export function exportScoreAppealLogs(id: number, examName = 'exam', studentName = 'student') {
  const safeExam = examName.replace(/[\\/:*?"<>|\s]+/g, '-');
  const safeStudent = studentName.replace(/[\\/:*?"<>|\s]+/g, '-');
  return downloadFile(
    `/api/reviews/appeals/${id}/logs/export`,
    `${safeExam}-${safeStudent}-score-appeal-log.csv`
  );
}

export function getScoreAppealRecheckReadiness(id: number) {
  return getJson<ScoreAppealRecheckReadiness>(`/api/reviews/appeals/${id}/recheck/readiness`);
}

export function replyScoreAppeal(id: number, payload: { reply: string; handlingResult: string }) {
  return postJson<ScoreAppeal, typeof payload>(`/api/reviews/appeals/${id}/reply`, payload);
}

export function closeScoreAppealRecheck(id: number, payload: { recheckNote: string }) {
  return postJson<ScoreAppeal, typeof payload>(`/api/reviews/appeals/${id}/recheck/close`, payload);
}
