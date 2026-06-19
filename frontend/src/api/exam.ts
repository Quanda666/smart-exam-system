import { deleteJson, downloadFile, getJson, postJson, putJson } from './request';
import type { PaperInfo } from './paper';
import type { Difficulty, QuestionType } from './question';

export interface ExamInfo {
  id: number;
  paperId: number;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  maxAttempts?: number;
  attemptNo?: number;
  passScore?: number | null;
  status: number;
  paperName?: string;
  subjectName?: string;
  targetSummary?: string;
  attemptCount?: number;
  submittedCount?: number;
  completedAttemptCount?: number;
  unscoredCompletedAttemptCount?: number;
  pendingReviewAttemptCount?: number;
  pendingAnswerReviewCount?: number;
  startedAttemptCount?: number;
  activeAttemptCount?: number;
  nonFinalStartedAttemptCount?: number;
  pendingScoreAppealCount?: number;
  openRecheckAppealCount?: number;
  candidateSnapshotCount?: number;
  questionSnapshotCount?: number;
  scoreReleaseStatus?: number;
  scoreReleaseReady?: boolean | number;
  scoreReleaseBlockers?: string | null;
  scorePublishedBy?: number | null;
  scorePublishedByName?: string | null;
  scorePublishedAt?: string;
  scoreRevokedBy?: number | null;
  scoreRevokedByName?: string | null;
  scoreRevokedAt?: string;
  scorePublishNote?: string | null;
  scoreRevokeReason?: string | null;
  scoreReleaseNote?: string | null;
  publishCandidateCount?: number;
  publishNotifiedStudentCount?: number;
  publishNotifiedAttemptCount?: number;
  approvalLogId?: number | null;
  operationLogId?: number | null;
  lifecycleState?: string;
  lifecycleGroup?: string;
  lifecycleSeverity?: 'OK' | 'INFO' | 'WARN' | 'HIGH' | string;
  lifecycleNextAction?: string;
  lifecycleNextActionType?: string;
  lifecycleActionRequired?: number | boolean;
  lifecycleRisk?: number | boolean;
  lifecycleBlockers?: string[] | string;
  lifecycleBlockerCodes?: string | null;
}

export interface StudentExamInfo {
  attemptId: number;
  examId: number;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  maxAttempts?: number;
  attemptNo?: number;
  passScore?: number | null;
  status: number;
  paperName: string;
  subjectName: string;
  score?: number;
  scoreVisible?: boolean | number;
  scoreVisibility?: 'RELEASED' | 'REVOKED' | 'PENDING_RELEASE' | 'PENDING_REVIEW' | 'PENDING_RECHECK' | 'PENDING_FINALIZE' | 'PENDING_SCORE' | string;
  scoreReleaseStatus?: number;
  scorePublishedAt?: string;
  rulesConfirmedAt?: string | null;
  submitTime?: string;
  serverTime?: string;
  accessStatus?: 'READY' | 'WAITING' | 'IN_PROGRESS' | 'CLOSED' | 'UNPUBLISHED' | 'SUBMITTED' | string;
  canStart?: boolean | number;
  secondsUntilStart?: number;
  secondsUntilEnd?: number | null;
}

export interface ExamTargetStudentInfo {
  userId: number;
  realName: string;
  studentNo?: string;
  className?: string;
  classCode?: string;
}

export interface ExamSnapshotCandidate {
  userId: number;
  sourceType: string;
  sourceId?: number | null;
  realName?: string;
  studentNo?: string;
  className?: string;
  createdAt?: string;
  activeAttemptId?: number | null;
  latestAttemptStatus?: number | null;
  submitType?: string | null;
}

export interface ExamSnapshotQuestion {
  questionId: number;
  questionType: string;
  stem: string;
  correctAnswer?: string;
  analysis?: string;
  score: number;
  sortOrder: number;
  options?: Array<{ optionLabel: string; optionContent: string; sortOrder: number }>;
}

export interface ExamSnapshot {
  exam: ExamInfo & { targets?: Array<Record<string, unknown>> };
  candidateCount: number;
  questionCount: number;
  totalScore: number;
  candidates: ExamSnapshotCandidate[];
  questions: ExamSnapshotQuestion[];
}

export interface ExamPayload {
  paperId: number;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  maxAttempts: number;
  passScore?: number | null;
  classIds?: number[];
  classCourseIds?: number[];
  studentUserIds?: number[];
}

export interface ExamPreflight {
  ok: boolean;
  errors: string[];
  warnings?: string[];
  targetCount?: number;
  candidateCount?: number;
  questionCount?: number;
  totalScore?: number;
  paper?: {
    paperId: number;
    paperName: string;
    subjectName?: string;
    totalScore?: number;
    questionCount?: number;
  };
  targets?: Array<Record<string, unknown>>;
}

export interface ExamApprovalDecisionPayload {
  note?: string;
}

export interface ScoreRevokePayload {
  reason: string;
}

export interface ExamApprovalLog {
  id: number;
  examId: number;
  action: 'SUBMIT' | 'RESUBMIT' | 'APPROVE' | 'REJECT' | 'DIRECT_PUBLISH' | string;
  statusFrom?: number | null;
  statusTo?: number | null;
  candidateCount?: number;
  notifiedStudentCount?: number;
  notifiedAttemptCount?: number;
  note?: string | null;
  actorId: number;
  actorName?: string;
  createdAt: string;
}

export interface ScoreReleaseLog {
  id: number;
  examId: number;
  action: 'PUBLISH' | 'REVOKE' | string;
  statusFrom?: number | null;
  statusTo: number;
  note?: string | null;
  actorId: number;
  actorName?: string;
  visibleAttemptCount: number;
  notifiedStudentCount: number;
  notifiedAttemptCount: number;
  createdAt: string;
}

export interface ExamLifecycleEvent {
  source: 'EXAM' | 'OPERATION_LOG' | 'EXAM_APPROVAL_LOG' | 'SCORE_RELEASE_LOG' | 'MONITOR_ACTION' | 'ATTEMPT' | string;
  sourceId?: number | string | null;
  eventType: string;
  createdAt?: string | null;
  actorId?: number | null;
  actorName?: string | null;
  statusFrom?: number | string | null;
  statusTo?: number | string | null;
  note?: string | null;
  relatedType?: string | null;
  relatedId?: number | string | null;
  details?: Record<string, unknown>;
}

export interface ExamLifecycle {
  exam: ExamInfo & {
    createdAt?: string;
    updatedAt?: string;
    creatorName?: string | null;
    targetCount?: number;
    snapshotTotalScore?: number;
  };
  summary: Record<string, number | string | null | undefined>;
  timeline: ExamLifecycleEvent[];
  approvalLogs?: ExamApprovalLog[];
  scoreReleaseLogs?: ScoreReleaseLog[];
  operationLogs?: Array<Record<string, unknown>>;
  monitorActions?: Array<Record<string, unknown>>;
}

export interface ScoreReleaseBlockerDetail {
  code: string;
  message: string;
  count?: number | string;
  action?: string;
}

export interface ScoreReleaseReadiness {
  examId: number;
  examName?: string;
  status?: number;
  startTime?: string;
  endTime?: string;
  ended?: boolean | number;
  ready?: boolean | number;
  scoreReleaseReady?: boolean | number;
  blockers?: string[];
  scoreReleaseBlockers?: string | null;
  blockerDetails?: ScoreReleaseBlockerDetail[];
  scoreReleaseStatus?: number;
  attemptCount?: number;
  completedAttemptCount?: number;
  scoredCompletedAttemptCount?: number;
  unscoredCompletedAttemptCount?: number;
  pendingReviewAttemptCount?: number;
  pendingAnswerReviewCount?: number;
  startedAttemptCount?: number;
  activeAttemptCount?: number;
  nonFinalStartedAttemptCount?: number;
  pendingScoreAppealCount?: number;
  openRecheckAppealCount?: number;
}

export type ScoreReleaseSafetyState = 'ALL' | 'READY' | 'BLOCKED' | 'RELEASED' | 'REVOKED' | 'ACTION_REQUIRED';

export type ScoreReleaseSafetyExam = ExamInfo & ScoreReleaseReadiness & {
  scoreSafetyState: 'READY' | 'BLOCKED' | 'RELEASED' | 'REVOKED' | string;
  nextAction?: string | null;
};

export interface ScoreReleaseSafetySummary {
  total: number;
  ready: number;
  blocked: number;
  released: number;
  revoked: number;
  actionRequired: number;
  blockerCounts?: Record<string, number>;
}

export interface ScoreReleaseSafetyResult {
  state: ScoreReleaseSafetyState;
  summary: ScoreReleaseSafetySummary;
  page: PageResult<ScoreReleaseSafetyExam>;
}

export type ExamLifecycleHealthState =
  | 'ALL'
  | 'ACTION_REQUIRED'
  | 'APPROVAL'
  | 'WAITING'
  | 'RUNNING'
  | 'REVIEW'
  | 'SCORE_READY'
  | 'RELEASED'
  | 'RISK';

export type ExamLifecycleHealthExam = ExamInfo & {
  examId?: number;
  targetCount?: number;
  notStartedAttemptCount?: number;
  scoredCompletedAttemptCount?: number;
  forcedSubmitCount?: number;
  timeoutPressureCount?: number;
  deadlinePassedActiveCount?: number;
  monitorSessionCount?: number;
  offlineMonitorCount?: number;
  highRiskMonitorCount?: number;
  monitorEventCount?: number;
  staleDraftCount?: number;
};

export interface ExamLifecycleHealthSummary {
  total: number;
  actionRequired: number;
  approval: number;
  waiting: number;
  running: number;
  review: number;
  scoreReady: number;
  released: number;
  risk: number;
  blockerCounts?: Record<string, number>;
}

export interface ExamLifecycleHealthResult {
  state: ExamLifecycleHealthState;
  summary: ExamLifecycleHealthSummary;
  page: PageResult<ExamLifecycleHealthExam>;
}

export interface ExamLifecycleHandoffBlocker {
  code: string;
  count: number;
}

export interface ExamLifecycleHandoffAction {
  examId: number;
  examName: string;
  paperName?: string;
  subjectName?: string;
  lifecycleState: string;
  lifecycleGroup: string;
  lifecycleSeverity: string;
  lifecycleActionRequired?: number | boolean;
  lifecycleNextAction?: string;
  lifecycleNextActionType?: string;
  lifecycleBlockerCodes?: string;
  targetPath?: string;
  activeAttemptCount?: number;
  completedAttemptCount?: number;
  pendingReviewAttemptCount?: number;
  pendingAnswerReviewCount?: number;
  pendingScoreAppealCount?: number;
  openRecheckAppealCount?: number;
  offlineMonitorCount?: number;
  highRiskMonitorCount?: number;
  staleDraftCount?: number;
  timeoutPressureCount?: number;
  deadlinePassedActiveCount?: number;
}

export interface ExamLifecycleHealthHandoff {
  generatedAt: string;
  keyword?: string | null;
  state: ExamLifecycleHealthState;
  role?: string | null;
  summary: ExamLifecycleHealthSummary;
  filteredTotal: number;
  actionTotal: number;
  topBlockers: ExamLifecycleHandoffBlocker[];
  groupCounts: Record<string, number>;
  stateCounts: Record<string, number>;
  severityCounts: Record<string, number>;
  actionRows: ExamLifecycleHandoffAction[];
}

export type ExamLifecycleHandoffAudience = 'SELF' | 'OPERATORS';

export interface ExamLifecycleHandoffNotifyResult {
  audience: ExamLifecycleHandoffAudience;
  state: ExamLifecycleHealthState;
  keyword?: string | null;
  link: string;
  recipientCount: number;
  notificationIds: number[];
  notificationCount: number;
  handoff: ExamLifecycleHealthHandoff;
}

export interface ExamApprovalQueueItem extends ExamInfo {
  createdAt: string;
  createdBy: number;
  creatorName?: string;
  targetCount: number;
  questionCount: number;
  totalScore: number;
  pendingHours?: number;
  latestApprovalAction?: string | null;
  latestApprovalNote?: string | null;
  latestApprovalAt?: string | null;
  riskFlags?: string | null;
}

export interface ApprovalReminderResult {
  sent: boolean;
  enabled: boolean;
  overdueHours: number;
  cooldownHours: number;
  cooldownActive?: boolean;
  overdueExamCount: number;
  adminCount: number;
  status?: string;
  message?: string;
  lastReminderAt?: string | null;
  triggerSource?: string;
  scheduleIntervalMinutes?: number;
  nodeId?: string | null;
  durationMs?: number | null;
  reminderLogId?: number | null;
}

export interface ApprovalReminderLog {
  id: number;
  triggeredBy: number;
  triggeredByName?: string;
  overdueHours: number;
  cooldownHours: number;
  overdueExamCount: number;
  recipientCount: number;
  status: string;
  triggerSource?: string;
  nodeId?: string | null;
  durationMs?: number | null;
  message?: string | null;
  createdAt: string;
}

export interface AnswerPayload {
  answers: Record<number, string>;
  submitToken?: string;
}

export interface StartExamPayload {
  rulesConfirmed?: boolean;
}

export interface DraftCacheStatus {
  enabled: boolean;
  available: boolean;
  writeBackEnabled: boolean;
  ttlSeconds: number;
  dirtyCount: number;
  reads: number;
  hits: number;
  writes: number;
  deletes: number;
  errors: number;
  flushSuccess: number;
  flushSkipped: number;
  lastFlushAtEpochMillis: number;
  lastFlushChecked: number;
  lastFlushFlushed: number;
  lastFlushSkipped: number;
  lastFlushCleaned: number;
  flushBatchSize: number;
  dirtyWarningThreshold: number;
  dirtyHighThreshold: number;
  errorWarningThreshold: number;
  staleFlushWarningSeconds: number;
  alertLevel: 'OK' | 'WARN' | 'HIGH' | 'DISABLED' | string;
  alertMessage: string;
  activeAttempts: number;
  dbDrafts: number;
}

export interface ExamTakingOption {
  optionLabel: string;
  optionContent: string;
  sortOrder?: number;
}

export interface ExamTakingQuestion {
  id?: number;
  questionId: number;
  score: number;
  sortOrder: number;
  questionType?: QuestionType | string;
  difficulty?: Difficulty | string | null;
  stem?: string;
  options?: ExamTakingOption[];
}

export interface ExamDetail extends Omit<PaperInfo, 'questions'> {
  examName: string;
  durationMinutes: number;
  maxAttempts?: number;
  passScore?: number | null;
  questions: ExamTakingQuestion[];
  remainingSeconds?: number;
  draftAnswers?: string | null;
  draftRevision?: number;
  draftClientDraftId?: string | null;
  draftSavedAt?: string | null;
  draftSavedCount?: number;
  draftSource?: 'DB' | 'REDIS' | string;
  rulesConfirmedAt?: string | null;
  lastHeartbeatAt?: string | null;
  lastDraftSavedAt?: string | null;
  submitted?: boolean;
  autoSubmitted?: boolean;
  alreadySubmitted?: boolean;
  responseReplayed?: boolean;
  submitType?: 'MANUAL' | 'TIMEOUT' | 'FORCED';
  submitToken?: string | null;
  submitTime?: string | null;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export function listTeacherExams(query?: { keyword?: string; status?: number | null; examId?: number | null; page?: number; size?: number }) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.status !== undefined && query.status !== null) params.set('status', String(query.status));
  if (query?.examId !== undefined && query.examId !== null) params.set('examId', String(query.examId));
  if (query?.page !== undefined) params.set('page', String(query.page));
  if (query?.size !== undefined) params.set('size', String(query.size));
  const value = params.toString();
  return getJson<PageResult<ExamInfo>>(`/api/exams/teacher${value ? `?${value}` : ''}`);
}

export function listExamApprovalQueue(query?: {
  keyword?: string;
  creatorKeyword?: string;
  status?: number | null;
  startFrom?: string;
  startTo?: string;
  risk?: string;
  examId?: number | null;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.creatorKeyword) params.set('creatorKeyword', query.creatorKeyword);
  if (query?.status !== undefined && query.status !== null) params.set('status', String(query.status));
  if (query?.startFrom) params.set('startFrom', query.startFrom);
  if (query?.startTo) params.set('startTo', query.startTo);
  if (query?.risk) params.set('risk', query.risk);
  if (query?.examId !== undefined && query.examId !== null) params.set('examId', String(query.examId));
  if (query?.page !== undefined) params.set('page', String(query.page));
  if (query?.size !== undefined) params.set('size', String(query.size));
  const value = params.toString();
  return getJson<PageResult<ExamApprovalQueueItem>>(`/api/exams/approvals${value ? `?${value}` : ''}`);
}

export function sendApprovalOverdueReminders() {
  return postJson<ApprovalReminderResult>('/api/exams/approvals/reminders');
}

export function listApprovalReminderLogs(page = 1, size = 10, query?: { logId?: number | string | null }) {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  if (query?.logId !== undefined && query.logId !== null && String(query.logId).trim()) {
    params.set('logId', String(query.logId));
  }
  return getJson<PageResult<ApprovalReminderLog>>(`/api/exams/approvals/reminders?${params.toString()}`);
}

export function exportApprovalReminderLogs() {
  return downloadFile('/api/exams/approvals/reminders/export', 'approval-reminder-log.csv');
}

export function listStudentExams(page = 1, size = 10) {
  return getJson<PageResult<StudentExamInfo>>(`/api/exams/student?page=${page}&size=${size}`);
}

export function listExamTargetStudents() {
  return getJson<ExamTargetStudentInfo[]>('/api/exams/targets/students');
}

export function createExam(payload: ExamPayload) {
  return postJson<ExamInfo, ExamPayload>('/api/exams', payload);
}

export function preflightExam(payload: ExamPayload) {
  return postJson<ExamPreflight, ExamPayload>('/api/exams/preflight', payload);
}

export function getExamSnapshot(id: number) {
  return getJson<ExamSnapshot>(`/api/exams/${id}/snapshot`);
}

export function repairExamSnapshot(id: number) {
  return postJson<{
    id: number;
    examName?: string;
    candidateSnapshotBefore: number;
    candidateSnapshotAfter: number;
    questionSnapshotBefore: number;
    questionSnapshotAfter: number;
    targetStudentCount: number;
    insertedCandidateSnapshots: number;
    insertedAttempts: number;
    questionSnapshotRebuilt: boolean;
    operationLogId?: number | null;
  }>(`/api/exams/${id}/snapshot/repair`);
}

export function exportExamSnapshot(id: number, examName = 'exam') {
  const safeExam = examName.replace(/[\\/:*?"<>|\s]+/g, '-');
  return downloadFile(
    `/api/exams/${id}/snapshot/export`,
    `${safeExam}-snapshot-evidence.csv`
  );
}

export function getExamApprovalLogs(id: number) {
  return getJson<ExamApprovalLog[]>(`/api/exams/${id}/approval-logs`);
}

export function exportExamApprovalLogs(id: number, examName = 'exam') {
  const safeExam = examName.replace(/[\\/:*?"<>|\s]+/g, '-');
  return downloadFile(
    `/api/exams/${id}/approval-logs/export`,
    `${safeExam}-approval-log.csv`
  );
}

export function getScoreReleaseLogs(id: number) {
  return getJson<ScoreReleaseLog[]>(`/api/exams/${id}/score-release-logs`);
}

export function getExamLifecycle(id: number) {
  return getJson<ExamLifecycle>(`/api/exams/${id}/lifecycle`);
}

export function getExamLifecycleHealth(query?: {
  keyword?: string;
  state?: ExamLifecycleHealthState;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.state) params.set('state', query.state);
  if (query?.page !== undefined) params.set('page', String(query.page));
  if (query?.size !== undefined) params.set('size', String(query.size));
  const value = params.toString();
  return getJson<ExamLifecycleHealthResult>(`/api/exams/lifecycle/health${value ? `?${value}` : ''}`);
}

export function exportExamLifecycleHealth(query?: { keyword?: string; state?: ExamLifecycleHealthState }) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.state) params.set('state', query.state);
  const value = params.toString();
  return downloadFile(
    `/api/exams/lifecycle/health/export${value ? `?${value}` : ''}`,
    `exam-lifecycle-health_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

export function getExamLifecycleHealthHandoff(query?: { keyword?: string; state?: ExamLifecycleHealthState }) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.state) params.set('state', query.state);
  const value = params.toString();
  return getJson<ExamLifecycleHealthHandoff>(`/api/exams/lifecycle/health/handoff${value ? `?${value}` : ''}`);
}

export function notifyExamLifecycleHealthHandoff(query?: {
  keyword?: string;
  state?: ExamLifecycleHealthState;
  audience?: ExamLifecycleHandoffAudience;
}) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.state) params.set('state', query.state);
  if (query?.audience) params.set('audience', query.audience);
  const value = params.toString();
  return postJson<ExamLifecycleHandoffNotifyResult>(`/api/exams/lifecycle/health/handoff/notify${value ? `?${value}` : ''}`);
}

export function exportScoreReleaseLogs(id: number, examName = 'exam') {
  const safeExam = examName.replace(/[\\/:*?"<>|\s]+/g, '-');
  return downloadFile(
    `/api/exams/${id}/score-release-logs/export`,
    `${safeExam}-score-release-log.csv`
  );
}

export interface ExamUpdatePayload {
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  maxAttempts: number;
  passScore?: number | null;
}

export function updateExam(id: number, payload: ExamUpdatePayload) {
  return putJson<ExamInfo, ExamUpdatePayload>(`/api/exams/${id}`, payload);
}

export function deleteExam(id: number) {
  return deleteJson<{ id: number; deleted: boolean; operationLogId?: number | null }>(`/api/exams/${id}`);
}

export function closeExam(id: number) {
  return putJson<{ id: number; operationLogId?: number | null }>(`/api/exams/${id}/close`);
}

export function approveExam(id: number, payload?: ExamApprovalDecisionPayload) {
  return postJson<ExamInfo, ExamApprovalDecisionPayload | undefined>(`/api/exams/${id}/approve`, payload);
}

export function rejectExam(id: number, payload: ExamApprovalDecisionPayload) {
  return postJson<ExamInfo, ExamApprovalDecisionPayload>(`/api/exams/${id}/reject`, payload);
}

export function getScoreReleaseReadiness(id: number) {
  return getJson<ScoreReleaseReadiness>(`/api/exams/${id}/scores/readiness`);
}

export function getScoreReleaseSafety(query?: {
  keyword?: string;
  state?: ScoreReleaseSafetyState;
  page?: number;
  size?: number;
}) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.state) params.set('state', query.state);
  if (query?.page !== undefined) params.set('page', String(query.page));
  if (query?.size !== undefined) params.set('size', String(query.size));
  const value = params.toString();
  return getJson<ScoreReleaseSafetyResult>(`/api/exams/scores/safety${value ? `?${value}` : ''}`);
}

export function exportScoreReleaseSafety(query?: { keyword?: string; state?: ScoreReleaseSafetyState }) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.state) params.set('state', query.state);
  const value = params.toString();
  return downloadFile(
    `/api/exams/scores/safety/export${value ? `?${value}` : ''}`,
    `score-release-safety_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

export function publishExamScores(id: number) {
  return postJson<{
    examId: number;
    status: number;
    publishedAt?: string;
    completedAttempts?: number;
    pendingReviewAttempts?: number;
    activeAttempts?: number;
    notifiedStudents?: number;
    notifiedAttempts?: number;
    scoreReleaseLogId?: number | null;
    publishNote?: string | null;
    scoreReleaseReady?: boolean | number;
    scoreReleaseBlockers?: string | null;
    pendingAnswerReviewCount?: number;
  }>(`/api/exams/${id}/scores/publish`);
}

export function recalculateMissingScores(id: number) {
  return postJson<{
    examId: number;
    scoreReleaseStatus?: number;
    missingBefore: number;
    eligibleAttempts: number;
    recalculatedAttempts: number;
    missingAfter: number;
    skippedPendingReviewAttempts: number;
    skippedNoAnswerAttempts: number;
    totalRecalculatedScore?: number | string;
    scoreReleaseReady?: boolean | number;
    scoreReleaseBlockers?: string | null;
    blockerDetails?: ScoreReleaseBlockerDetail[];
    operationLogId?: number | null;
  }>(`/api/exams/${id}/scores/recalculate-missing`);
}

export function finalizeActiveExamAttempts(id: number) {
  return postJson<{
    examId: number;
    scoreReleaseStatus?: number;
    activeBefore: number;
    pendingReviewBefore: number;
    completedBefore: number;
    eligibleActiveAttempts: number;
    forcedSubmittedAttempts: number;
    completedByFinalize: number;
    pendingReviewByFinalize: number;
    failedAttempts: number;
    failureMessages?: string[];
    activeAfter?: number;
    pendingReviewAfter?: number;
    completedAfter?: number;
    nonFinalAfter?: number;
    scoreReleaseReady?: boolean | number;
    scoreReleaseBlockers?: string | null;
    blockerDetails?: ScoreReleaseBlockerDetail[];
    operationLogId?: number | null;
  }>(`/api/exams/${id}/attempts/finalize-active`);
}

export function revokeExamScores(id: number, payload: ScoreRevokePayload) {
  return postJson<{
    examId: number;
    status: number;
    revokedAt?: string;
    visibleAttemptsBeforeRevoke?: number;
    notifiedStudents?: number;
    notifiedAttempts?: number;
    scoreReleaseLogId?: number | null;
    revokeReason?: string;
    publishNote?: string | null;
    note?: string;
  }, ScoreRevokePayload>(`/api/exams/${id}/scores/revoke`, payload);
}

export function exportExamScores(examId: number, examName?: string) {
  return downloadFile(`/api/exams/${examId}/scores/export`, `${examName || 'exam'}-成绩单.csv`);
}

export function startExam(attemptId: number, payload: StartExamPayload = {}) {
  return postJson<ExamDetail, StartExamPayload>(`/api/exams/attempt/${attemptId}/start`, payload);
}

export function submitExam(attemptId: number, payload: AnswerPayload) {
  return postJson<{
    success: boolean;
    message: string;
    status: number;
    scoreVisible?: boolean;
    scoreVisibility?: string;
    alreadySubmitted?: boolean;
    submitToken?: string | null;
    submitPayloadHash?: string | null;
    submitTime?: string | null;
    responseReplayed?: boolean;
    submitTokenMismatch?: boolean;
    submitPayloadMismatch?: boolean;
    draftFlushedBeforeSubmit?: boolean;
    questionCount?: number;
    answeredCount?: number;
    unansweredCount?: number;
  }, AnswerPayload>(
    `/api/exams/attempt/${attemptId}/submit`,
    payload
  );
}

export function examAttemptHeartbeat(attemptId: number) {
  return postJson<{
    status: number;
    remainingSeconds: number;
    serverTime?: number;
    autoSubmitted?: boolean;
    forcedSubmitted?: boolean;
    submitted?: boolean;
    submitTime?: string | null;
    submitType?: 'MANUAL' | 'TIMEOUT' | 'FORCED' | string;
    message?: string;
    draftRevision?: number;
    draftSavedAt?: string | null;
    draftSavedCount?: number;
    draftSource?: string;
    lastHeartbeatAt?: string | null;
    lastDraftSavedAt?: string | null;
  }>(`/api/exams/attempt/${attemptId}/heartbeat`);
}

export function forceSubmitAttempt(attemptId: number) {
  return postJson<{
    success: boolean;
    message: string;
    status: number;
    submitType: string;
    submitTime?: string | null;
    scoreVisible?: boolean;
    scoreVisibility?: string;
    alreadySubmitted?: boolean;
    submitted?: boolean;
    forcedSubmitted?: boolean;
    responseReplayed?: boolean;
    questionCount?: number;
    answeredCount?: number;
    unansweredCount?: number;
  }>(
    `/api/exams/attempt/${attemptId}/force-submit`
  );
}

export function getDraftCacheStatus() {
  return getJson<DraftCacheStatus>('/api/exams/draft-cache/status');
}

export function saveExamDraft(attemptId: number, answersJson: string, meta?: { clientDraftId?: string; revision?: number }) {
  return postJson<{
    saved: boolean;
    revision?: number;
    serverRevision?: number;
    stale?: boolean;
    savedAt?: string;
    clientDraftId?: string | null;
    draftSource?: string;
    cacheEnabled?: boolean;
    reason?: string;
  }, { answers: string; clientDraftId?: string; revision?: number }>(
    `/api/exams/attempt/${attemptId}/save`,
    { answers: answersJson, clientDraftId: meta?.clientDraftId, revision: meta?.revision }
  );
}
