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
  profile: Record<string, unknown>;
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

export function fetchRoleOverview(role: RoleCode) {
  const pathMap: Record<RoleCode, string> = {
    ADMIN: '/api/admin/overview',
    TEACHER: '/api/teacher/overview',
    STUDENT: '/api/student/overview',
    GUEST: '/api/student/overview'
  };
  return getJson<RoleOverview>(pathMap[role]);
}
