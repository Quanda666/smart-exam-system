import { getJson, postJson, putJson } from './request';

export type RoleCode = 'ADMIN' | 'TEACHER' | 'STUDENT' | 'GUEST';

export interface AuthUser {
  id: number;
  username: string;
  realName: string;
  roles: RoleCode[];
  primaryRole: RoleCode;
  roleLabel: string;
  defaultPath: string;
  // profile 由后端按角色拼装，可能含 email/emailVerified/phone/student_no/class_name/major/grade/teacher_no/title 等
  profile: Record<string, unknown>;
  avatar?: string;
}

export interface MenuItem {
  title: string;
  path: string;
  icon: string;
  roles: RoleCode[];
  children: MenuItem[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string | null;
  expiresAt: string | null;
  user: AuthUser;
  menus: MenuItem[];
  defaultPath: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  realName: string;
  roleType: 'STUDENT' | 'TEACHER';
  studentNo?: string;
  classId?: number | null;
  teacherNo?: string;
  title?: string;
  introduction?: string;
  phone?: string;
  email?: string;
}

export interface RegisterOptions {
  roles: Array<{ value: 'STUDENT' | 'TEACHER'; label: string }>;
  classes: Array<{ id: number; className: string; major?: string; grade?: string }>;
}

export interface OverviewCard {
  label: string;
  value: string | number;
  remark: string;
}

export interface RoleOverview {
  role: RoleCode;
  title: string;
  description: string;
  cards: OverviewCard[];
  nextModules: string[];
}

export function login(payload: LoginRequest) {
  return postJson<LoginResponse, LoginRequest>('/api/auth/login', payload);
}

export function logout() {
  return postJson<{ loggedOut: boolean }>('/api/auth/logout');
}

export function register(payload: RegisterRequest) {
  return postJson<LoginResponse, RegisterRequest>('/api/auth/register', payload);
}

export function fetchRegisterOptions() {
  return getJson<RegisterOptions>('/api/auth/register-options');
}

export function fetchCurrentUser() {
  return getJson<LoginResponse>('/api/auth/me');
}

export function fetchMenus() {
  return getJson<MenuItem[]>('/api/auth/menus');
}

export function changePassword(oldPassword: string, newPassword: string) {
  return putJson<{ changed: boolean }, { oldPassword: string; newPassword: string }>('/api/auth/password', { oldPassword, newPassword });
}

export function sendLoginCode(email: string) {
  return postJson<{ sent: boolean }, { email: string }>('/api/auth/send-login-code', { email });
}

export function loginByCode(email: string, code: string) {
  return postJson<LoginResponse, { email: string; code: string }>('/api/auth/login-by-code', { email, code });
}

export function sendBindCode(email: string) {
  return postJson<{ sent: boolean }, { email: string }>('/api/auth/send-bind-code', { email });
}

export function bindEmail(email: string, code: string) {
  return postJson<{ bound: boolean; email: string }, { email: string; code: string }>('/api/auth/bind-email', { email, code });
}

export function updateProfile(realName: string, phone: string) {
  return putJson<{ updated: boolean }, { realName: string; phone: string }>('/api/auth/profile', { realName, phone });
}

export function updateAvatar(avatar: string) {
  return putJson<{ updated: boolean }, { avatar: string }>('/api/auth/avatar', { avatar });
}

export function fetchAvatar() {
  return getJson<{ avatar: string }>('/api/auth/avatar');
}

export interface LoginLog {
  action?: string;
  ip?: string;
  detail?: string;
  created_at?: string;
}

export function fetchLoginLogs() {
  return getJson<LoginLog[]>('/api/auth/login-logs');
}

export function fetchRoleOverview(role: RoleCode) {
  const pathMap: Record<RoleCode, string> = {
    ADMIN: '/api/admin/overview',
    TEACHER: '/api/teacher/overview',
    STUDENT: '/api/student/overview',
    GUEST: '/api/student/overview'
  };
  return getJson<RoleOverview>(pathMap[role]);
}
