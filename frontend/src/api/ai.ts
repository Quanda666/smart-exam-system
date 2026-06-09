import { postForm, postJson } from './request';
import type { Difficulty, QuestionInfo, QuestionOption, QuestionPayload, QuestionType } from './question';

export interface SuggestReviewPayload {
  question: string;
  studentAnswer: string;
  correctAnswer?: string;
}

export interface GenerateQuestionBatchPayload {
  subjectId: number;
  subjectName: string;
  knowledgePointId?: number | null;
  knowledgePointName?: string | null;
  questionType: QuestionType;
  difficulty: Difficulty;
  count: number;
  defaultScore: number;
  requirements?: string;
}

export type AiGeneratedQuestion = QuestionPayload;

export interface QuestionDocumentPayload {
  subjectId: number;
  subjectName: string;
  knowledgePointId?: number | null;
  knowledgePointName?: string | null;
  difficulty: Difficulty;
  defaultScore: number;
}

export interface MaterialQuestionPayload extends QuestionDocumentPayload {
  requirements?: string;
  typeCounts: Record<QuestionType, number>;
}

export interface SaveGeneratedQuestionsResult {
  savedCount: number;
  questions: QuestionInfo[];
}

export interface WrongQuestionExplainPayload {
  questionId?: number;
  stem: string;
  questionType?: string;
  studentAnswer?: string;
  correctAnswer?: string;
  analysis?: string;
  wrongCount?: number;
  options?: QuestionOption[];
}

export function generateQuestionDrafts(payload: GenerateQuestionBatchPayload) {
  return postJson<AiGeneratedQuestion[], GenerateQuestionBatchPayload>('/api/ai/questions/generate', payload);
}

export function importQuestionDocument(file: File, payload: QuestionDocumentPayload) {
  const form = contextForm(file, payload);
  return postForm<AiGeneratedQuestion[]>('/api/ai/questions/import-document', form);
}

export function generateQuestionsFromMaterial(file: File, payload: MaterialQuestionPayload) {
  const form = contextForm(file, payload);
  form.set('requirements', payload.requirements || '');
  form.set('singleChoiceCount', String(payload.typeCounts.SINGLE_CHOICE || 0));
  form.set('multipleChoiceCount', String(payload.typeCounts.MULTIPLE_CHOICE || 0));
  form.set('trueFalseCount', String(payload.typeCounts.TRUE_FALSE || 0));
  form.set('fillBlankCount', String(payload.typeCounts.FILL_BLANK || 0));
  form.set('subjectiveCount', String(payload.typeCounts.SUBJECTIVE || 0));
  return postForm<AiGeneratedQuestion[]>('/api/ai/questions/generate-from-material', form);
}

export function saveGeneratedQuestions(questions: AiGeneratedQuestion[]) {
  return postJson<SaveGeneratedQuestionsResult, { questions: AiGeneratedQuestion[] }>('/api/ai/questions/save', { questions });
}

export function explainWrongQuestion(payload: WrongQuestionExplainPayload) {
  return postJson<string, WrongQuestionExplainPayload>('/api/ai/wrong-question/explain', payload);
}

export function suggestReview(payload: SuggestReviewPayload) {
  return postJson<string, SuggestReviewPayload>('/api/ai/suggest-review', payload);
}

function contextForm(file: File, payload: QuestionDocumentPayload) {
  const form = new FormData();
  form.set('file', file);
  form.set('subjectId', String(payload.subjectId));
  form.set('subjectName', payload.subjectName);
  if (payload.knowledgePointId !== undefined && payload.knowledgePointId !== null) {
    form.set('knowledgePointId', String(payload.knowledgePointId));
  }
  if (payload.knowledgePointName) {
    form.set('knowledgePointName', payload.knowledgePointName);
  }
  form.set('difficulty', payload.difficulty);
  form.set('defaultScore', String(payload.defaultScore));
  return form;
}
