export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
}

const TOKEN_KEY = 'smart_exam_token';
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '');

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

export async function getJson<T>(url: string): Promise<ApiResponse<T>> {
  return requestJson<T>(url, {
    method: 'GET'
  });
}

export async function postJson<T, B = unknown>(url: string, body?: B): Promise<ApiResponse<T>> {
  return requestJson<T>(url, {
    method: 'POST',
    body: body === undefined ? undefined : JSON.stringify(body)
  });
}

export async function putJson<T, B = unknown>(url: string, body?: B): Promise<ApiResponse<T>> {
  return requestJson<T>(url, {
    method: 'PUT',
    body: body === undefined ? undefined : JSON.stringify(body)
  });
}

export async function deleteJson<T>(url: string): Promise<ApiResponse<T>> {
  return requestJson<T>(url, {
    method: 'DELETE'
  });
}

async function requestJson<T>(url: string, init: RequestInit): Promise<ApiResponse<T>> {
  const token = getToken();
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(resolveUrl(url), {
    ...init,
    headers
  });

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;
  if (!response.ok) {
    const message = payload?.message || `请求失败：${response.status}`;
    throw new Error(message);
  }

  if (!payload) {
    throw new Error('接口响应为空');
  }

  return payload;
}

function resolveUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  return `${API_BASE_URL}${url}`;
}
