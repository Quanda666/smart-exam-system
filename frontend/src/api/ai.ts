import { postJson } from './request';
import type { Difficulty, QuestionInfo, QuestionOption, QuestionPayload, QuestionType } from './question';

export interface GenerateQuestionPayload {
  subject: string;
  knowledgePoint?: string;
  questionType: QuestionType;
  difficulty?: string;
  count?: number;
}

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

export function generateQuestion(payload: GenerateQuestionPayload) {
  return postJson<string, GenerateQuestionPayload>('/api/ai/generate-question', payload);
}

export function generateQuestionDrafts(payload: GenerateQuestionBatchPayload) {
  return postJson<AiGeneratedQuestion[], GenerateQuestionBatchPayload>('/api/ai/questions/generate', payload);
}

export function saveGeneratedQuestions(questions: AiGeneratedQuestion[]) {
  return postJson<SaveGeneratedQuestionsResult, { questions: AiGeneratedQuestion[] }>('/api/ai/questions/save', { questions });
}

export function explainText(text: string) {
  return postJson<string, { text: string }>('/api/ai/explain', { text });
}

export function explainWrongQuestion(payload: WrongQuestionExplainPayload) {
  return postJson<string, WrongQuestionExplainPayload>('/api/ai/wrong-question/explain', payload);
}

export function suggestReview(payload: SuggestReviewPayload) {
  return postJson<string, SuggestReviewPayload>('/api/ai/suggest-review', payload);
}
