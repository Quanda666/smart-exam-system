/**
 * 时间格式化工具。
 * 全站统一为 YYYY-MM-DD HH:mm:ss 格式。
 */

export function formatDateTime(
  value: string | number | Date | null | undefined,
  fallback = '—'
): string {
  if (!value) return fallback;

  const date = typeof value === 'string' || typeof value === 'number'
    ? new Date(value)
    : value;

  if (isNaN(date.getTime())) return fallback;

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  const second = String(date.getSeconds()).padStart(2, '0');

  return `${year}-${month}-${day} ${hour}:${minute}:${second}`;
}

export function formatDate(
  value: string | number | Date | null | undefined,
  fallback = '—'
): string {
  if (!value) return fallback;

  const date = typeof value === 'string' || typeof value === 'number'
    ? new Date(value)
    : value;

  if (isNaN(date.getTime())) return fallback;

  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');

  return `${year}-${month}-${day}`;
}
