import { getJson } from './request';

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

export function fetchHealth() {
  return getJson<HealthData>('/api/health');
}

export function fetchAiStatus() {
  return getJson<AiStatusData>('/api/ai/status');
}
