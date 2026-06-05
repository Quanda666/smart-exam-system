import { deleteJson, getJson, postJson, putJson } from './request';

export interface ClassInfo {
  id: number;
  className: string;
  major: string;
  grade: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface SubjectInfo {
  id: number;
  subjectName: string;
  description: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface KnowledgePointInfo {
  id: number;
  subjectId: number;
  subjectName: string;
  parentId: number | null;
  pointName: string;
  sortOrder: number;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface NoticeInfo {
  id: number;
  title: string;
  content: string;
  publisherId?: number;
  publisherName?: string;
  publishTime?: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface BasicQuery {
  keyword?: string;
  status?: number | null;
  subjectId?: number | null;
}

export type DeleteResult = {
  deleted: boolean;
  id: number;
};

function queryString(query: BasicQuery = {}) {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.status !== undefined && query.status !== null) params.set('status', String(query.status));
  if (query.subjectId !== undefined && query.subjectId !== null) params.set('subjectId', String(query.subjectId));
  const value = params.toString();
  return value ? `?${value}` : '';
}

export function listClasses(query?: BasicQuery) {
  return getJson<ClassInfo[]>(`/api/basic/classes${queryString(query)}`);
}

export function createClass(payload: Omit<ClassInfo, 'id'>) {
  return postJson<ClassInfo, Omit<ClassInfo, 'id'>>('/api/basic/classes', payload);
}

export function updateClass(id: number, payload: Omit<ClassInfo, 'id'>) {
  return putJson<ClassInfo, Omit<ClassInfo, 'id'>>(`/api/basic/classes/${id}`, payload);
}

export function deleteClass(id: number) {
  return deleteJson<DeleteResult>(`/api/basic/classes/${id}`);
}

export function listSubjects(query?: BasicQuery) {
  return getJson<SubjectInfo[]>(`/api/basic/subjects${queryString(query)}`);
}

export function createSubject(payload: Omit<SubjectInfo, 'id'>) {
  return postJson<SubjectInfo, Omit<SubjectInfo, 'id'>>('/api/basic/subjects', payload);
}

export function updateSubject(id: number, payload: Omit<SubjectInfo, 'id'>) {
  return putJson<SubjectInfo, Omit<SubjectInfo, 'id'>>(`/api/basic/subjects/${id}`, payload);
}

export function deleteSubject(id: number) {
  return deleteJson<DeleteResult>(`/api/basic/subjects/${id}`);
}

export function listKnowledgePoints(query?: BasicQuery) {
  return getJson<KnowledgePointInfo[]>(`/api/basic/knowledge-points${queryString(query)}`);
}

export function createKnowledgePoint(payload: Omit<KnowledgePointInfo, 'id' | 'subjectName'>) {
  return postJson<KnowledgePointInfo, Omit<KnowledgePointInfo, 'id' | 'subjectName'>>('/api/basic/knowledge-points', payload);
}

export function updateKnowledgePoint(id: number, payload: Omit<KnowledgePointInfo, 'id' | 'subjectName'>) {
  return putJson<KnowledgePointInfo, Omit<KnowledgePointInfo, 'id' | 'subjectName'>>(`/api/basic/knowledge-points/${id}`, payload);
}

export function deleteKnowledgePoint(id: number) {
  return deleteJson<DeleteResult>(`/api/basic/knowledge-points/${id}`);
}

export function listNotices(query?: BasicQuery) {
  return getJson<NoticeInfo[]>(`/api/basic/notices${queryString(query)}`);
}

export function createNotice(payload: Omit<NoticeInfo, 'id'>) {
  return postJson<NoticeInfo, Omit<NoticeInfo, 'id'>>('/api/basic/notices', payload);
}

export function updateNotice(id: number, payload: Omit<NoticeInfo, 'id'>) {
  return putJson<NoticeInfo, Omit<NoticeInfo, 'id'>>(`/api/basic/notices/${id}`, payload);
}

export function deleteNotice(id: number) {
  return deleteJson<DeleteResult>(`/api/basic/notices/${id}`);
}

export function fetchBasicSummary() {
  return getJson<Record<string, number>>('/api/basic/summary');
}
