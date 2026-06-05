import { getJson, postJson } from './request';

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

export interface DemoUser {
  username: string;
  password: string;
  roleLabel: string;
  defaultPath: string;
  description: string;
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

export function fetchCurrentUser() {
  return getJson<LoginResponse>('/api/auth/me');
}

export function fetchMenus() {
  return getJson<MenuItem[]>('/api/auth/menus');
}

export function fetchDemoUsers() {
  return getJson<DemoUser[]>('/api/auth/demo-users');
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
