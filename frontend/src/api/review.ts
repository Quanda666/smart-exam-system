import { getJson, postJson } from './request';

export interface PendingReview {
  attemptId: number;
  examName: string;
  studentName: string;
  pendingCount: number;
}

export interface ReviewAnswer {
  answerRecordId: number;
  stem: string;
  correctAnswer: string;
  studentAnswer: string;
  score: number | null;
  isCorrect: boolean | null;
}

export interface ReviewDetail {
  attemptId: number;
  examName: string;
  studentName: string;
  status: number;
  answers: ReviewAnswer[];
}

export interface ReviewPayload {
  answerRecordId: number;
  score: number;
  comment: string;
}

export function getPendingReviews() {
  return getJson<PendingReview[]>('/api/reviews/pending');
}

export function getReviewDetails(attemptId: number) {
  return getJson<ReviewDetail>(`/api/reviews/attempt/${attemptId}`);
}

export function submitReview(attemptId: number, payload: ReviewPayload[]) {
  return postJson<any, ReviewPayload[]>(`/api/reviews/attempt/${attemptId}`, payload);
}
