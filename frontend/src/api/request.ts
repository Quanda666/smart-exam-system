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

export async function postForm<T>(url: string, body: FormData): Promise<ApiResponse<T>> {
  const token = getToken();
  const headers = new Headers();
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(resolveUrl(url), {
    method: 'POST',
    headers,
    body
  });

  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null;
  if (!response.ok) {
    throw new Error(payload?.message || `请求失败：${response.status}`);
  }
  if (!payload) {
    throw new Error('接口响应为空');
  }
  return payload;
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
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(resolveUrl(url), { method: 'GET', headers });
  if (!response.ok) {
    let message = `下载失败：${response.status}`;
    try {
      const payload = (await response.json()) as ApiResponse<unknown> | null;
      if (payload?.message) {
        message = payload.message;
      }
    } catch {
      // 错误响应体不是 JSON 时忽略，沿用状态码提示
    }
    throw new Error(message);
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
