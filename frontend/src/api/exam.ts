import { deleteJson, downloadFile, getJson, postJson, putJson } from './request';
import type { PaperInfo, PaperQuestionInfo } from './paper';
import type { QuestionOption } from './question';

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
  targetSummary?: string;
  attemptCount?: number;
  submittedCount?: number;
}

export interface StudentExamInfo {
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
  submitTime?: string;
}

export interface ExamPayload {
  paperId: number;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  classIds?: number[];
  classCourseIds?: number[];
  studentUserIds?: number[];
}

export interface AnswerPayload {
  answers: Record<number, string>;
}

export interface ExamDetail extends PaperInfo {
  examName: string;
  durationMinutes: number;
  questions: Array<PaperQuestionInfo & { options?: Array<Omit<QuestionOption, 'correct'> & { correct?: boolean | number }> }>;
  remainingSeconds?: number;
  draftAnswers?: string | null;
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export function listTeacherExams(query?: { keyword?: string; status?: number | null; page?: number; size?: number }) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.status !== undefined && query.status !== null) params.set('status', String(query.status));
  if (query?.page !== undefined) params.set('page', String(query.page));
  if (query?.size !== undefined) params.set('size', String(query.size));
  const value = params.toString();
  return getJson<PageResult<ExamInfo>>(`/api/exams/teacher${value ? `?${value}` : ''}`);
}

export function listStudentExams(page = 1, size = 10) {
  return getJson<PageResult<StudentExamInfo>>(`/api/exams/student?page=${page}&size=${size}`);
}

export function createExam(payload: ExamPayload) {
  return postJson<ExamInfo, ExamPayload>('/api/exams', payload);
}

export interface ExamUpdatePayload {
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
}

export function updateExam(id: number, payload: ExamUpdatePayload) {
  return putJson<ExamInfo, ExamUpdatePayload>(`/api/exams/${id}`, payload);
}

export function deleteExam(id: number) {
  return deleteJson<{ id: number; deleted: boolean }>(`/api/exams/${id}`);
}

export function closeExam(id: number) {
  return putJson<{ id: number }>(`/api/exams/${id}/close`);
}

export function exportExamScores(examId: number, examName?: string) {
  return downloadFile(`/api/exams/${examId}/scores/export`, `${examName || 'exam'}-成绩单.csv`);
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

export function saveExamDraft(attemptId: number, answersJson: string) {
  return postJson<{ saved: boolean }, { answers: string }>(
    `/api/exams/attempt/${attemptId}/save`,
    { answers: answersJson }
  );
}
