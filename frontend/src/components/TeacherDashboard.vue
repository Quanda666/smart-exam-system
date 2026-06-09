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
      <div class="mp-quick-action" @click="emit('navigate', '/papers')"><el-icon><Document /></el-icon>组卷</div>
      <div class="mp-quick-action" @click="emit('navigate', '/exam-tasks')"><el-icon><Calendar /></el-icon>考试任务</div>
      <div class="mp-quick-action" @click="emit('navigate', '/teacher/analysis')"><el-icon><DataAnalysis /></el-icon>学情分析</div>
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

interface TeacherOverview {
  myExams: number;
  runningExams: number;
  upcomingExams: number;
  finishedExams: number;
  pendingReviews: number;
  myPapers: number;
  publishedPapers: number;
  avgScore: number;
  scoreDistribution: Array<{ name: string; value: number }>;
  recentExams: RecentExam[];
}

const data = ref<TeacherOverview>({
  myExams: 0,
  runningExams: 0,
  upcomingExams: 0,
  finishedExams: 0,
  pendingReviews: 0,
  myPapers: 0,
  publishedPapers: 0,
  avgScore: 0,
  scoreDistribution: [],
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
  { group: '考试任务', label: '进行中', value: data.value.runningExams, icon: Clock, iconClass: 'mp-icon-blue', color: '#2563eb' },
  { group: '考试任务', label: '待开始', value: data.value.upcomingExams, icon: Calendar, iconClass: 'mp-icon-green', color: '#16a34a' },
  { group: '待处理', label: '待批阅试卷', value: data.value.pendingReviews, icon: DocumentChecked, iconClass: 'mp-icon-orange', color: '#ea580c' },
  { group: '试卷管理', label: '已发布试卷', value: `${data.value.publishedPapers}/${data.value.myPapers}`, icon: Files, iconClass: 'mp-icon-purple', color: '#4f46e5' },
  { group: '学情分析', label: '平均分', value: data.value.avgScore, icon: TrendCharts, iconClass: 'mp-icon-blue', color: '#0f172a' }
]);

const scoreDistChart = ref<HTMLElement>();

onMounted(async () => {
  try {
    data.value = (await getJson<TeacherOverview>('/api/overview/teacher')).data;
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
