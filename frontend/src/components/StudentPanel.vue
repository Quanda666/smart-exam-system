<template>
  <section class="student-exam-page mp-page">
    <div class="mp-page-header">
      <div>
        <h3 class="mp-page-title">考试中心</h3>
        <p class="mp-page-desc">待参加、进行中、已完成考试统一管理，开始考试后进入全屏作答。</p>
      </div>
    </div>

    <ExamList @view-result="viewResult" @start-exam="startExam" />

    <el-drawer v-model="drawerVisible" title="考试结果详情" direction="rtl" size="52%">
      <div v-if="examResult" class="result-drawer">
        <el-descriptions :title="examResult.gradeInfo.examName" :column="2" border>
          <el-descriptions-item label="总分">{{ examResult.gradeInfo.score ?? '待批阅' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(examResult.gradeInfo.status) }}</el-descriptions-item>
          <el-descriptions-item label="科目">{{ examResult.gradeInfo.subjectName }}</el-descriptions-item>
          <el-descriptions-item label="交卷时间">{{ formatDateTime(examResult.gradeInfo.submitTime) }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <div v-for="(answer, index) in examResult.answers" :key="index" class="answer-block">
          <p>
            <strong>{{ index + 1 }}. {{ answer.stem }}</strong>
            <el-tag size="small">{{ typeText(answer.questionType) }}</el-tag>
          </p>
          <p :class="answer.isCorrect ? 'text-success' : 'text-danger'">你的答案：{{ answer.studentAnswer || '未作答' }}</p>
          <p>正确答案：{{ answer.correctAnswer || '待批阅' }}</p>
          <p>得分：{{ answer.score }}</p>
          <p v-if="answer.analysis">解析：{{ answer.analysis }}</p>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ElMessage } from 'element-plus';
import ExamList from './ExamList.vue';
import { getExamResult, type ExamResult } from '../api/student';
import type { StudentExamInfo } from '../api/exam';
import { formatDateTime } from '../utils/dateFormat';

const emit = defineEmits<{
  (e: 'start-exam', exam: StudentExamInfo): void;
}>();

const drawerVisible = ref(false);
const examResult = ref<ExamResult | null>(null);

function startExam(exam: StudentExamInfo) {
  emit('start-exam', exam);
}

async function viewResult(attemptId: number) {
  try {
    examResult.value = (await getExamResult(attemptId)).data;
    drawerVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查看结果失败');
  }
}

function statusText(status?: number) {
  if (status === 4) return '待批阅';
  if (status === 5) return '已出分';
  if (status === 2) return '已交卷';
  return '未完成';
}

function typeText(type?: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SUBJECTIVE: '主观题'
  };
  return type ? map[type] || type : '未知题型';
}
</script>

<style scoped>
.student-exam-page {
  gap: 16px;
}

.result-drawer .answer-block {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.result-drawer .answer-block p {
  margin: 8px 0;
  line-height: 1.7;
}

.result-drawer .text-success {
  color: #16a34a;
}

.result-drawer .text-danger {
  color: #dc2626;
}
</style>
