import { deleteJson, getJson, postJson, putJson } from './request';

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_BLANK' | 'SUBJECTIVE';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';

export interface QuestionOption {
  id?: number;
  questionId?: number;
  optionLabel: string;
  optionContent: string;
  correct: boolean | number;
  sortOrder?: number;
}

export interface QuestionInfo {
  id: number;
  subjectId: number;
  subjectName: string;
  knowledgePointId: number | null;
  knowledgePointName?: string | null;
  questionType: QuestionType;
  difficulty: Difficulty;
  stem: string;
  correctAnswer?: string | null;
  analysis?: string | null;
  defaultScore: number;
  status: number;
  createdBy?: number;
  creatorName?: string;
  createdAt?: string;
  updatedAt?: string;
  options: QuestionOption[];
}

export interface QuestionPayload {
  subjectId: number;
  knowledgePointId: number | null;
  questionType: QuestionType;
  difficulty: Difficulty;
  stem: string;
  correctAnswer: string;
  analysis: string;
  defaultScore: number;
  status: number;
  options: QuestionOption[];
}

export interface QuestionQuery {
  keyword?: string;
  subjectId?: number | null;
  knowledgePointId?: number | null;
  questionType?: string | null;
  difficulty?: string | null;
  status?: number | null;
}

export interface QuestionSummary {
  total: number;
  published: number;
  draft: number;
  types: Record<string, number>;
  difficulties: Record<string, number>;
}

export type DeleteResult = {
  deleted: boolean;
  id: number;
};

function queryString(query: QuestionQuery = {}) {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.subjectId !== undefined && query.subjectId !== null) params.set('subjectId', String(query.subjectId));
  if (query.knowledgePointId !== undefined && query.knowledgePointId !== null) params.set('knowledgePointId', String(query.knowledgePointId));
  if (query.questionType) params.set('questionType', query.questionType);
  if (query.difficulty) params.set('difficulty', query.difficulty);
  if (query.status !== undefined && query.status !== null) params.set('status', String(query.status));
  const value = params.toString();
  return value ? `?${value}` : '';
}

export function fetchQuestionSummary() {
  return getJson<QuestionSummary>('/api/questions/summary');
}

export function listQuestions(query?: QuestionQuery) {
  return getJson<QuestionInfo[]>(`/api/questions${queryString(query)}`);
}

export function createQuestion(payload: QuestionPayload) {
  return postJson<QuestionInfo, QuestionPayload>('/api/questions', payload);
}

export function updateQuestion(id: number, payload: QuestionPayload) {
  return putJson<QuestionInfo, QuestionPayload>(`/api/questions/${id}`, payload);
}

export function updateQuestionStatus(id: number, status: number) {
  return putJson<QuestionInfo, { status: number }>(`/api/questions/${id}/status`, { status });
}

export function deleteQuestion(id: number) {
  return deleteJson<DeleteResult>(`/api/questions/${id}`);
}
