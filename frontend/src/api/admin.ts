import { deleteJson, downloadFile, getJson, postJson, putJson } from './request';

export interface SystemUser {
  id: number;
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  status: number;
  roleCodes?: string;
  studentNo?: string;
  classId?: number;
  className?: string;
  classType?: string;
  classMemberships?: string;
  enrollmentYear?: string;
  studentCollege?: string;
  studentMajor?: string;
  teacherNo?: string;
  teacherStatus?: number;
  hireDate?: string;
  teacherTitle?: string;
  teacherCollege?: string;
  introduction?: string;
  teachingAssignments?: string;
  createdAt?: string;
  updatedAt?: string;
  operationLogId?: number | null;
}

export interface UserOperationResult {
  id: number;
  status?: number;
  deleted?: boolean;
  teacherReviewApproved?: boolean;
  operationLogId?: number | null;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export type OpsDrilldownType =
  | 'TIMEOUT_PRESSURE'
  | 'DEADLINE_PASSED_ACTIVE'
  | 'OFFLINE_MONITOR'
  | 'HIGH_RISK_MONITOR'
  | 'STALE_DB_DRAFTS'
  | 'DIRTY_DRAFTS'
  | 'FORCED_SUBMITS_TODAY';

export interface OpsDrilldownItem {
  examId?: number;
  examName?: string;
  examStartTime?: string;
  examEndTime?: string;
  attemptId?: number;
  studentUserId?: number;
  studentName?: string;
  studentNo?: string;
  attemptStatus?: number;
  attemptStartTime?: string;
  submitTime?: string;
  submitType?: string;
  note?: string;
  sessionId?: number;
  monitorStatus?: string;
  riskScore?: number;
  eventCount?: number;
  lastEventType?: string;
  lastEventAt?: string;
  lastHeartbeatAt?: string;
  serverDeadline?: string;
  secondsToDeadline?: number;
  draftRevision?: number | string;
  draftSavedCount?: number;
  draftUpdatedAt?: string;
  cacheKey?: string;
  clientDraftId?: string;
}

export interface UserQuery {
  keyword?: string;
  role?: string;
  status?: number | null;
  teacherStatus?: number | null;
  userId?: number | string | null;
  page?: number;
  size?: number;
}

export interface SystemRole {
  id: number;
  roleCode: string;
  roleName: string;
  status: number;
  userCount: number;
  pages: string[];
  availablePages?: RolePageOption[];
}

export interface RolePageOption {
  title: string;
  path: string;
  icon?: string;
  roles: string[];
}

export interface RolePageUpdateResult {
  roleCode: string;
  pages: string[];
  operationLogId?: number | null;
}

export interface OperationLog {
  id: number;
  operator_id?: number;
  operator_name?: string;
  operatorId?: number;
  operatorName?: string;
  action?: string;
  target?: string;
  detail?: string;
  ip?: string;
  created_at?: string;
  createdAt?: string;
}

export interface OperationLogQuery {
  logId?: number | string;
  keyword?: string;
  action?: string;
  target?: string;
  startFrom?: string;
  startTo?: string;
}

export interface LoginAuditLog {
  id: number;
  operatorId?: number | null;
  operatorName?: string | null;
  action?: string;
  target?: string;
  detail?: string;
  ip?: string;
  createdAt?: string;
  success?: number | boolean;
}

export interface LoginAuditQuery {
  logId?: number | string;
  keyword?: string;
  action?: string;
  operatorId?: number | string;
  success?: boolean | null;
  startFrom?: string;
  startTo?: string;
}

export interface AiUsageLog {
  id: number;
  userId?: number;
  userName?: string;
  scene?: string;
  prompt?: string;
  response?: string;
  success: number | boolean;
  errorMessage?: string;
  createdAt?: string;
}

export interface AiUsageLogQuery {
  scene?: string;
  success?: boolean | null;
  keyword?: string;
  startFrom?: string;
  startTo?: string;
}

export interface ScoreReleaseAuditLog {
  id: number;
  examId: number;
  examName?: string;
  paperName?: string;
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

export interface ScoreReleaseAuditQuery {
  logId?: number | string;
  keyword?: string;
  action?: string;
  startFrom?: string;
  startTo?: string;
}

export interface ExamApprovalAuditLog {
  id: number;
  examId: number;
  examName?: string;
  paperName?: string;
  action: 'SUBMIT' | 'RESUBMIT' | 'APPROVE' | 'REJECT' | 'DIRECT_PUBLISH' | string;
  statusFrom?: number | null;
  statusTo: number;
  note?: string | null;
  actorId: number;
  actorName?: string;
  candidateCount: number;
  notifiedStudentCount: number;
  notifiedAttemptCount: number;
  createdAt: string;
}

export interface ExamApprovalAuditQuery {
  logId?: number | string;
  keyword?: string;
  action?: string;
  startFrom?: string;
  startTo?: string;
}

export interface ApprovalReminderAuditLog {
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

export interface ApprovalReminderAuditQuery {
  logId?: number | string;
  keyword?: string;
  status?: string;
  triggerSource?: string;
  startFrom?: string;
  startTo?: string;
}

export interface SystemConfigAuditLog {
  id: number;
  configKey: string;
  oldValue?: string | null;
  newValue: string;
  valueType: string;
  category: string;
  actorId: number;
  actorName?: string | null;
  actorUsername?: string | null;
  createdAt: string;
}

export interface SystemConfigAuditQuery {
  logId?: number | string;
  keyword?: string;
  category?: string;
  configKey?: string;
  actorId?: number | string;
  startFrom?: string;
  startTo?: string;
}

export interface QuestionReviewAuditLog {
  id: number;
  questionId: number;
  versionNo: number;
  actionType: string;
  fromStatus?: number | null;
  toStatus?: number | null;
  fromReviewStatus?: string | null;
  toReviewStatus?: string | null;
  comment?: string | null;
  operatedBy?: number | null;
  operatorName?: string | null;
  operatorUsername?: string | null;
  operatedAt?: string;
  subjectId?: number | null;
  subjectName?: string | null;
  questionType?: string | null;
  difficulty?: string | null;
  questionStem?: string | null;
  questionStatus?: number | null;
  currentReviewStatus?: string | null;
  questionDeleted?: number | boolean;
  createdBy?: number | null;
  creatorName?: string | null;
  creatorUsername?: string | null;
}

export interface QuestionReviewAuditQuery {
  logId?: number | string;
  questionId?: number | string;
  keyword?: string;
  actionType?: string;
  reviewStatus?: string;
  subjectId?: number | string;
  operatorId?: number | string;
  startFrom?: string;
  startTo?: string;
}

export interface ScoreAppealAuditLog {
  id: number;
  appealId: number;
  attemptId: number;
  examId: number;
  examName?: string;
  questionId?: number | null;
  questionStem?: string;
  userId: number;
  studentName?: string;
  studentNo?: string;
  action: 'SUBMIT' | 'REPLY' | 'RECHECK_OPEN' | 'CLOSE_RECHECK' | string;
  statusFrom?: number | null;
  statusTo: number;
  handlingResult?: string | null;
  note?: string | null;
  actorId: number;
  actorName?: string;
  createdAt: string;
}

export interface ScoreAppealAuditQuery {
  logId?: number | string;
  keyword?: string;
  action?: string;
  handlingResult?: string;
  startFrom?: string;
  startTo?: string;
}

export interface ReviewScoreAuditLog {
  id: number;
  attemptId: number;
  answerRecordId: number;
  questionId: number;
  questionStem?: string | null;
  examId: number;
  examName?: string;
  userId: number;
  studentName?: string;
  studentNo?: string | null;
  oldScore?: number | null;
  newScore: number;
  maxScore: number;
  comment?: string | null;
  reviewerId: number;
  reviewerName?: string;
  createdAt: string;
}

export interface ReviewScoreAuditQuery {
  logId?: number | string;
  keyword?: string;
  examId?: number | string;
  reviewerId?: number | string;
  startFrom?: string;
  startTo?: string;
}

export interface NotificationAuditLog {
  id: number;
  userId: number;
  username?: string;
  realName?: string;
  title: string;
  content?: string | null;
  type?: string;
  link?: string | null;
  relatedType?: string | null;
  relatedId?: number | null;
  isRead: number;
  createdAt: string;
}

export interface NotificationAuditQuery {
  notificationId?: number | string;
  keyword?: string;
  type?: string;
  relatedType?: string;
  relatedId?: number | string;
  read?: boolean | null;
  userId?: number | string;
  startFrom?: string;
  startTo?: string;
}

export interface RoleStat {
  roleCode: string;
  roleName: string;
  userCount: number;
}

export interface SubjectStat {
  subjectName: string;
  examCount: number;
  attemptCount: number;
  avgScore: number;
}

export interface AnalysisOverview {
  userCount: number;
  questionCount: number;
  paperCount: number;
  examCount: number;
  attemptCount: number;
  completedCount: number;
  averageScore: number;
  roleDistribution: RoleStat[];
  subjectStats: SubjectStat[];
  scoreDistribution: Record<string, number>;
}

function userQueryString(query: UserQuery = {}) {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.role) params.set('role', query.role);
  if (query.status !== undefined && query.status !== null) params.set('status', String(query.status));
  if (query.teacherStatus !== undefined && query.teacherStatus !== null) params.set('teacherStatus', String(query.teacherStatus));
  if (query.userId !== undefined && query.userId !== null && query.userId !== '') params.set('userId', String(query.userId));
  if (query.page) params.set('page', String(query.page));
  if (query.size) params.set('size', String(query.size));
  const value = params.toString();
  return value ? `?${value}` : '';
}

export function listUsers(query?: UserQuery) {
  return getJson<PageResult<SystemUser>>(`/api/system/users${userQueryString(query)}`);
}

export function fetchUserSummary() {
  return getJson<Record<string, number>>('/api/system/users/summary');
}

export function updateUserStatus(id: number, status: number) {
  return putJson<UserOperationResult>(`/api/system/users/${id}/status?status=${status}`);
}

export function rejectTeacherReview(id: number, reason: string) {
  return putJson<UserOperationResult>(`/api/system/users/${id}/teacher-review/reject`, { reason });
}

export function resetUserPassword(id: number, newPassword: string) {
  return putJson<UserOperationResult>(`/api/system/users/${id}/password`, { newPassword });
}

export function deleteUser(id: number) {
  return deleteJson<UserOperationResult>(`/api/system/users/${id}`);
}

export interface CreateUserPayload {
  username: string;
  password: string;
  realName: string;
  roleType: string;
  studentNo?: string;
  classId?: number | null;
  electiveClassIds?: number[];
  enrollmentYear?: string;
  college?: string;
  major?: string;
  teacherNo?: string;
  hireDate?: string | null;
  title?: string;
  introduction?: string;
  phone?: string;
  email?: string;
}

export function createUser(payload: CreateUserPayload) {
  return postJson<SystemUser>('/api/system/users', payload);
}

export interface UpdateUserPayload {
  realName: string;
  roleType: string;
  studentNo?: string;
  classId?: number | null;
  electiveClassIds?: number[];
  enrollmentYear?: string;
  college?: string;
  major?: string;
  teacherNo?: string;
  hireDate?: string | null;
  title?: string;
  introduction?: string;
  phone?: string;
  email?: string;
}

export function updateUser(id: number, payload: UpdateUserPayload) {
  return putJson<SystemUser>(`/api/system/users/${id}`, payload);
}

export function listRoles() {
  return getJson<SystemRole[]>('/api/system/roles');
}

export function updateRolePages(roleCode: string, pages: string[]) {
  return putJson<RolePageUpdateResult>(`/api/system/roles/${roleCode}/pages`, { pages });
}

export function listOpsDrilldown(type: OpsDrilldownType, page = 1, size = 10) {
  const params = new URLSearchParams();
  params.set('type', type);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<OpsDrilldownItem>>(`/api/overview/admin/ops-drilldown?${params.toString()}`);
}

export function exportOpsDrilldown(type: OpsDrilldownType) {
  const params = new URLSearchParams();
  params.set('type', type);
  return downloadFile(
    `/api/overview/admin/ops-drilldown/export?${params.toString()}`,
    `ops-drilldown-${type.toLowerCase()}_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

export function listOperationLogs(page = 1, size = 10, query: OperationLogQuery = {}) {
  const params = operationLogParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<OperationLog>>(`/api/monitor/logs?${params.toString()}`);
}

export function exportOperationLogs(query: OperationLogQuery = {}) {
  const params = operationLogParams(query);
  return downloadFile(
    `/api/monitor/logs/export?${params.toString()}`,
    `操作日志_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function operationLogParams(query: OperationLogQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.action) params.set('action', query.action);
  if (query.target) params.set('target', query.target);
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listLoginAuditLogs(page = 1, size = 10, query: LoginAuditQuery = {}) {
  const params = loginAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<LoginAuditLog>>(`/api/monitor/login-logs?${params.toString()}`);
}

export function exportLoginAuditLogs(query: LoginAuditQuery = {}) {
  const params = loginAuditParams(query);
  return downloadFile(
    `/api/monitor/login-logs/export?${params.toString()}`,
    `login-audit_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function loginAuditParams(query: LoginAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.action) params.set('action', query.action);
  if (query.operatorId !== undefined && query.operatorId !== null) params.set('operatorId', String(query.operatorId));
  if (query.success !== undefined && query.success !== null) params.set('success', String(query.success));
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listAiUsageLogs(page = 1, size = 10, query: AiUsageLogQuery = {}) {
  const params = aiUsageLogParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<AiUsageLog>>(`/api/monitor/ai-logs?${params.toString()}`);
}

export function exportAiUsageLogs(query: AiUsageLogQuery = {}) {
  const params = aiUsageLogParams(query);
  return downloadFile(
    `/api/monitor/ai-logs/export?${params.toString()}`,
    `AI调用日志_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function aiUsageLogParams(query: AiUsageLogQuery = {}) {
  const params = new URLSearchParams();
  if (query.scene) params.set('scene', query.scene);
  if (query.success !== undefined && query.success !== null) params.set('success', String(query.success));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listScoreReleaseAuditLogs(page = 1, size = 10, query: ScoreReleaseAuditQuery = {}) {
  const params = scoreReleaseAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<ScoreReleaseAuditLog>>(`/api/monitor/score-release-logs?${params.toString()}`);
}

export function exportScoreReleaseAuditLogs(query: ScoreReleaseAuditQuery = {}) {
  const params = scoreReleaseAuditParams(query);
  return downloadFile(
    `/api/monitor/score-release-logs/export?${params.toString()}`,
    `成绩发布审计_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function scoreReleaseAuditParams(query: ScoreReleaseAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.action) params.set('action', query.action);
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listExamApprovalAuditLogs(page = 1, size = 10, query: ExamApprovalAuditQuery = {}) {
  const params = examApprovalAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<ExamApprovalAuditLog>>(`/api/monitor/exam-approval-logs?${params.toString()}`);
}

export function exportExamApprovalAuditLogs(query: ExamApprovalAuditQuery = {}) {
  const params = examApprovalAuditParams(query);
  return downloadFile(
    `/api/monitor/exam-approval-logs/export?${params.toString()}`,
    `exam-approval-audit_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function examApprovalAuditParams(query: ExamApprovalAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.action) params.set('action', query.action);
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listApprovalReminderAuditLogs(page = 1, size = 10, query: ApprovalReminderAuditQuery = {}) {
  const params = approvalReminderAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<ApprovalReminderAuditLog>>(`/api/monitor/approval-reminder-logs?${params.toString()}`);
}

export function exportApprovalReminderAuditLogs(query: ApprovalReminderAuditQuery = {}) {
  const params = approvalReminderAuditParams(query);
  return downloadFile(
    `/api/monitor/approval-reminder-logs/export?${params.toString()}`,
    `approval-reminder-audit_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function approvalReminderAuditParams(query: ApprovalReminderAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.status) params.set('status', query.status);
  if (query.triggerSource) params.set('triggerSource', query.triggerSource);
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listSystemConfigAuditLogs(page = 1, size = 10, query: SystemConfigAuditQuery = {}) {
  const params = systemConfigAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<SystemConfigAuditLog>>(`/api/monitor/system-config-logs?${params.toString()}`);
}

export function exportSystemConfigAuditLogs(query: SystemConfigAuditQuery = {}) {
  const params = systemConfigAuditParams(query);
  return downloadFile(
    `/api/monitor/system-config-logs/export?${params.toString()}`,
    `system-config-audit_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function systemConfigAuditParams(query: SystemConfigAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.category) params.set('category', query.category);
  if (query.configKey) params.set('configKey', query.configKey);
  if (query.actorId !== undefined && query.actorId !== null) params.set('actorId', String(query.actorId));
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listQuestionReviewAuditLogs(page = 1, size = 10, query: QuestionReviewAuditQuery = {}) {
  const params = questionReviewAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<QuestionReviewAuditLog>>(`/api/monitor/question-review-logs?${params.toString()}`);
}

export function exportQuestionReviewAuditLogs(query: QuestionReviewAuditQuery = {}) {
  const params = questionReviewAuditParams(query);
  return downloadFile(
    `/api/monitor/question-review-logs/export?${params.toString()}`,
    `question-review-audit_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function questionReviewAuditParams(query: QuestionReviewAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.questionId !== undefined && query.questionId !== null) params.set('questionId', String(query.questionId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.actionType) params.set('actionType', query.actionType);
  if (query.reviewStatus) params.set('reviewStatus', query.reviewStatus);
  if (query.subjectId !== undefined && query.subjectId !== null) params.set('subjectId', String(query.subjectId));
  if (query.operatorId !== undefined && query.operatorId !== null) params.set('operatorId', String(query.operatorId));
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listScoreAppealAuditLogs(page = 1, size = 10, query: ScoreAppealAuditQuery = {}) {
  const params = scoreAppealAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<ScoreAppealAuditLog>>(`/api/monitor/score-appeal-logs?${params.toString()}`);
}

export function exportScoreAppealAuditLogs(query: ScoreAppealAuditQuery = {}) {
  const params = scoreAppealAuditParams(query);
  return downloadFile(
    `/api/monitor/score-appeal-logs/export?${params.toString()}`,
    `成绩申诉审计_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function scoreAppealAuditParams(query: ScoreAppealAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.action) params.set('action', query.action);
  if (query.handlingResult) params.set('handlingResult', query.handlingResult);
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listReviewScoreAuditLogs(page = 1, size = 10, query: ReviewScoreAuditQuery = {}) {
  const params = reviewScoreAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<ReviewScoreAuditLog>>(`/api/monitor/review-score-logs?${params.toString()}`);
}

export function exportReviewScoreAuditLogs(query: ReviewScoreAuditQuery = {}) {
  const params = reviewScoreAuditParams(query);
  return downloadFile(
    `/api/monitor/review-score-logs/export?${params.toString()}`,
    `review-score-audit_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function reviewScoreAuditParams(query: ReviewScoreAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.logId) params.set('logId', String(query.logId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.examId !== undefined && query.examId !== null) params.set('examId', String(query.examId));
  if (query.reviewerId !== undefined && query.reviewerId !== null) params.set('reviewerId', String(query.reviewerId));
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function listNotificationAuditLogs(page = 1, size = 10, query: NotificationAuditQuery = {}) {
  const params = notificationAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<NotificationAuditLog>>(`/api/notifications/audit?${params.toString()}`);
}

export function exportNotificationAuditLogs(query: NotificationAuditQuery = {}) {
  const params = notificationAuditParams(query);
  return downloadFile(
    `/api/notifications/audit/export?${params.toString()}`,
    `notification-audit_${new Date().toISOString().slice(0, 10)}.csv`
  );
}

function notificationAuditParams(query: NotificationAuditQuery = {}) {
  const params = new URLSearchParams();
  if (query.notificationId !== undefined && query.notificationId !== null) params.set('notificationId', String(query.notificationId));
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.type) params.set('type', query.type);
  if (query.relatedType) params.set('relatedType', query.relatedType);
  if (query.relatedId !== undefined && query.relatedId !== null) params.set('relatedId', String(query.relatedId));
  if (query.read !== undefined && query.read !== null) params.set('read', String(query.read));
  if (query.userId !== undefined && query.userId !== null) params.set('userId', String(query.userId));
  if (query.startFrom) params.set('startFrom', query.startFrom);
  if (query.startTo) params.set('startTo', query.startTo);
  return params;
}

export function fetchAnalysisOverview() {
  return getJson<AnalysisOverview>('/api/analysis/overview');
}

export function fetchTeacherAnalysis() {
  return getJson<AnalysisOverview>('/api/analysis/teacher');
}
