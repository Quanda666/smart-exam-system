import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';

export const BASIC_DATA_ROOT = '/basic/data';
export const BASIC_DATA_PATHS = new Set([
  BASIC_DATA_ROOT,
  '/basic/classes',
  '/basic/courses',
  '/basic/class-courses',
  '/basic/teaching-assignments',
  '/basic/subjects',
  '/basic/knowledge-points',
  '/basic/notices'
]);

const routes: RouteRecordRaw[] = [
  { path: '/', name: 'login', component: { template: '<div />' } },
  { path: '/:pathMatch(.*)*', name: 'workspace', component: { template: '<div />' } }
];

export const router = createRouter({
  history: createWebHistory(),
  routes
});

export function resolveMenuAccessPath(path: string) {
  return BASIC_DATA_PATHS.has(path) ? BASIC_DATA_ROOT : path;
}

export function isKnownWorkspacePath(path: string) {
  return path !== '/';
}

