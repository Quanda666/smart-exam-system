import { getJson, putJson } from './request';

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export interface Notification {
  id: number;
  title: string;
  content: string;
  type: string;
  link?: string;
  relatedType?: string | null;
  relatedId?: number | null;
  isRead: number;
  createdAt: string;
}

export interface NotificationQuery {
  type?: string;
  relatedType?: string;
  relatedId?: number | string;
}

export function getMyNotifications(page = 1, size = 10, query: NotificationQuery = {}) {
  const params = new URLSearchParams({
    page: String(page),
    size: String(size)
  });
  if (query.type) params.set('type', query.type);
  if (query.relatedType) params.set('relatedType', query.relatedType);
  if (query.relatedId !== undefined && query.relatedId !== null) params.set('relatedId', String(query.relatedId));
  return getJson<PageResult<Notification>>(`/api/notifications/my?${params.toString()}`);
}

export function getUnreadCount() {
  return getJson<{ count: number }>('/api/notifications/unread-count');
}

export function markRead(id: number) {
  return putJson<{ id: number; read: boolean }>(`/api/notifications/${id}/read`);
}

export function markAllRead() {
  return putJson<{ success: boolean }>('/api/notifications/read-all');
}
