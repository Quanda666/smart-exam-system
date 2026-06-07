import { getJson } from './request';

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
