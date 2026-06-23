<template>
  <section class="dashboard student-dashboard">
    <div class="dashboard-head">
      <div>
        <h2 class="mp-greeting">{{ greeting }}，同学</h2>
        <p>考试待办、成绩变化和薄弱知识点会在这里同步更新。</p>
      </div>
      <el-button type="primary" :icon="AlarmClock" @click="emit('navigate', '/student/exams')">进入考试</el-button>
    </div>

    <div class="mp-quick-actions">
      <div class="mp-quick-action" @click="emit('navigate', '/student/exams')"><el-icon><AlarmClock /></el-icon>进入考试</div>
      <div class="mp-quick-action" @click="emit('navigate', '/student/wrong-questions')"><el-icon><Notebook /></el-icon>错题本</div>
      <div class="mp-quick-action" @click="emit('navigate', '/student/results')"><el-icon><TrendCharts /></el-icon>成绩查询</div>
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

    <div class="mp-card student-action-center">
      <div class="mp-card-title student-action-title">
        <span>
          <el-icon><AlarmClock /></el-icon>
          Action Center
        </span>
        <el-tag :type="data.actionCenter.total > 0 ? 'warning' : 'success'" effect="plain">
          {{ data.actionCenter.total }} open
        </el-tag>
      </div>
      <div class="student-action-summary">
        <button
          v-for="item in actionSummaryRows"
          :key="item.key"
          type="button"
          class="student-action-summary-item"
          @click="emit('navigate', item.target)"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </button>
      </div>
      <div class="student-action-list">
        <button
          v-for="item in data.actionCenter.items"
          :key="actionItemKey(item)"
          type="button"
          class="student-action-row"
          @click="openActionItem(item)"
        >
          <span :class="['student-action-icon', `severity-${String(item.severity || 'INFO').toLowerCase()}`]">
            <el-icon><component :is="actionIcon(item.type)" /></el-icon>
          </span>
          <span class="student-action-main">
            <strong>{{ item.title }}</strong>
            <small>{{ item.subject || '-' }}</small>
            <small>{{ item.detail || '-' }}</small>
          </span>
          <span class="student-action-side">
            <el-tag :type="actionSeverityType(item.severity)" size="small">{{ item.count }}</el-tag>
          </span>
        </button>
        <el-empty v-if="data.actionCenter.items.length === 0" description="No open student actions" :image-size="80" />
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><Calendar /></el-icon>
          最近考试
        </div>
        <div class="exam-list">
          <button v-for="exam in data.recentExams" :key="exam.attemptId || exam.name" type="button" class="exam-row" @click="openRecentExam(exam)">
            <span class="exam-dot" :class="phaseClass(exam.phase)"></span>
            <span class="exam-main">
              <strong>{{ exam.name }}</strong>
              <small>{{ formatDateTime(exam.time) }} 至 {{ formatDateTime(exam.endTime) }}</small>
              <small v-if="(exam.maxAttempts || 1) > 1">第 {{ exam.attemptNo || 1 }} / {{ exam.maxAttempts }} 次</small>
            </span>
            <span class="exam-side">
              <el-tag :type="phaseTagType(exam.phase)" size="small">{{ phaseText(exam.phase) }}</el-tag>
              <small>{{ statusText(exam.status) }}</small>
            </span>
          </button>
          <el-empty v-if="!data.recentExams?.length" description="暂无待办考试" :image-size="80" />
        </div>
      </div>

      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><TrendCharts /></el-icon>
          成绩趋势
        </div>
        <div v-if="data.scoreTrend?.length" ref="scoreTrendChart" class="chart-box"></div>
        <el-empty v-else description="暂无考试记录" :image-size="80" />
      </div>
    </div>

    <div class="mp-card knowledge-card">
      <div class="mp-card-title">
        <el-icon><Aim /></el-icon>
        知识点掌握度
      </div>
      <div v-if="data.knowledgePoints?.length" ref="kpChart" class="chart-box wide"></div>
      <el-empty v-else description="暂无学习数据" :image-size="80" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Aim, AlarmClock, Calendar, CircleCheck, Finished, Notebook, TrendCharts, Warning } from '@element-plus/icons-vue';
import { getJson } from '../api/request';
import { useChartAutoResize } from '../composables/useChartAutoResize';
import { formatDateTime } from '../utils/dateFormat';
import * as echarts from 'echarts';

const emit = defineEmits<{ navigate: [path: string] }>();
const { register } = useChartAutoResize();

interface RecentExam {
  attemptId?: number;
  name: string;
  time: string;
  endTime?: string;
  phase: number;
  status: number;
  attemptNo?: number;
  maxAttempts?: number;
}

interface StudentActionItem {
  type: 'RESUME_EXAM' | 'CONFIRM_RULES' | 'ENTER_EXAM' | 'UPCOMING_EXAM' | 'SCORE_RELEASED' | 'SCORE_PENDING' | 'APPEAL_STATUS' | 'WRONG_BOOK' | string;
  title: string;
  subject?: string;
  detail?: string;
  count: number | string;
  severity?: 'HIGH' | 'WARN' | 'INFO' | string;
  target: string;
  attemptId?: number | null;
  examId?: number | null;
  appealId?: number | null;
}

interface StudentActionCenter {
  generatedAt?: string;
  total: number;
  activeExams: number;
  readyExams: number;
  waitingSoonExams: number;
  releasedScores: number;
  pendingScores: number;
  openAppeals: number;
  wrongQuestions: number;
  items: StudentActionItem[];
}

interface StudentOverview {
  upcomingExams: number;
  activeExams: number;
  finishedExams: number;
  wrongQuestions: number;
  avgScore: number;
  bestScore: number;
  scoreTrend: Array<{ date: string; score: number; examName: string }>;
  knowledgePoints: Array<{ name: string; mastery: number }>;
  recentExams: RecentExam[];
  actionCenter: StudentActionCenter;
}

const emptyActionCenter = (): StudentActionCenter => ({
  total: 0,
  activeExams: 0,
  readyExams: 0,
  waitingSoonExams: 0,
  releasedScores: 0,
  pendingScores: 0,
  openAppeals: 0,
  wrongQuestions: 0,
  items: []
});

const data = ref<StudentOverview>({
  upcomingExams: 0,
  activeExams: 0,
  finishedExams: 0,
  wrongQuestions: 0,
  avgScore: 0,
  bestScore: 0,
  scoreTrend: [],
  knowledgePoints: [],
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
  { group: '考试提醒', label: '待参加考试', value: data.value.upcomingExams, icon: Calendar, iconClass: 'mp-icon-blue', color: '#2563eb' },
  { group: '考试提醒', label: '进行中考试', value: data.value.activeExams, icon: AlarmClock, iconClass: 'mp-icon-orange', color: '#ea580c' },
  { group: '学习进度', label: '已完成考试', value: data.value.finishedExams, icon: Finished, iconClass: 'mp-icon-green', color: '#16a34a' },
  { group: '成绩表现', label: '平均/最高分', value: `${data.value.avgScore}/${data.value.bestScore}`, icon: TrendCharts, iconClass: 'mp-icon-purple', color: '#4f46e5' },
  { group: '错题回顾', label: '错题数', value: data.value.wrongQuestions, icon: Warning, iconClass: 'mp-icon-orange', color: '#dc2626' }
]);

const actionSummaryRows = computed(() => [
  { key: 'active', label: 'Resume', value: data.value.actionCenter.activeExams, target: '/student/exams' },
  { key: 'ready', label: 'Ready', value: data.value.actionCenter.readyExams, target: '/student/exams' },
  { key: 'soon', label: 'Soon', value: data.value.actionCenter.waitingSoonExams, target: '/student/exams' },
  { key: 'released', label: 'Released', value: data.value.actionCenter.releasedScores, target: '/student/results' },
  { key: 'pending', label: 'Pending score', value: data.value.actionCenter.pendingScores, target: '/student/results' },
  { key: 'appeals', label: 'Appeals', value: data.value.actionCenter.openAppeals, target: '/student/results' },
  { key: 'wrong', label: 'Wrong book', value: data.value.actionCenter.wrongQuestions, target: '/student/wrong-questions' }
]);

const scoreTrendChart = ref<HTMLElement>();
const kpChart = ref<HTMLElement>();

onMounted(async () => {
  try {
    const overview = (await getJson<StudentOverview>('/api/overview/student')).data;
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
    renderCharts();
  } catch {
    ElMessage.error('概况数据加载失败');
  }
});

function renderCharts() {
  if (scoreTrendChart.value && data.value.scoreTrend.length) {
    const chart = echarts.init(scoreTrendChart.value);
    register(chart, scoreTrendChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.value.scoreTrend.map((item) => item.date || item.examName), axisLabel: { rotate: 25 } },
      yAxis: { type: 'value', name: '分数' },
      series: [{ type: 'line', data: data.value.scoreTrend.map((item) => item.score), smooth: true, itemStyle: { color: '#4f46e5' }, areaStyle: { color: 'rgba(79, 70, 229, 0.12)' } }],
      grid: { top: 30, right: 20, bottom: 48, left: 48 }
    });
  }

  if (kpChart.value && data.value.knowledgePoints.length) {
    const chart = echarts.init(kpChart.value);
    register(chart, kpChart.value);
    chart.setOption({
      tooltip: {},
      radar: {
        indicator: data.value.knowledgePoints.map((item) => ({ name: item.name, max: 100 })),
        radius: '66%',
        axisName: { color: '#475569', fontSize: 12 }
      },
      series: [{
        type: 'radar',
        data: [{
          value: data.value.knowledgePoints.map((item) => item.mastery),
          name: '掌握度',
          areaStyle: { color: 'rgba(79, 70, 229, 0.2)' },
          lineStyle: { color: '#4f46e5' },
          itemStyle: { color: '#4f46e5' }
        }]
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

function statusText(status?: number) {
  if (status === 1) return '答题中';
  if ((status || 0) >= 2) return '已交卷';
  return '可进入';
}
function openRecentExam(exam: RecentExam) {
  const attemptId = Number(exam.attemptId || 0);
  if (Number.isFinite(attemptId) && attemptId > 0) {
    emit('navigate', `/student/exams?attemptId=${attemptId}`);
    return;
  }
  emit('navigate', '/student/exams');
}

function actionIcon(type?: string) {
  const normalized = String(type || '').toUpperCase();
  if (normalized === 'RESUME_EXAM' || normalized === 'CONFIRM_RULES' || normalized === 'ENTER_EXAM') return AlarmClock;
  if (normalized === 'UPCOMING_EXAM') return Calendar;
  if (normalized === 'SCORE_RELEASED') return CircleCheck;
  if (normalized === 'SCORE_PENDING' || normalized === 'APPEAL_STATUS') return TrendCharts;
  if (normalized === 'WRONG_BOOK') return Notebook;
  return AlarmClock;
}

function actionSeverityType(severity?: string) {
  const normalized = String(severity || '').toUpperCase();
  if (normalized === 'HIGH') return 'danger';
  if (normalized === 'WARN') return 'warning';
  return 'info';
}

function actionItemKey(item: StudentActionItem) {
  return `${item.type}-${item.attemptId || 'none'}-${item.appealId || item.title}`;
}

function openActionItem(item: StudentActionItem) {
  emit('navigate', item.target || '/student/exams');
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
  grid-template-columns: minmax(360px, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
}

.knowledge-card {
  margin-top: 16px;
}

.chart-box {
  width: 100%;
  height: 300px;
}

.chart-box.wide {
  height: 320px;
}

.student-action-center {
  margin-bottom: 16px;
}

.student-action-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.student-action-title > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.student-action-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.student-action-summary-item {
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

.student-action-summary-item:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.student-action-summary-item span {
  color: #64748b;
  font-size: 12px;
}

.student-action-summary-item strong {
  color: #111827;
  font-size: 18px;
}

.student-action-list {
  display: grid;
  gap: 10px;
}

.student-action-row {
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

.student-action-row:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.student-action-icon {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
}

.student-action-icon.severity-high {
  background: #fef2f2;
  color: #dc2626;
}

.student-action-icon.severity-warn {
  background: #fff7ed;
  color: #ea580c;
}

.student-action-main {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.student-action-main strong,
.student-action-main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-action-main strong {
  color: #111827;
}

.student-action-main small {
  color: #64748b;
}

.student-action-side {
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

  .student-action-row {
    grid-template-columns: 38px minmax(0, 1fr);
  }

  .student-action-side {
    grid-column: 2;
    justify-items: start;
  }
}
</style>
