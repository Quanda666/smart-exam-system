<template>
  <section class="dashboard teacher-dashboard">
    <div class="dashboard-head">
      <div>
        <h2 class="mp-greeting">{{ greeting }}，老师</h2>
        <p>这里聚合今天最需要处理的考试、阅卷和学情入口。</p>
      </div>
      <el-button type="primary" :icon="Calendar" @click="emit('navigate', '/exam-tasks')">发布考试</el-button>
    </div>

    <div class="mp-quick-actions">
      <div class="mp-quick-action" @click="emit('navigate', '/reviews')"><el-icon><DocumentChecked /></el-icon>批阅试卷</div>
      <div class="mp-quick-action" @click="emit('navigate', '/reviews?appealStatus=0&appealHandlingResult=ALL')"><el-icon><Tickets /></el-icon>成绩申诉</div>
      <div class="mp-quick-action" @click="emit('navigate', '/papers')"><el-icon><Document /></el-icon>组卷</div>
      <div class="mp-quick-action" @click="emit('navigate', '/exam-tasks')"><el-icon><Calendar /></el-icon>考试任务</div>
      <div class="mp-quick-action" @click="emit('navigate', '/teacher/analysis')"><el-icon><DataAnalysis /></el-icon>学情分析</div>
    </div>

    <div class="mp-stat-grid">
      <div
        v-for="card in statCards"
        :key="card.label"
        :class="['mp-stat-card', { clickable: card.target }]"
        @click="card.target && emit('navigate', card.target)"
      >
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

    <div class="mp-card teacher-action-center">
      <div class="mp-card-title teacher-action-title">
        <span>
          <el-icon><Clock /></el-icon>
          Action Center
        </span>
        <el-tag :type="data.actionCenter.total > 0 ? 'warning' : 'success'" effect="plain">
          {{ data.actionCenter.total }} open
        </el-tag>
      </div>
      <div class="teacher-action-summary">
        <button
          v-for="item in actionSummaryRows"
          :key="item.key"
          type="button"
          class="teacher-action-summary-item"
          @click="emit('navigate', item.target)"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </button>
      </div>
      <div class="teacher-action-list">
        <button
          v-for="item in data.actionCenter.items"
          :key="actionItemKey(item)"
          type="button"
          class="teacher-action-row"
          @click="openActionItem(item)"
        >
          <span :class="['teacher-action-icon', `severity-${String(item.severity || 'INFO').toLowerCase()}`]">
            <el-icon><component :is="actionIcon(item.type)" /></el-icon>
          </span>
          <span class="teacher-action-main">
            <strong>{{ item.title }}</strong>
            <small>{{ item.subject || '-' }}</small>
            <small>{{ item.detail || '-' }}</small>
          </span>
          <span class="teacher-action-side">
            <el-tag :type="actionSeverityType(item.severity)" size="small">{{ item.count }}</el-tag>
          </span>
        </button>
        <el-empty v-if="data.actionCenter.items.length === 0" description="No open teacher actions" :image-size="80" />
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><PieChart /></el-icon>
          学生成绩分布
        </div>
        <div v-if="data.scoreDistribution?.length" ref="scoreDistChart" class="chart-box"></div>
        <el-empty v-else description="暂无已提交成绩" :image-size="80" />
      </div>

      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><Calendar /></el-icon>
          近期考试安排
        </div>
        <div class="exam-list">
          <button v-for="exam in data.recentExams" :key="exam.id || exam.name" type="button" class="exam-row" @click="openRecentExam(exam)">
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
          <el-empty v-if="!data.recentExams?.length" description="暂无考试安排" :image-size="80" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Calendar, Clock, DataAnalysis, Document, DocumentChecked, Files, PieChart, Tickets, TrendCharts } from '@element-plus/icons-vue';
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

interface TeacherActionItem {
  type: 'REVIEW' | 'APPEAL' | 'RECHECK' | 'SCORE_BLOCKED' | 'SCORE_READY' | 'LIFECYCLE_HEALTH' | string;
  title: string;
  subject?: string;
  detail?: string;
  count: number | string;
  severity?: 'HIGH' | 'WARN' | 'INFO' | string;
  target: string;
  examId?: number | null;
  appealId?: number | null;
}

interface TeacherActionCenter {
  generatedAt?: string;
  total: number;
  lifecycleActionRequiredExams: number;
  lifecycleRiskExams: number;
  pendingReviewExams: number;
  pendingAppeals: number;
  openRechecks: number;
  scoreBlockedExams: number;
  readyToPublishExams: number;
  items: TeacherActionItem[];
}

interface TeacherOverview {
  myExams: number;
  runningExams: number;
  upcomingExams: number;
  finishedExams: number;
  pendingReviews: number;
  pendingAppeals: number;
  recheckAppeals: number;
  myPapers: number;
  publishedPapers: number;
  avgScore: number;
  scoreDistribution: Array<{ name: string; value: number }>;
  recentExams: RecentExam[];
  actionCenter: TeacherActionCenter;
}

const emptyActionCenter = (): TeacherActionCenter => ({
  total: 0,
  lifecycleActionRequiredExams: 0,
  lifecycleRiskExams: 0,
  pendingReviewExams: 0,
  pendingAppeals: 0,
  openRechecks: 0,
  scoreBlockedExams: 0,
  readyToPublishExams: 0,
  items: []
});

const data = ref<TeacherOverview>({
  myExams: 0,
  runningExams: 0,
  upcomingExams: 0,
  finishedExams: 0,
  pendingReviews: 0,
  pendingAppeals: 0,
  recheckAppeals: 0,
  myPapers: 0,
  publishedPapers: 0,
  avgScore: 0,
  scoreDistribution: [],
  recentExams: [],
  actionCenter: emptyActionCenter()
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
  { group: '考试任务', label: '进行中', value: data.value.runningExams, icon: Clock, iconClass: 'mp-icon-blue', color: '#2563eb' },
  { group: '考试任务', label: '待开始', value: data.value.upcomingExams, icon: Calendar, iconClass: 'mp-icon-green', color: '#16a34a' },
  { group: '待处理', label: '待批阅试卷', value: data.value.pendingReviews, icon: DocumentChecked, iconClass: 'mp-icon-orange', color: '#ea580c', target: '/reviews' },
  { group: '成绩申诉', label: '待处理申诉', value: data.value.pendingAppeals, icon: Tickets, iconClass: 'mp-icon-orange', color: '#dc2626', target: '/reviews?appealStatus=0&appealHandlingResult=ALL' },
  { group: '成绩申诉', label: '需复核申诉', value: data.value.recheckAppeals, icon: Tickets, iconClass: 'mp-icon-purple', color: '#7c3aed', target: '/reviews?appealStatus=1&appealHandlingResult=RECHECK_REQUIRED' },
  { group: '试卷管理', label: '已发布试卷', value: `${data.value.publishedPapers}/${data.value.myPapers}`, icon: Files, iconClass: 'mp-icon-purple', color: '#4f46e5' },
  { group: '学情分析', label: '平均分', value: data.value.avgScore, icon: TrendCharts, iconClass: 'mp-icon-blue', color: '#0f172a' }
]);

const actionSummaryRows = computed(() => [
  { key: 'lifecycle', label: 'Lifecycle', value: data.value.actionCenter.lifecycleActionRequiredExams, target: '/exam-tasks' },
  { key: 'reviews', label: 'Review exams', value: data.value.actionCenter.pendingReviewExams, target: '/reviews' },
  { key: 'appeals', label: 'Appeals', value: data.value.actionCenter.pendingAppeals, target: '/reviews?appealStatus=0&appealHandlingResult=ALL' },
  { key: 'rechecks', label: 'Rechecks', value: data.value.actionCenter.openRechecks, target: '/reviews?appealStatus=1&appealHandlingResult=RECHECK_REQUIRED' },
  { key: 'blocked', label: 'Score blocked', value: data.value.actionCenter.scoreBlockedExams, target: '/exam-tasks' },
  { key: 'ready', label: 'Ready publish', value: data.value.actionCenter.readyToPublishExams, target: '/exam-tasks' }
]);

const scoreDistChart = ref<HTMLElement>();

onMounted(async () => {
  try {
    const overview = (await getJson<TeacherOverview>('/api/overview/teacher')).data;
    data.value = {
      ...data.value,
      ...overview,
      actionCenter: {
        ...emptyActionCenter(),
        ...(overview.actionCenter || {}),
        items: overview.actionCenter?.items || []
      }
    };
    await new Promise((resolve) => setTimeout(resolve, 100));
    renderScoreChart();
  } catch {
    ElMessage.error('概况数据加载失败');
  }
});

function renderScoreChart() {
  if (!scoreDistChart.value || !data.value.scoreDistribution.length) return;
  const chart = echarts.init(scoreDistChart.value);
  register(chart, scoreDistChart.value);
  chart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['44%', '72%'],
      data: data.value.scoreDistribution.map((item) => ({ name: item.name, value: item.value })),
      label: { formatter: '{b}\n{c}人', fontSize: 12, color: '#475569' }
    }],
    color: ['#16a34a', '#2563eb', '#d97706', '#94a3b8', '#dc2626']
  });
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

function openRecentExam(exam: RecentExam) {
  const examId = Number(exam.id || 0);
  if (Number.isFinite(examId) && examId > 0) {
    emit('navigate', `/exam-tasks?examId=${examId}`);
    return;
  }
  emit('navigate', '/exam-tasks');
}

function actionIcon(type?: string) {
  const normalized = String(type || '').toUpperCase();
  if (normalized === 'LIFECYCLE_HEALTH') return Clock;
  if (normalized === 'REVIEW') return DocumentChecked;
  if (normalized === 'APPEAL' || normalized === 'RECHECK') return Tickets;
  if (normalized === 'SCORE_BLOCKED') return Clock;
  if (normalized === 'SCORE_READY') return Calendar;
  return DocumentChecked;
}

function actionSeverityType(severity?: string) {
  const normalized = String(severity || '').toUpperCase();
  if (normalized === 'HIGH') return 'danger';
  if (normalized === 'WARN') return 'warning';
  return 'info';
}

function actionItemKey(item: TeacherActionItem) {
  return `${item.type}-${item.examId || 'none'}-${item.appealId || item.title}`;
}

function openActionItem(item: TeacherActionItem) {
  emit('navigate', item.target || '/reviews');
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

.chart-box {
  width: 100%;
  height: 300px;
}

.mp-stat-card.clickable {
  cursor: pointer;
}

.mp-stat-card.clickable:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.teacher-action-center {
  margin-bottom: 16px;
}

.teacher-action-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.teacher-action-title > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.teacher-action-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.teacher-action-summary-item {
  min-height: 58px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  text-align: left;
  cursor: pointer;
}

.teacher-action-summary-item:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.teacher-action-summary-item span {
  color: #64748b;
  font-size: 12px;
}

.teacher-action-summary-item strong {
  color: #111827;
  font-size: 18px;
}

.teacher-action-list {
  display: grid;
  gap: 10px;
}

.teacher-action-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.teacher-action-row:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.teacher-action-icon {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
}

.teacher-action-icon.severity-high {
  background: #fef2f2;
  color: #dc2626;
}

.teacher-action-icon.severity-warn {
  background: #fff7ed;
  color: #ea580c;
}

.teacher-action-main {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.teacher-action-main strong,
.teacher-action-main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.teacher-action-main strong {
  color: #111827;
}

.teacher-action-main small {
  color: #64748b;
}

.teacher-action-side {
  display: grid;
  justify-items: end;
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

  .teacher-action-row {
    grid-template-columns: 38px minmax(0, 1fr);
  }

  .teacher-action-side {
    grid-column: 2;
    justify-items: start;
  }
}
</style>
