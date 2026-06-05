import { getJson, postJson } from './request';
import type { PaperInfo } from './paper';

export interface ExamInfo {
  id: number;
  paperId: number;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  status: number;
  paperName?: string;
  subjectName?: string;
}

export interface ExamAttempt {
  attemptId: number;
  examId: number;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  status: number;
  paperName: string;
  subjectName: string;
  score?: number;
}

export interface ExamPayload {
  paperId: number;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  classIds: number[];
}

export interface AnswerPayload {
  answers: Record<number, string>;
}

export interface ExamDetail extends PaperInfo {
  // Inherits paper details and questions
}

export function listTeacherExams(query?: { keyword?: string; status?: number | null }) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.status !== undefined && query.status !== null) params.set('status', String(query.status));
  const value = params.toString();
  return getJson<ExamInfo[]>(`/api/exams/teacher${value ? `?${value}` : ''}`);
}

export function listStudentExams() {
  return getJson<ExamAttempt[]>('/api/exams/student');
}

export function createExam(payload: ExamPayload) {
  return postJson<ExamInfo, ExamPayload>('/api/exams', payload);
}

export function startExam(attemptId: number) {
  return postJson<ExamDetail>(`/api/exams/attempt/${attemptId}/start`);
}

export function submitExam(attemptId: number, payload: AnswerPayload) {
  return postJson<{ success: boolean; message: string; score: number; status: number }, AnswerPayload>(
    `/api/exams/attempt/${attemptId}/submit`,
    payload
  );
}
