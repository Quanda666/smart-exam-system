import { deleteJson, getJson, putJson } from './request';

export interface SystemUser {
  id: number;
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  status: number;
  roleCodes?: string;
  studentNo?: string;
  className?: string;
  teacherNo?: string;
  teacherTitle?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export interface UserQuery {
  keyword?: string;
  role?: string;
  status?: number | null;
  page?: number;
  size?: number;
}

export interface SystemRole {
  id: number;
  roleCode: string;
  roleName: string;
  status: number;
  userCount: number;
  pages: string[];
}

export interface OperationLog {
  id: number;
  operator_id?: number;
  operator_name?: string;
  action?: string;
  target?: string;
  detail?: string;
  ip?: string;
  created_at?: string;
}

export interface RoleStat {
  roleCode: string;
  roleName: string;
  userCount: number;
}

export interface SubjectStat {
  subjectName: string;
  examCount: number;
  attemptCount: number;
  avgScore: number;
}

export interface AnalysisOverview {
  userCount: number;
  questionCount: number;
  paperCount: number;
  examCount: number;
  attemptCount: number;
  completedCount: number;
  averageScore: number;
  roleDistribution: RoleStat[];
  subjectStats: SubjectStat[];
  scoreDistribution: Record<string, number>;
}

function userQueryString(query: UserQuery = {}) {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.role) params.set('role', query.role);
  if (query.status !== undefined && query.status !== null) params.set('status', String(query.status));
  if (query.page) params.set('page', String(query.page));
  if (query.size) params.set('size', String(query.size));
  const value = params.toString();
  return value ? `?${value}` : '';
}

export function listUsers(query?: UserQuery) {
  return getJson<PageResult<SystemUser>>(`/api/system/users${userQueryString(query)}`);
}

export function fetchUserSummary() {
  return getJson<Record<string, number>>('/api/system/users/summary');
}

export function updateUserStatus(id: number, status: number) {
  return putJson(`/api/system/users/${id}/status?status=${status}`);
}

export function resetUserPassword(id: number, newPassword: string) {
  return putJson(`/api/system/users/${id}/password`, { newPassword });
}

export function deleteUser(id: number) {
  return deleteJson(`/api/system/users/${id}`);
}

export function listRoles() {
  return getJson<SystemRole[]>('/api/system/roles');
}

export function listOperationLogs() {
  return getJson<OperationLog[]>('/api/monitor/logs');
}

export function fetchAnalysisOverview() {
  return getJson<AnalysisOverview>('/api/analysis/overview');
}

export function fetchTeacherAnalysis() {
  return getJson<AnalysisOverview>('/api/analysis/teacher');
}
