import { getJson } from './request';

export interface GradeInfo {
  attemptId: number;
  examName: string;
  subjectName: string;
  score: number;
  submitTime: string;
  status: number;
}

export interface ExamResult {
  gradeInfo: GradeInfo;
  answers: Array<{
    stem: string;
    questionType: string;
    correctAnswer: string;
    analysis: string;
    studentAnswer: string;
    score: number;
    isCorrect: boolean;
  }>;
}

export interface WrongQuestion {
    questionId: number;
    stem: string;
    questionType: string;
    correctAnswer: string;
    analysis: string;
    wrongCount: number;
    lastWrongTime: string;
    options: Array<{
        optionLabel: string;
        optionContent: string;
        isCorrect: boolean;
    }>;
}

export function getGrades() {
  return getJson<GradeInfo[]>('/api/student/grades');
}

export function getExamResult(attemptId: number) {
  return getJson<ExamResult>(`/api/student/exam-result/${attemptId}`);
}

export function getWrongQuestions() {
    return getJson<WrongQuestion[]>('/api/student/wrong-questions');
}

export function getKnowledgePointMastery() {
    return getJson<Record<string, number>>('/api/student/mastery');
}
