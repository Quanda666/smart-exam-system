import { deleteJson, getJson, postJson, putJson } from './request';

export interface ClassInfo {
  id: number;
  className: string;
  classCode?: string;
  classType?: 'MAJOR' | 'ELECTIVE' | 'TEMPORARY';
  major: string;
  grade: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  operationLogId?: number | null;
}

export interface SubjectInfo {
  id: number;
  subjectName: string;
  description: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  operationLogId?: number | null;
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
  operationLogId?: number | null;
}

export interface CourseInfo {
  id: number;
  courseCode: string;
  courseName: string;
  subjectId?: number | null;
  subjectName?: string;
  credit?: number | null;
  description?: string;
  status: number;
  createdAt?: string;
  updatedAt?: string;
  operationLogId?: number | null;
}

export interface ClassCourseInfo {
  classCourseId: number;
  classId: number;
  className: string;
  classCode?: string;
  classType?: string;
  courseId: number;
  courseCode?: string;
  courseName: string;
  subjectId?: number | null;
  subjectName?: string;
  termName: string;
  status: number;
  operationLogId?: number | null;
}

export interface TeachingAssignmentInfo {
  id: number;
  teacherUserId: number;
  teacherName: string;
  teacherNo?: string;
  classCourseId: number;
  className: string;
  courseName: string;
  termName: string;
  teacherRole: string;
  status: number;
  operationLogId?: number | null;
}

export interface StudentMembershipInfo {
  id: number;
  studentUserId: number;
  studentName: string;
  studentNo?: string;
  classId: number;
  className: string;
  classType: string;
  membershipType: 'PRIMARY' | 'ELECTIVE' | 'TEMPORARY';
  source?: string;
  status: number;
  operationLogId?: number | null;
}

export interface NoticeTargetInfo {
  id?: number;
  noticeId?: number;
  targetType: 'SYSTEM' | 'ROLE' | 'CLASS' | 'CLASS_COURSE' | 'USER';
  targetId?: number;
  targetCode?: string;
}

export interface NoticeInfo {
  id: number;
  title: string;
  content: string;
  publisherId?: number;
  publisherName?: string;
  publishTime?: string;
  status: number;
  targets?: NoticeTargetInfo[];
  targetSummary?: string;
  createdAt?: string;
  updatedAt?: string;
  operationLogId?: number | null;
}

export interface BasicQuery {
  keyword?: string;
  status?: number | null;
  subjectId?: number | null;
  noticeId?: number | null;
}

export type DeleteResult = {
  deleted: boolean;
  id: number;
  operationLogId?: number | null;
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

export function listCourses(query?: BasicQuery) {
  return getJson<CourseInfo[]>(`/api/basic/courses${queryString(query)}`);
}

export function createCourse(payload: Omit<CourseInfo, 'id' | 'subjectName'>) {
  return postJson<CourseInfo, Omit<CourseInfo, 'id' | 'subjectName'>>('/api/basic/courses', payload);
}

export function updateCourse(id: number, payload: Omit<CourseInfo, 'id' | 'subjectName'>) {
  return putJson<CourseInfo, Omit<CourseInfo, 'id' | 'subjectName'>>(`/api/basic/courses/${id}`, payload);
}

export function deleteCourse(id: number) {
  return deleteJson<DeleteResult>(`/api/basic/courses/${id}`);
}

export function listClassCourses(query?: BasicQuery) {
  return getJson<ClassCourseInfo[]>(`/api/basic/class-courses${queryString(query)}`);
}

export function createClassCourse(payload: { classId: number; courseId: number; termName: string; status: number }) {
  return postJson<ClassCourseInfo, typeof payload>('/api/basic/class-courses', payload);
}

export function updateClassCourse(id: number, payload: { classId: number; courseId: number; termName: string; status: number }) {
  return putJson<ClassCourseInfo, typeof payload>(`/api/basic/class-courses/${id}`, payload);
}

export function deleteClassCourse(id: number) {
  return deleteJson<DeleteResult>(`/api/basic/class-courses/${id}`);
}

export function listTeachingAssignments(query: { teacherUserId?: number; classCourseId?: number } = {}) {
  const params = new URLSearchParams();
  if (query.teacherUserId) params.set('teacherUserId', String(query.teacherUserId));
  if (query.classCourseId) params.set('classCourseId', String(query.classCourseId));
  const value = params.toString();
  return getJson<TeachingAssignmentInfo[]>(`/api/basic/teaching-assignments${value ? `?${value}` : ''}`);
}

export function createTeachingAssignment(payload: { teacherUserId: number; classCourseId: number; teacherRole: string }) {
  return postJson<TeachingAssignmentInfo, typeof payload>('/api/basic/teaching-assignments', payload);
}

export function deleteTeachingAssignment(id: number) {
  return deleteJson<DeleteResult>(`/api/basic/teaching-assignments/${id}`);
}

export function listStudentMemberships(query: { studentUserId?: number; classId?: number } = {}) {
  const params = new URLSearchParams();
  if (query.studentUserId) params.set('studentUserId', String(query.studentUserId));
  if (query.classId) params.set('classId', String(query.classId));
  const value = params.toString();
  return getJson<StudentMembershipInfo[]>(`/api/basic/student-memberships${value ? `?${value}` : ''}`);
}

export function createStudentMembership(payload: { studentUserId: number; classId: number; membershipType: string; source?: string }) {
  return postJson<StudentMembershipInfo, typeof payload>('/api/basic/student-memberships', payload);
}

export function deleteStudentMembership(id: number) {
  return deleteJson<DeleteResult>(`/api/basic/student-memberships/${id}`);
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
