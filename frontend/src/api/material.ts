import { getJson, postForm, postJson } from './request';
import type { AiGeneratedQuestion } from './ai';
import type { Difficulty, QuestionType } from './question';

export interface CourseMaterial {
  id: number;
  subjectId: number;
  subjectName: string;
  title: string;
  fileName?: string;
  fileType?: string;
  uploadedBy?: number;
  uploaderName?: string;
  status: number;
  chunkCount?: number;
  outlineCount?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface MaterialOutlineItem {
  id?: number;
  outlineOrder?: number;
  title: string;
  summary?: string;
  keywords?: string;
  sourcePage?: number;
  sourceParagraph?: number;
}

export interface MaterialChunk {
  id?: number;
  chunkOrder?: number;
  pageNo: number;
  paragraphNo: number;
  heading?: string;
  content: string;
  keywords?: string;
}

export interface CourseMaterialDetail extends CourseMaterial {
  outline: MaterialOutlineItem[];
  chunks: MaterialChunk[];
}

export interface MaterialQuestionGeneratePayload {
  knowledgePointId?: number | null;
  knowledgePointName?: string | null;
  difficulty: Difficulty;
  defaultScore: number;
  requirements?: string;
  typeCounts: Record<QuestionType, number>;
}

export function uploadCourseMaterial(file: File, subjectId: number, title?: string) {
  const form = new FormData();
  form.set('file', file);
  form.set('subjectId', String(subjectId));
  if (title) form.set('title', title);
  return postForm<CourseMaterialDetail>('/api/materials', form);
}

export function listCourseMaterials(query: { keyword?: string; subjectId?: number | null } = {}) {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.subjectId !== undefined && query.subjectId !== null) params.set('subjectId', String(query.subjectId));
  const value = params.toString();
  return getJson<CourseMaterial[]>(`/api/materials${value ? `?${value}` : ''}`);
}

export function fetchCourseMaterial(id: number) {
  return getJson<CourseMaterialDetail>(`/api/materials/${id}`);
}

export function generateQuestionsFromLibraryMaterial(id: number, payload: MaterialQuestionGeneratePayload) {
  return postJson<AiGeneratedQuestion[], MaterialQuestionGeneratePayload>(`/api/materials/${id}/questions/generate`, payload);
}
