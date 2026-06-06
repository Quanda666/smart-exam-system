import { postJson } from './request';
import type { QuestionType } from './question';

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

export function generateQuestion(payload: GenerateQuestionPayload) {
  return postJson<string, GenerateQuestionPayload>('/api/ai/generate-question', payload);
}

export function explainText(text: string) {
  return postJson<string, { text: string }>('/api/ai/explain', { text });
}

export function suggestReview(payload: SuggestReviewPayload) {
  return postJson<string, SuggestReviewPayload>('/api/ai/suggest-review', payload);
}
