import { downloadFile, getJson, postJson } from './request';

export interface GradeInfo {
  attemptId: number;
  examName: string;
  subjectName: string;
  score?: number | null;
  submitTime?: string | null;
  status: number;
  scoreVisible?: boolean;
  scoreVisibility?: 'RELEASED' | 'REVOKED' | 'PENDING_RELEASE' | 'PENDING_REVIEW' | 'PENDING_RECHECK' | 'PENDING_FINALIZE' | 'PENDING_SCORE' | string;
  scoreReleaseStatus?: number;
  scorePublishedAt?: string | null;
  scoreRevokedAt?: string | null;
  scoreRevokeReason?: string | null;
  appealOpen?: boolean;
  appealDeadlineAt?: string | null;
  appealWindowDays?: number | null;
  questionCount?: number | string | null;
  answeredCount?: number | string | null;
  unansweredCount?: number | string | null;
}

export interface ExamResult {
  gradeInfo: GradeInfo;
  answers: Array<{
    questionId?: number;
    stem: string;
    questionType: string;
    correctAnswer: string;
    analysis: string;
    studentAnswer: string;
    score: number;
    isCorrect: boolean;
    options?: Array<{
      optionLabel: string;
      optionContent: string;
      correct?: boolean | number;
      isCorrect?: boolean | number;
    }>;
  }>;
}

export interface ScoreAppeal {
  id: number;
  attemptId: number;
  questionId?: number | null;
  userId: number;
  studentName?: string;
  studentNo?: string;
  examId: number;
  examName: string;
  questionStem?: string;
  questionType?: string;
  reason: string;
  status: number;
  teacherReply?: string;
  handlingResult?: 'MAINTAINED' | 'RECHECK_REQUIRED' | 'ADJUSTED_OFFLINE' | string;
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

export interface ScoreAppealEvidenceAnswer {
  answerRecordId: number;
  questionId: number;
  questionType?: string | null;
  stem?: string | null;
  maxScore?: number | string | null;
  reviewStatus?: number | string | null;
  currentScore?: number | string | null;
  oldScore?: number | string | null;
  newScore?: number | string | null;
  reviewComment?: string | null;
  reviewScoreLogId?: number | string | null;
  reviewerId?: number | string | null;
  reviewerName?: string | null;
  reviewedAt?: string | null;
}

export interface ScoreAppealEvidence extends ScoreAppeal {
  requiredRecheckAnswerCount: number | string;
  reviewedRecheckAnswerCount: number | string;
  pendingRecheckAnswerCount: number | string;
  reviewScoreLogCount: number | string;
  recheckOpenedAt?: string | null;
  evidenceAvailable?: number | string | boolean;
  answers: ScoreAppealEvidenceAnswer[];
  logs?: ScoreAppealLog[];
}

export interface WrongQuestion {
    questionId: number;
    examId: number;
    stem: string;
    questionType: string;
    correctAnswer: string;
    analysis: string;
    wrongCount: number;
    lastWrongTime: string;
    options: Array<{
        optionLabel: string;
        optionContent: string;
        correct?: boolean | number;
        isCorrect?: boolean | number;
    }>;
}

export function getGrades() {
  return getJson<GradeInfo[]>('/api/student/grades');
}

export function getExamResult(attemptId: number) {
  return getJson<ExamResult>(`/api/student/exam-result/${attemptId}`);
}

export function getWrongQuestions() {
    return getJson<WrongQuestion[]>('/api/student/wrong-questions');
}

export function getKnowledgePointMastery() {
    return getJson<Record<string, number>>('/api/student/mastery');
}

export function getMyScoreAppeals() {
  return getJson<ScoreAppeal[]>('/api/student/appeals');
}

export function getMyScoreAppealLogs(id: number) {
  return getJson<ScoreAppealLog[]>(`/api/student/appeals/${id}/logs`);
}

export function getMyScoreAppealEvidence(id: number) {
  return getJson<ScoreAppealEvidence>(`/api/student/appeals/${id}/evidence`);
}

export function exportMyScoreAppealLogs(id: number, examName = 'exam') {
  const safeExam = examName.replace(/[\\/:*?"<>|\s]+/g, '-');
  return downloadFile(
    `/api/student/appeals/${id}/logs/export`,
    `${safeExam}-my-score-appeal-log.csv`
  );
}

export function submitScoreAppeal(payload: { attemptId: number; questionId?: number | null; reason: string }) {
  return postJson<ScoreAppeal, typeof payload>('/api/student/appeals', payload);
}
