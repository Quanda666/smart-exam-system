import { deleteJson, getJson, postJson, putJson } from './request';
import type { Difficulty, QuestionType } from './question';

export interface PaperQuestionInfo {
  id?: number;
  paperId?: number;
  questionId: number;
  score: number;
  sortOrder: number;
  questionType?: QuestionType;
  difficulty?: Difficulty;
  stem?: string;
  analysis?: string | null;
  subjectId?: number;
  subjectName?: string;
  knowledgePointId?: number | null;
  knowledgePointName?: string | null;
}

export interface PaperInfo {
  id: number;
  subjectId: number;
  subjectName: string;
  paperName: string;
  description?: string | null;
  totalScore: number;
  status: number;
  createdBy?: number;
  creatorName?: string;
  createdAt?: string;
  updatedAt?: string;
  questionCount: number;
  questions?: PaperQuestionInfo[];
}

export interface PaperPayload {
  subjectId: number;
  paperName: string;
  description: string;
  status: number;
  questions: Array<{
    questionId: number;
    score: number;
    sortOrder: number;
  }>;
}

export interface GenerateRulePayload {
  knowledgePointId: number | null;
  questionType: QuestionType;
  difficulty: Difficulty | null;
  count: number;
  score: number;
}

export interface GeneratePaperPayload {
  subjectId: number;
  paperName: string;
  description: string;
  status: number;
  rules: GenerateRulePayload[];
}

export interface PaperQuery {
  keyword?: string;
  subjectId?: number | null;
  status?: number | null;
}

export interface PaperSummary {
  total: number;
  published: number;
  draft: number;
  totalQuestions: number;
}

export type DeleteResult = {
  deleted: boolean;
  id: number;
};

function queryString(query: PaperQuery = {}) {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.subjectId !== undefined && query.subjectId !== null) params.set('subjectId', String(query.subjectId));
  if (query.status !== undefined && query.status !== null) params.set('status', String(query.status));
  const value = params.toString();
  return value ? `?${value}` : '';
}

export function fetchPaperSummary() {
  return getJson<PaperSummary>('/api/papers/summary');
}

export function listPapers(query?: PaperQuery) {
  return getJson<PaperInfo[]>(`/api/papers${queryString(query)}`);
}

export function getPaper(id: number) {
  return getJson<PaperInfo>(`/api/papers/${id}`);
}

export function createPaper(payload: PaperPayload) {
  return postJson<PaperInfo, PaperPayload>('/api/papers', payload);
}

export function generatePaper(payload: GeneratePaperPayload) {
  return postJson<PaperInfo, GeneratePaperPayload>('/api/papers/generate', payload);
}

export function updatePaper(id: number, payload: PaperPayload) {
  return putJson<PaperInfo, PaperPayload>(`/api/papers/${id}`, payload);
}

export function updatePaperStatus(id: number, status: number) {
  return putJson<PaperInfo, { status: number }>(`/api/papers/${id}/status`, { status });
}

export function deletePaper(id: number) {
  return deleteJson<DeleteResult>(`/api/papers/${id}`);
}
