export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
  timestamp: string;
  requestId?: string;
  responseTimeMs?: number;
}

export class ApiError extends Error {
  code: string;
  status: number;
  requestId?: string;
  responseTimeMs?: number;
  rawMessage: string;

  constructor(message: string, code = 'REQUEST_FAILED', status = 0, requestId?: string, responseTimeMs?: number) {
    super(requestId ? `${message}（请求ID：${requestId}）` : message);
    this.name = 'ApiError';
    this.rawMessage = message;
    this.code = code;
    this.status = status;
    this.requestId = requestId;
    this.responseTimeMs = responseTimeMs;
  }
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

export async function postForm<T>(url: string, body: FormData): Promise<ApiResponse<T>> {
  const token = getToken();
  const headers = new Headers();
  headers.set('X-Request-Id', createRequestId());
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(resolveUrl(url), {
    method: 'POST',
    headers,
    body
  });

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;
  const trace = traceHeaders(response);
  if (!response.ok) {
    throw new ApiError(payload?.message || `Request failed: ${response.status}`,
      payload?.code,
      response.status,
      trace.requestId,
      trace.responseTimeMs);
  }
  if (!payload) {
    throw new ApiError('Empty API response', 'EMPTY_RESPONSE', response.status, trace.requestId, trace.responseTimeMs);
  }
  return withTrace(payload, trace);
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

/**
 * 鉴权文件下载：以 Bearer Token 发起 GET，把响应当作二进制读取并触发浏览器下载。
 * 这里不能用 <a href> 直链，因为后端导出接口需要 Authorization 头，直链带不上 Token。
 * 文件名优先取后端 Content-Disposition（RFC 5987 编码的中文名），解析失败时回退到 fallbackName。
 */
export async function downloadFile(url: string, fallbackName = 'export.csv'): Promise<void> {
  const token = getToken();
  const headers = new Headers();
  headers.set('X-Request-Id', createRequestId());
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(resolveUrl(url), { method: 'GET', headers });
  const trace = traceHeaders(response);
  if (!response.ok) {
    let message = `下载失败：${response.status}`;
    let code = 'DOWNLOAD_FAILED';
    try {
      const payload = (await response.json()) as ApiResponse<unknown> | null;
      if (payload?.message) {
        message = payload.message;
      }
      if (payload?.code) {
        code = payload.code;
      }
    } catch {
      // 错误响应体不是 JSON 时忽略，沿用状态码提示
    }
    throw new ApiError(message, code, response.status, trace.requestId, trace.responseTimeMs);
  }

  const blob = await response.blob();
  const filename = parseFilename(response.headers.get('Content-Disposition')) || fallbackName;
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(objectUrl);
}

function parseFilename(header: string | null): string | null {
  if (!header) {
    return null;
  }
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(header);
  if (encoded) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      // 解码失败时退回普通 filename
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(header);
  return plain ? plain[1] : null;
}

async function requestJson<T>(url: string, init: RequestInit): Promise<ApiResponse<T>> {
  const token = getToken();
  const headers = new Headers(init.headers);
  headers.set('Content-Type', 'application/json');
  headers.set('X-Request-Id', createRequestId());
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(resolveUrl(url), {
    ...init,
    headers
  });

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;
  const trace = traceHeaders(response);
  if (!response.ok) {
    const message = payload?.message || `Request failed: ${response.status}`;
    throw new ApiError(message, payload?.code, response.status, trace.requestId, trace.responseTimeMs);
  }

  if (!payload) {
    throw new ApiError('Empty API response', 'EMPTY_RESPONSE', response.status, trace.requestId, trace.responseTimeMs);
  }

  return withTrace(payload, trace);
}

function resolveUrl(url: string) {
  if (/^https?:\/\//i.test(url)) {
    return url;
  }
  return `${API_BASE_URL}${url}`;
}

function createRequestId() {
  const random = typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID().replace(/-/g, '')
    : Math.random().toString(16).slice(2) + Date.now().toString(16);
  return `web-${random}`.slice(0, 80);
}

function traceHeaders(response: Response) {
  const requestId = response.headers.get('X-Request-Id') || undefined;
  const rawResponseTime = response.headers.get('X-Response-Time-Ms');
  const responseTimeMs = rawResponseTime && !Number.isNaN(Number(rawResponseTime))
    ? Number(rawResponseTime)
    : undefined;
  return { requestId, responseTimeMs };
}

function withTrace<T>(payload: ApiResponse<T>, trace: { requestId?: string; responseTimeMs?: number }) {
  payload.requestId = trace.requestId;
  payload.responseTimeMs = trace.responseTimeMs;
  return payload;
}
