<template>
  <section class="student-results-page mp-page">
    <div class="mp-page-header">
      <div>
        <h3 class="mp-page-title">成绩查询</h3>
      </div>
      <div class="mp-page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadGrades(true)">刷新</el-button>
      </div>
    </div>

    <div class="mp-stat-grid">
      <div v-for="card in summaryCards" :key="card.label" class="mp-stat-card">
        <div class="mp-stat-header">{{ card.label }}</div>
        <div class="mp-stat-row">
          <div class="mp-stat-content">
            <div class="mp-stat-value" :class="card.className">{{ card.value }}</div>
            <div class="mp-stat-label">{{ card.remark }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="mp-table-card">
      <el-table v-loading="loading" :data="grades" border>
        <el-table-column prop="examName" label="考试名称" min-width="220" />
        <el-table-column prop="subjectName" label="科目" width="150" />
        <el-table-column label="分数" width="120">
          <template #default="scope">
            <strong>{{ scoreText(scope.row.score) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.status === 5 ? 'success' : 'warning'">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.submitTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="View" @click="viewResult(scope.row.attemptId)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && grades.length === 0" description="暂无成绩记录" />
    </div>

    <el-drawer v-model="drawerVisible" title="考试结果详情" direction="rtl" size="52%">
      <div v-if="examResult" class="result-drawer">
        <el-descriptions :title="examResult.gradeInfo.examName" :column="2" border>
          <el-descriptions-item label="总分">{{ scoreText(examResult.gradeInfo.score) }}</el-descriptions-item>
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
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh, View } from '@element-plus/icons-vue';
import { getExamResult, getGrades, type ExamResult, type GradeInfo } from '../api/student';
import { formatDateTime } from '../utils/dateFormat';

const loading = ref(false);
const grades = ref<GradeInfo[]>([]);
const drawerVisible = ref(false);
const examResult = ref<ExamResult | null>(null);

const summaryCards = computed(() => {
  const graded = grades.value.filter((item) => item.status === 5);
  const pending = grades.value.filter((item) => item.status === 4);
  const average = graded.length
    ? (graded.reduce((sum, item) => sum + Number(item.score || 0), 0) / graded.length).toFixed(1)
    : '—';
  return [
    { label: '记录数', value: grades.value.length, remark: '已提交考试', className: '' },
    { label: '已出分', value: graded.length, remark: '完成评分', className: 'mp-val-ok' },
    { label: '待批阅', value: pending.length, remark: '主观题处理中', className: 'mp-val-warn' },
    { label: '平均分', value: average, remark: '已出分考试', className: '' }
  ];
});

onMounted(() => {
  loadGrades();
});

async function loadGrades(manual = false) {
  loading.value = true;
  try {
    grades.value = (await getGrades()).data;
    if (manual) {
      ElMessage.success('成绩已刷新');
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成绩列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function viewResult(attemptId: number) {
  try {
    examResult.value = (await getExamResult(attemptId)).data;
    drawerVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查看结果失败');
  }
}

function scoreText(score: number | string | null | undefined) {
  return score === null || score === undefined ? '待批阅' : score;
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

.text-success {
  color: #16a34a;
}

.text-danger {
  color: #dc2626;
}
</style>
