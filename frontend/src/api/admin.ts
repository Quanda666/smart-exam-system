import { deleteJson, getJson, postJson, putJson } from './request';

export interface SystemUser {
  id: number;
  username: string;
  realName: string;
  phone?: string;
  email?: string;
  status: number;
  roleCodes?: string;
  studentNo?: string;
  classId?: number;
  className?: string;
  classType?: string;
  classMemberships?: string;
  enrollmentYear?: string;
  studentCollege?: string;
  studentMajor?: string;
  teacherNo?: string;
  hireDate?: string;
  teacherTitle?: string;
  teacherCollege?: string;
  introduction?: string;
  teachingAssignments?: string;
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

export interface AiUsageLog {
  id: number;
  userId?: number;
  userName?: string;
  scene?: string;
  prompt?: string;
  response?: string;
  success: number | boolean;
  errorMessage?: string;
  createdAt?: string;
}

export interface AiUsageLogQuery {
  scene?: string;
  success?: boolean | null;
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

export interface CreateUserPayload {
  username: string;
  password: string;
  realName: string;
  roleType: string;
  studentNo?: string;
  classId?: number | null;
  electiveClassIds?: number[];
  enrollmentYear?: string;
  college?: string;
  major?: string;
  teacherNo?: string;
  hireDate?: string | null;
  title?: string;
  introduction?: string;
  phone?: string;
  email?: string;
}

export function createUser(payload: CreateUserPayload) {
  return postJson('/api/system/users', payload);
}

export interface UpdateUserPayload {
  realName: string;
  roleType: string;
  studentNo?: string;
  classId?: number | null;
  electiveClassIds?: number[];
  enrollmentYear?: string;
  college?: string;
  major?: string;
  teacherNo?: string;
  hireDate?: string | null;
  title?: string;
  introduction?: string;
  phone?: string;
  email?: string;
}

export function updateUser(id: number, payload: UpdateUserPayload) {
  return putJson(`/api/system/users/${id}`, payload);
}

export function listRoles() {
  return getJson<SystemRole[]>('/api/system/roles');
}

export function listOperationLogs(page = 1, size = 10) {
  return getJson<PageResult<OperationLog>>(`/api/monitor/logs?page=${page}&size=${size}`);
}

export function listAiUsageLogs(page = 1, size = 10, query: AiUsageLogQuery = {}) {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  if (query.scene) params.set('scene', query.scene);
  if (query.success !== undefined && query.success !== null) params.set('success', String(query.success));
  return getJson<PageResult<AiUsageLog>>(`/api/monitor/ai-logs?${params.toString()}`);
}

export function fetchAnalysisOverview() {
  return getJson<AnalysisOverview>('/api/analysis/overview');
}

export function fetchTeacherAnalysis() {
  return getJson<AnalysisOverview>('/api/analysis/teacher');
}
