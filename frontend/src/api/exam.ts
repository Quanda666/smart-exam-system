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
  classIds: number[];
}

export interface AnswerPayload {
  answers: Record<number, string>;
}

export interface ExamDetail extends PaperInfo {
  examName: string;
  durationMinutes: number;
  questions: Array<PaperQuestionInfo & { options: QuestionOption[] }>;
  remainingSeconds?: number;
  draftAnswers?: string | null;
}

export function listTeacherExams(query?: { keyword?: string; status?: number | null }) {
  const params = new URLSearchParams();
  if (query?.keyword) params.set('keyword', query.keyword);
  if (query?.status !== undefined && query.status !== null) params.set('status', String(query.status));
  const value = params.toString();
  return getJson<ExamInfo[]>(`/api/exams/teacher${value ? `?${value}` : ''}`);
}

export function listStudentExams() {
  return getJson<StudentExamInfo[]>('/api/exams/student');
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
