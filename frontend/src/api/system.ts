import { downloadFile, getJson, putJson } from './request';

export interface HealthData {
  application: string;
  status: string;
  time: string;
  database: {
    connected: boolean;
    message: string;
  };
}

export interface AiStatusData {
  baseUrl: string;
  model: string;
  mockEnabled: boolean;
  apiKeyConfigured: boolean;
  apiKeyMasked: string;
  timeoutSeconds: number;
  available: boolean;
  mode: 'MOCK' | 'REMOTE' | 'DISABLED';
  notice: string;
}

export interface SystemConfigItem {
  id: number;
  configKey: string;
  configValue: string;
  valueType: 'STRING' | 'NUMBER' | 'BOOLEAN';
  category: string;
  description?: string;
  editable: number;
  updatedBy?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
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

export function fetchHealth() {
  return getJson<HealthData>('/api/health');
}

export function fetchAiStatus() {
  return getJson<AiStatusData>('/api/ai/status');
}

export function listSystemConfigs(category?: string) {
  const params = new URLSearchParams();
  if (category) params.set('category', category);
  const query = params.toString();
  return getJson<SystemConfigItem[]>(`/api/system/configs${query ? `?${query}` : ''}`);
}

export function updateSystemConfig(key: string, configValue: string) {
  return putJson<SystemConfigItem, { configValue: string }>(
    `/api/system/configs/${encodeURIComponent(key)}`,
    { configValue }
  );
}

export function listSystemConfigAuditLogs(page = 1, size = 10, query: SystemConfigAuditQuery = {}) {
  const params = systemConfigAuditParams(query);
  params.set('page', String(page));
  params.set('size', String(size));
  return getJson<PageResult<SystemConfigAuditLog>>(`/api/system/configs/audit?${params.toString()}`);
}

export function exportSystemConfigAuditLogs(query: SystemConfigAuditQuery = {}) {
  const params = systemConfigAuditParams(query);
  return downloadFile(
    `/api/system/configs/audit/export?${params.toString()}`,
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
