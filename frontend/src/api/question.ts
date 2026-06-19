import { deleteJson, getJson, postJson, putJson } from './request';

export type QuestionType = 'SINGLE_CHOICE' | 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'FILL_BLANK' | 'SUBJECTIVE';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type QuestionReviewStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'REJECTED';

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
  versionNo?: number;
  reviewStatus?: QuestionReviewStatus;
  reviewedBy?: number | null;
  reviewerName?: string | null;
  reviewedAt?: string | null;
  reviewComment?: string | null;
  canEdit?: boolean;
  canDelete?: boolean;
  canSubmitReview?: boolean;
  canReview?: boolean;
  canTakeOffline?: boolean;
  createdBy?: number;
  creatorName?: string;
  sourceType?: string;
  sourceDetail?: string | null;
  materialId?: number | null;
  sourcePage?: number | null;
  sourceParagraph?: number | null;
  sourceExcerpt?: string | null;
  aiModel?: string | null;
  promptVersion?: string | null;
  createdAt?: string;
  updatedAt?: string;
  questionReviewLogId?: number | null;
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
  sourceType?: string;
  sourceDetail?: string | null;
  materialId?: number | null;
  sourcePage?: number | null;
  sourceParagraph?: number | null;
  sourceExcerpt?: string | null;
  aiModel?: string | null;
  promptVersion?: string | null;
  options: QuestionOption[];
}

export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  size: number;
}

export interface QuestionQuery {
  keyword?: string;
  subjectId?: number | null;
  knowledgePointId?: number | null;
  questionType?: string | null;
  difficulty?: string | null;
  status?: number | null;
  reviewStatus?: QuestionReviewStatus | null;
  page?: number;
  size?: number;
}

export interface QuestionSummary {
  total: number;
  published: number;
  draft: number;
  pendingReview?: number;
  approvedReview?: number;
  rejectedReview?: number;
  types: Record<string, number>;
  difficulties: Record<string, number>;
}

export type DeleteResult = {
  deleted: boolean;
  id: number;
  questionReviewLogId?: number | null;
};

export interface QuestionReviewLog {
  id: number;
  questionId: number;
  versionNo: number;
  actionType: string;
  fromStatus?: number | null;
  toStatus?: number | null;
  fromReviewStatus?: QuestionReviewStatus | null;
  toReviewStatus?: QuestionReviewStatus | null;
  comment?: string | null;
  operatedBy?: number | null;
  operatorName?: string | null;
  operatedAt?: string;
}

function queryString(query: QuestionQuery = {}) {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.subjectId !== undefined && query.subjectId !== null) params.set('subjectId', String(query.subjectId));
  if (query.knowledgePointId !== undefined && query.knowledgePointId !== null) params.set('knowledgePointId', String(query.knowledgePointId));
  if (query.questionType) params.set('questionType', query.questionType);
  if (query.difficulty) params.set('difficulty', query.difficulty);
  if (query.status !== undefined && query.status !== null) params.set('status', String(query.status));
  if (query.reviewStatus) params.set('reviewStatus', query.reviewStatus);
  if (query.page !== undefined) params.set('page', String(query.page));
  if (query.size !== undefined) params.set('size', String(query.size));
  const value = params.toString();
  return value ? `?${value}` : '';
}

export function fetchQuestionSummary() {
  return getJson<QuestionSummary>('/api/questions/summary');
}

export function listQuestions(query?: QuestionQuery) {
  return getJson<PageResult<QuestionInfo>>(`/api/questions${queryString(query)}`);
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

export function submitQuestionReview(id: number) {
  return postJson<QuestionInfo>(`/api/questions/${id}/review/submit`);
}

export function approveQuestionReview(id: number, comment?: string) {
  return postJson<QuestionInfo, { comment?: string }>(`/api/questions/${id}/review/approve`, { comment });
}

export function rejectQuestionReview(id: number, comment: string) {
  return postJson<QuestionInfo, { comment: string }>(`/api/questions/${id}/review/reject`, { comment });
}

export function listQuestionReviewLogs(id: number) {
  return getJson<QuestionReviewLog[]>(`/api/questions/${id}/review-logs`);
}

export function deleteQuestion(id: number) {
  return deleteJson<DeleteResult>(`/api/questions/${id}`);
}
