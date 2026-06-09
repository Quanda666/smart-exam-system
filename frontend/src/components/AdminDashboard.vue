<template>
  <section class="dashboard admin-dashboard">
    <div class="dashboard-head">
      <div>
        <h2 class="mp-greeting">{{ greeting }}，管理员</h2>
        <p>系统用户、考试运行、题库和成绩趋势都在这里集中查看。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="emit('navigate', '/system/users')">新建用户</el-button>
    </div>

    <div class="mp-quick-actions">
      <div class="mp-quick-action" @click="emit('navigate', '/system/users')"><el-icon><Plus /></el-icon>新建用户</div>
      <div class="mp-quick-action" @click="emit('navigate', '/basic/notices')"><el-icon><Bell /></el-icon>发布公告</div>
      <div class="mp-quick-action" @click="emit('navigate', '/question-bank')"><el-icon><Collection /></el-icon>题库管理</div>
      <div class="mp-quick-action" @click="emit('navigate', '/exam/analysis')"><el-icon><DataAnalysis /></el-icon>成绩分析</div>
    </div>

    <div class="mp-stat-grid">
      <div v-for="card in statCards" :key="card.label" class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><component :is="card.icon" /></el-icon>
          {{ card.group }}
        </div>
        <div class="mp-stat-row">
          <div :class="['mp-stat-icon', card.iconClass]">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">{{ card.label }}</div>
            <div class="mp-stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><TrendCharts /></el-icon>
          近7天考试通过趋势
        </div>
        <div v-if="data.examTrend?.length" ref="examTrendChart" class="chart-box"></div>
        <el-empty v-else description="暂无提交记录" :image-size="80" />
      </div>

      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><Calendar /></el-icon>
          近期考试运行
        </div>
        <div class="exam-list">
          <button v-for="exam in data.recentExams" :key="exam.id || exam.name" type="button" class="exam-row" @click="emit('navigate', '/exam-tasks')">
            <span class="exam-dot" :class="phaseClass(exam.phase)"></span>
            <span class="exam-main">
              <strong>{{ exam.name }}</strong>
              <small>{{ formatDateTime(exam.time) }} 至 {{ formatDateTime(exam.endTime) }}</small>
            </span>
            <span class="exam-side">
              <el-tag :type="phaseTagType(exam.phase)" size="small">{{ phaseText(exam.phase) }}</el-tag>
              <small>{{ exam.submittedCount || 0 }}/{{ exam.attemptCount || 0 }} 已交</small>
            </span>
          </button>
          <el-empty v-if="!data.recentExams?.length" description="暂无考试" :image-size="80" />
        </div>
      </div>
    </div>

    <div class="dashboard-grid secondary">
      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><Histogram /></el-icon>
          题库学科分布
        </div>
        <div v-if="data.teacherSubjects?.length" ref="teacherSubjectChart" class="chart-box compact"></div>
        <el-empty v-else description="暂无题库数据" :image-size="80" />
      </div>

      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><PieChart /></el-icon>
          学生年级分布
        </div>
        <div v-if="data.studentGrades?.length" ref="studentGradeChart" class="chart-box compact"></div>
        <el-empty v-else description="暂无学生数据" :image-size="80" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  Avatar,
  Bell,
  Calendar,
  Collection,
  DataAnalysis,
  Document,
  Histogram,
  PieChart,
  Plus,
  Tickets,
  TrendCharts,
  User,
  UserFilled,
  Warning
} from '@element-plus/icons-vue';
import { getJson } from '../api/request';
import { useChartAutoResize } from '../composables/useChartAutoResize';
import { formatDateTime } from '../utils/dateFormat';
import * as echarts from 'echarts';

const emit = defineEmits<{ navigate: [path: string] }>();
const { register } = useChartAutoResize();

interface RecentExam {
  id?: number;
  name: string;
  time: string;
  endTime?: string;
  phase: number;
  attemptCount?: number;
  submittedCount?: number;
}

interface OverviewData {
  totalStudents: number;
  totalTeachers: number;
  todayExams: number;
  runningExams: number;
  pendingReviews: number;
  totalPapers: number;
  totalQuestions: number;
  teacherSubjects: Array<{ name: string; value: number }>;
  studentGrades: Array<{ name: string; value: number }>;
  examTrend: Array<{ date: string; total: number; passed: number }>;
  recentExams: RecentExam[];
}

const data = ref<OverviewData>({
  totalStudents: 0,
  totalTeachers: 0,
  todayExams: 0,
  runningExams: 0,
  pendingReviews: 0,
  totalPapers: 0,
  totalQuestions: 0,
  teacherSubjects: [],
  studentGrades: [],
  examTrend: [],
  recentExams: []
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 11) return '早上好';
  if (hour < 13) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const statCards = computed(() => [
  { group: '用户', label: '学生总数', value: data.value.totalStudents, icon: User, iconClass: 'mp-icon-blue', color: '#2563eb' },
  { group: '用户', label: '教师总数', value: data.value.totalTeachers, icon: Avatar, iconClass: 'mp-icon-green', color: '#16a34a' },
  { group: '考试', label: '今日考试', value: data.value.todayExams, icon: Calendar, iconClass: 'mp-icon-yellow', color: '#d97706' },
  { group: '考试', label: '进行中', value: data.value.runningExams, icon: TrendCharts, iconClass: 'mp-icon-blue', color: '#4f46e5' },
  { group: '处理', label: '待批阅', value: data.value.pendingReviews, icon: Warning, iconClass: 'mp-icon-orange', color: '#ea580c' },
  { group: '资源', label: '试卷/题目', value: `${data.value.totalPapers}/${data.value.totalQuestions}`, icon: Tickets, iconClass: 'mp-icon-purple', color: '#0f172a' }
]);

const teacherSubjectChart = ref<HTMLElement>();
const studentGradeChart = ref<HTMLElement>();
const examTrendChart = ref<HTMLElement>();

onMounted(async () => {
  try {
    data.value = (await getJson<OverviewData>('/api/overview/admin')).data;
    await new Promise((resolve) => setTimeout(resolve, 100));
    renderCharts();
  } catch {
    ElMessage.error('概况数据加载失败');
  }
});

function renderCharts() {
  if (examTrendChart.value && data.value.examTrend.length) {
    const chart = echarts.init(examTrendChart.value);
    register(chart, examTrendChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['提交人次', '通过人次'], top: 0 },
      xAxis: { type: 'category', data: data.value.examTrend.map((item) => item.date) },
      yAxis: { type: 'value' },
      series: [
        { name: '提交人次', type: 'line', smooth: true, data: data.value.examTrend.map((item) => item.total), itemStyle: { color: '#4f46e5' } },
        { name: '通过人次', type: 'line', smooth: true, data: data.value.examTrend.map((item) => item.passed), itemStyle: { color: '#16a34a' } }
      ],
      grid: { top: 42, right: 20, bottom: 28, left: 42 }
    });
  }

  if (teacherSubjectChart.value && data.value.teacherSubjects.length) {
    const chart = echarts.init(teacherSubjectChart.value);
    register(chart, teacherSubjectChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.value.teacherSubjects.map((item) => item.name), axisLabel: { rotate: 25 } },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: data.value.teacherSubjects.map((item) => item.value), itemStyle: { color: '#4f46e5', borderRadius: [6, 6, 0, 0] } }],
      grid: { top: 18, right: 18, bottom: 58, left: 42 }
    });
  }

  if (studentGradeChart.value && data.value.studentGrades.length) {
    const chart = echarts.init(studentGradeChart.value);
    register(chart, studentGradeChart.value);
    chart.setOption({
      tooltip: { trigger: 'item' },
      color: ['#4f46e5', '#16a34a', '#d97706', '#dc2626', '#7c3aed', '#0891b2'],
      series: [{
        type: 'pie',
        radius: ['44%', '72%'],
        data: data.value.studentGrades.map((item) => ({ name: item.name || '未分班', value: item.value })),
        label: { formatter: '{b}\n{c}人', fontSize: 12, color: '#475569' }
      }]
    });
  }
}

function phaseText(phase?: number) {
  if (phase === 1) return '进行中';
  if (phase === 2) return '已结束';
  return '待开始';
}

function phaseTagType(phase?: number) {
  if (phase === 1) return 'success';
  if (phase === 2) return 'info';
  return 'warning';
}

function phaseClass(phase?: number) {
  if (phase === 1) return 'running';
  if (phase === 2) return 'ended';
  return 'waiting';
}
</script>

<style scoped>
.dashboard-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.dashboard-head p {
  margin: 6px 0 0;
  color: #64748b;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 16px;
}

.dashboard-grid.secondary {
  margin-top: 16px;
}

.chart-box {
  width: 100%;
  height: 300px;
}

.chart-box.compact {
  height: 260px;
}

.exam-list {
  display: grid;
  gap: 10px;
}

.exam-row {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.exam-row:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.exam-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #f59e0b;
}

.exam-dot.running {
  background: #16a34a;
}

.exam-dot.ended {
  background: #94a3b8;
}

.exam-main,
.exam-side {
  display: grid;
  gap: 4px;
}

.exam-main {
  min-width: 0;
}

.exam-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exam-main small,
.exam-side small {
  color: #64748b;
}

.exam-side {
  justify-items: end;
}

@media (max-width: 900px) {
  .dashboard-head,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-head {
    display: grid;
  }
}
</style>
