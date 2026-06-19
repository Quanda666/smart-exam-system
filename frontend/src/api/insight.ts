import { downloadFile, getJson } from './request';

export interface ClassStudent {
  userId: number;
  username: string;
  realName: string;
  studentNo?: string;
  status: number;
  className?: string;
  completedCount: number;
  avgScore: number;
}

export interface StudentExamRecord {
  examName: string;
  subjectName: string;
  score: number | null;
  totalScore: number;
  questionCount?: number | string | null;
  answeredCount?: number | string | null;
  unansweredCount?: number | string | null;
  submitTime: string;
}

export interface StudentInsightData {
  student: { realName: string; username: string; studentNo?: string; className?: string };
  exams: StudentExamRecord[];
  summary: { count: number; avgScore: number; maxScore: number; minScore: number };
}

export function listClassStudents(classId: number) {
  return getJson<ClassStudent[]>(`/api/insight/classes/${classId}/students`);
}

export function getStudentInsight(userId: number) {
  return getJson<StudentInsightData>(`/api/insight/students/${userId}`);
}

export function exportClassStudents(classId: number, className?: string) {
  return downloadFile(`/api/insight/classes/${classId}/students/export`, `${className || 'class'}-学生名单.csv`);
}

export function exportStudentScores(userId: number, realName?: string) {
  return downloadFile(`/api/insight/students/${userId}/export`, `${realName || 'student'}-成绩历史.csv`);
}
