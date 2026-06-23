import { ApiError, downloadFile, getJson, postJson } from './request';

const MONITOR_QUEUE_STORAGE_PREFIX = 'smart_exam_monitor_queue_';
const MONITOR_EVENT_BATCH_SIZE = 100;
const STORED_MONITOR_EVENT_MAX_AGE_MS = 24 * 60 * 60 * 1000;

export interface CheatEventPayload {
  attemptId: number;
  eventType: string;
  extraInfo?: string;
  clientEventId: string;
  clientEventTime?: string;
}

export interface MonitorSession {
  id: number;
  attemptId: number;
  examId: number;
  userId: number;
  status: 'ONLINE' | 'OFFLINE' | 'SUBMITTED' | string;
  lastHeartbeatAt?: string | null;
  lastEventAt?: string | null;
  eventCount: number;
  riskScore: number;
  riskLevel?: 'NORMAL' | 'WARNING' | 'HIGH' | string;
  warningThreshold?: number;
  highThreshold?: number;
  latestActionType?: string | null;
  latestActionNote?: string | null;
  latestHandledAt?: string | null;
  latestHandlerName?: string | null;
  latestActionNotificationSent?: boolean | number | string | null;
  latestActionNotificationId?: number | string | null;
  latestActionNotificationRead?: boolean | number | string | null;
  latestActionNotificationCreatedAt?: string | null;
  lastEventType?: string | null;
  attemptStatus?: number | null;
  attemptNo?: number | null;
  startTime?: string | null;
  rulesConfirmedAt?: string | null;
  submitTime?: string | null;
  submitType?: string | null;
  lastDraftSavedAt?: string | null;
  draftRevision?: number | string | null;
  deadlineAt?: string | null;
  remainingSeconds?: number | string | null;
  questionCount?: number | string | null;
  answeredCount?: number | string | null;
  unansweredCount?: number | string | null;
  examName?: string;
  realName?: string;
  studentNo?: string;
  className?: string;
}

export interface MonitorEvent {
  id: number;
  attemptId: number;
  examId?: number;
  userId?: number;
  eventType: string;
  riskScore?: number;
  extraInfo?: string | null;
  clientEventId?: string | null;
  clientEventTime?: string | null;
  eventTime: string;
}

export interface MonitorEventQuery {
  eventType?: string;
  startFrom?: string;
  startTo?: string;
  minRiskScore?: number;
}

export interface MonitorSessionExportQuery {
  sessionStatus?: string;
  minRiskScore?: number;
  latestNotificationStatus?: string;
  rulesConfirmationStatus?: string;
  latestActionType?: string;
}

export interface MonitorAction {
  id: number;
  sessionId: number;
  attemptId: number;
  examId: number;
  userId: number;
  actionType: string;
  note?: string | null;
  notificationSent?: boolean | number | string;
  notificationId?: number | string | null;
  notificationRead?: boolean | number | string | null;
  notificationCreatedAt?: string | null;
  handledBy: number;
  handlerName?: string | null;
  handledAt: string;
  operationLogId?: number | string | null;
}

export interface MonitorAttemptEvidence {
  attemptId: number;
  examId: number;
  userId: number;
  attemptNo?: number | string | null;
  attemptStatus?: number | string | null;
  startTime?: string | null;
  rulesConfirmedAt?: string | null;
  submitTime?: string | null;
  submitType?: string | null;
  submitReason?: string | null;
  lastHeartbeatAt?: string | null;
  lastDraftSavedAt?: string | null;
  draftRevision?: number | string | null;
  submitPayloadHash?: string | null;
  examName?: string | null;
  examStartTime?: string | null;
  examEndTime?: string | null;
  durationMinutes?: number | string | null;
  deadlineAt?: string | null;
  remainingSeconds?: number | string | null;
}

export interface MonitorDraftEvidence {
  exists: boolean;
  source: string;
  clientDraftId?: string | null;
  revision?: number | string | null;
  savedCount?: number | string | null;
  updatedAt?: string | null;
  answerKeyCount: number | string;
  filledAnswerCount: number | string;
  parseError?: boolean;
}

export interface MonitorAnswerStats {
  questionCount: number | string;
  recordedCount: number | string;
  answeredCount: number | string;
  unansweredCount: number | string;
  reviewedCount: number | string;
  pendingReviewCount: number | string;
}

export interface MonitorIncidentFinding {
  severity: 'HIGH' | 'WARNING' | 'INFO' | string;
  code: string;
  message: string;
}

export interface MonitorForceSubmitEvidence {
  exists: boolean;
  submitForced?: boolean;
  submitTime?: string | null;
  submitReason?: string | null;
  submitPayloadHash?: string | null;
  action?: MonitorAction | null;
}

export interface MonitorIncidentDetail {
  session: MonitorSession;
  attempt: MonitorAttemptEvidence;
  health: {
    level: 'NORMAL' | 'INFO' | 'WARNING' | 'HIGH' | string;
    summary: string;
    findings: MonitorIncidentFinding[];
  };
  draft: MonitorDraftEvidence;
  answerStats: MonitorAnswerStats;
  forceSubmitEvidence: MonitorForceSubmitEvidence;
  events: MonitorEvent[];
  actions: MonitorAction[];
  eventLimit: number;
}

export interface MonitorForceSubmitResult {
  attemptId: number;
  sessionId: number;
  submit: {
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
  };
  action: MonitorAction;
  actionAlreadyRecorded: boolean;
  notificationSent?: boolean;
  notificationId?: number | string | null;
  operationLogId?: number | string | null;
}

export interface StoredMonitorFlushResult {
  scannedQueues: number;
  flushedQueues: number;
  failedQueues: number;
  remainingEvents: number;
}

export function recordCheatEvent(payload: CheatEventPayload) {
  return postJson<null, CheatEventPayload>('/api/monitor/cheat-event', payload);
}

export function recordCheatEventsBatch(events: CheatEventPayload[]) {
  return postJson<{ accepted: number; duplicates: number }, { events: CheatEventPayload[] }>(
    '/api/monitor/cheat-events/batch',
    { events }
  );
}

export async function flushStoredMonitorQueues(): Promise<StoredMonitorFlushResult> {
  const result: StoredMonitorFlushResult = {
    scannedQueues: 0,
    flushedQueues: 0,
    failedQueues: 0,
    remainingEvents: 0
  };
  if (typeof localStorage === 'undefined' || navigator.onLine === false) {
    return result;
  }
  const keys = storedMonitorQueueKeys();
  result.scannedQueues = keys.length;
  for (const key of keys) {
    const events = readStoredMonitorQueue(key);
    if (events.length === 0) {
      localStorage.removeItem(key);
      continue;
    }
    const remaining = [...events];
    try {
      while (remaining.length > 0) {
        const batch = remaining.slice(0, MONITOR_EVENT_BATCH_SIZE);
        await recordCheatEventsBatch(batch);
        remaining.splice(0, batch.length);
        writeStoredMonitorQueue(key, remaining);
      }
      localStorage.removeItem(key);
      result.flushedQueues += 1;
    } catch (error) {
      const isolatedRemaining = isPermanentStoredMonitorUploadReject(error)
        ? await isolateStoredMonitorQueueFailures(key, remaining)
        : remaining;
      writeStoredMonitorQueue(key, isolatedRemaining);
      if (isolatedRemaining.length === 0) {
        result.flushedQueues += 1;
      } else {
        result.failedQueues += 1;
        result.remainingEvents += isolatedRemaining.length;
      }
    }
  }
  return result;
}

export function listExamMonitorSessions(examId: number) {
  return getJson<MonitorSession[]>(`/api/monitor/exams/${examId}/sessions`);
}

export function exportExamMonitorSessions(examId: number, examName?: string, query?: MonitorSessionExportQuery) {
  return downloadFile(
    `/api/monitor/exams/${examId}/sessions/export${monitorSessionExportQueryString(query)}`,
    monitorSessionExportFileName(examName, query)
  );
}

export function listAttemptMonitorEvents(attemptId: number, query?: MonitorEventQuery) {
  return getJson<MonitorEvent[]>(`/api/monitor/cheat-events/${attemptId}${monitorEventQueryString(query)}`);
}

export function exportAttemptMonitorEvents(
  attemptId: number,
  examName?: string,
  studentName?: string,
  query?: MonitorEventQuery
) {
  return downloadFile(
    `/api/monitor/cheat-events/${attemptId}/export${monitorEventQueryString(query)}`,
    monitorEventExportFileName(attemptId, examName, studentName, query)
  );
}

export function exportMonitorActions(sessionId: number, examName?: string, studentName?: string, attemptId?: number) {
  return downloadFile(
    `/api/monitor/sessions/${sessionId}/actions/export`,
    monitorActionExportFileName(sessionId, examName, studentName, attemptId)
  );
}

export function createMonitorAction(sessionId: number, payload: { actionType: string; note?: string }) {
  return postJson<MonitorAction, { actionType: string; note?: string }>(
    `/api/monitor/sessions/${sessionId}/actions`,
    payload
  );
}

export function forceSubmitMonitorSession(sessionId: number, payload?: { note?: string }) {
  return postJson<MonitorForceSubmitResult, { note?: string }>(
    `/api/monitor/sessions/${sessionId}/force-submit`,
    payload || {}
  );
}

export function listMonitorActions(sessionId: number) {
  return getJson<MonitorAction[]>(`/api/monitor/sessions/${sessionId}/actions`);
}

export function getMonitorAttemptIncident(sessionId: number) {
  return getJson<MonitorIncidentDetail>(`/api/monitor/sessions/${sessionId}/incident`);
}

function monitorEventQueryString(query?: MonitorEventQuery) {
  if (!query) {
    return '';
  }
  const params = new URLSearchParams();
  if (query.eventType && query.eventType !== 'ALL') {
    params.set('eventType', query.eventType);
  }
  if (query.startFrom) {
    params.set('startFrom', query.startFrom);
  }
  if (query.startTo) {
    params.set('startTo', query.startTo);
  }
  if (typeof query.minRiskScore === 'number' && query.minRiskScore >= 0) {
    params.set('minRiskScore', String(query.minRiskScore));
  }
  const value = params.toString();
  return value ? `?${value}` : '';
}

function monitorSessionExportQueryString(query?: MonitorSessionExportQuery) {
  if (!query) {
    return '';
  }
  const params = new URLSearchParams();
  if (query.sessionStatus && query.sessionStatus !== 'ALL') {
    params.set('sessionStatus', query.sessionStatus);
  }
  if (typeof query.minRiskScore === 'number' && query.minRiskScore >= 0) {
    params.set('minRiskScore', String(query.minRiskScore));
  }
  if (query.latestNotificationStatus && query.latestNotificationStatus !== 'ALL') {
    params.set('latestNotificationStatus', query.latestNotificationStatus);
  }
  if (query.rulesConfirmationStatus && query.rulesConfirmationStatus !== 'ALL') {
    params.set('rulesConfirmationStatus', query.rulesConfirmationStatus);
  }
  if (query.latestActionType && query.latestActionType !== 'ALL') {
    params.set('latestActionType', query.latestActionType);
  }
  const value = params.toString();
  return value ? `?${value}` : '';
}

function monitorSessionExportFileName(examName?: string, query?: MonitorSessionExportQuery) {
  const parts = [
    safeFileNamePart(examName || 'exam'),
    'monitor_sessions',
    ...monitorSessionExportFilterParts(query),
    new Date().toISOString().slice(0, 10)
  ];
  return `${parts.filter(Boolean).join('_')}.csv`;
}

function monitorSessionExportFilterParts(query?: MonitorSessionExportQuery) {
  if (!query) return [];
  const parts: string[] = [];
  if (query.sessionStatus && query.sessionStatus !== 'ALL') {
    parts.push(`status_${safeFileNamePart(query.sessionStatus)}`);
  }
  if (typeof query.minRiskScore === 'number' && query.minRiskScore >= 0) {
    parts.push(`risk_${query.minRiskScore}`);
  }
  if (query.latestNotificationStatus && query.latestNotificationStatus !== 'ALL') {
    parts.push(`notice_${safeFileNamePart(query.latestNotificationStatus)}`);
  }
  if (query.rulesConfirmationStatus && query.rulesConfirmationStatus !== 'ALL') {
    parts.push(`rules_${safeFileNamePart(query.rulesConfirmationStatus)}`);
  }
  if (query.latestActionType && query.latestActionType !== 'ALL') {
    parts.push(`action_${safeFileNamePart(query.latestActionType)}`);
  }
  return parts;
}

function monitorEventExportFileName(attemptId: number, examName?: string, studentName?: string, query?: MonitorEventQuery) {
  const parts = [
    safeFileNamePart(examName || 'exam'),
    safeFileNamePart(studentName || 'student'),
    'monitor_events',
    `attempt_${attemptId}`,
    ...monitorEventExportFilterParts(query),
    new Date().toISOString().slice(0, 10)
  ];
  return `${parts.filter(Boolean).join('_')}.csv`;
}

function monitorEventExportFilterParts(query?: MonitorEventQuery) {
  if (!query) return [];
  const parts: string[] = [];
  if (query.eventType && query.eventType !== 'ALL') {
    parts.push(`event_${safeFileNamePart(query.eventType)}`);
  }
  if (typeof query.minRiskScore === 'number' && query.minRiskScore >= 0) {
    parts.push(`risk_${query.minRiskScore}`);
  }
  if (query.startFrom) {
    parts.push(`from_${safeFileNamePart(query.startFrom)}`);
  }
  if (query.startTo) {
    parts.push(`to_${safeFileNamePart(query.startTo)}`);
  }
  return parts;
}

function monitorActionExportFileName(sessionId: number, examName?: string, studentName?: string, attemptId?: number) {
  const parts = [
    safeFileNamePart(examName || 'exam'),
    safeFileNamePart(studentName || 'student'),
    'monitor_actions',
    `session_${sessionId}`,
    attemptId ? `attempt_${attemptId}` : '',
    new Date().toISOString().slice(0, 10)
  ];
  return `${parts.filter(Boolean).join('_')}.csv`;
}

function safeFileNamePart(value: string) {
  return value.trim().replace(/[\\/:*?"<>|\s]+/g, '-').replace(/^-+|-+$/g, '');
}

function storedMonitorQueueKeys() {
  const keys: string[] = [];
  for (let index = 0; index < localStorage.length; index += 1) {
    const key = localStorage.key(index);
    if (key?.startsWith(MONITOR_QUEUE_STORAGE_PREFIX)) {
      keys.push(key);
    }
  }
  return keys;
}

function readStoredMonitorQueue(key: string) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return [];
    const parsed = JSON.parse(raw) as CheatEventPayload[];
    return Array.isArray(parsed) ? parsed.filter(isStoredMonitorEvent) : [];
  } catch {
    return [];
  }
}

function writeStoredMonitorQueue(key: string, events: CheatEventPayload[]) {
  if (events.length === 0) {
    localStorage.removeItem(key);
    return;
  }
  localStorage.setItem(key, JSON.stringify(events.slice(-200)));
}

async function isolateStoredMonitorQueueFailures(key: string, events: CheatEventPayload[]) {
  const remaining = [...events];
  let index = 0;
  while (index < remaining.length) {
    try {
      await recordCheatEventsBatch([remaining[index]]);
      remaining.splice(index, 1);
      writeStoredMonitorQueue(key, remaining);
    } catch (error) {
      if (!isPermanentStoredMonitorUploadReject(error)) {
        return remaining;
      }
      remaining.splice(index, 1);
      writeStoredMonitorQueue(key, remaining);
    }
  }
  return remaining;
}

function isPermanentStoredMonitorUploadReject(error: unknown) {
  return error instanceof ApiError && error.status === 400;
}

function isStoredMonitorEvent(value: unknown): value is CheatEventPayload {
  if (!value || typeof value !== 'object') return false;
  const item = value as Partial<CheatEventPayload>;
  return typeof item.attemptId === 'number'
    && typeof item.eventType === 'string'
    && typeof item.clientEventId === 'string'
    && isRecentStoredMonitorEvent(item.clientEventTime);
}

function isRecentStoredMonitorEvent(clientEventTime?: string) {
  if (!clientEventTime) return false;
  const eventTime = Date.parse(clientEventTime);
  if (Number.isNaN(eventTime)) return false;
  return Date.now() - eventTime <= STORED_MONITOR_EVENT_MAX_AGE_MS;
}
