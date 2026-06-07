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
  isRead: number;
  createdAt: string;
}

export function getMyNotifications(page = 1, size = 10) {
  return getJson<PageResult<Notification>>(`/api/notifications/my?page=${page}&size=${size}`);
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
