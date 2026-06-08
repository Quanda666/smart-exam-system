<template>
  <section class="dashboard">
    <!-- 问候语 -->
    <h2 class="mp-greeting">{{ greeting }}，同学</h2>

    <!-- 快捷操作入口 -->
    <div class="mp-quick-actions">
      <div class="mp-quick-action" @click="emit('navigate', '/student/exams')"><el-icon><AlarmClock /></el-icon>进入考试</div>
      <div class="mp-quick-action" @click="emit('navigate', '/student/wrong-questions')"><el-icon><Notebook /></el-icon>错题本</div>
      <div class="mp-quick-action" @click="emit('navigate', '/student/results')"><el-icon><TrendCharts /></el-icon>成绩查询</div>
    </div>

    <!-- 统计卡片网格 -->
    <div class="mp-stat-grid">
      <!-- 待参加考试 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><Calendar /></el-icon>
          考试提醒
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-blue">
            <el-icon><AlarmClock /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">待参加考试</div>
            <div class="mp-stat-value" style="color: #2563eb;">{{ data.upcomingExams }}</div>
          </div>
        </div>
      </div>

      <!-- 已完成考试 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><CircleCheck /></el-icon>
          学习进度
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-green">
            <el-icon><Finished /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">已完成考试</div>
            <div class="mp-stat-value" style="color: #16a34a;">{{ data.finishedExams }}</div>
          </div>
        </div>
      </div>

      <!-- 错题数 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><Warning /></el-icon>
          错题回顾
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-orange">
            <el-icon><Notebook /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">错题数</div>
            <div class="mp-stat-value" style="color: #ea580c;">{{ data.wrongQuestions }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="mp-card">
          <div class="mp-card-title">
            <el-icon><TrendCharts /></el-icon>
            成绩趋势
          </div>
          <div v-if="data.scoreTrend?.length" ref="scoreTrendChart" class="chart-box"></div>
          <el-empty v-else description="暂无考试记录" :image-size="80" />
        </div>
      </el-col>
      <el-col :span="10">
        <div class="mp-card">
          <div class="mp-card-title">
            <el-icon><Aim /></el-icon>
            知识点掌握度
          </div>
          <div v-if="data.knowledgePoints?.length" ref="kpChart" class="chart-box"></div>
          <el-empty v-else description="暂无学习数据" :image-size="80" />
        </div>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { Calendar, AlarmClock, CircleCheck, Finished, Warning, Notebook, TrendCharts, Aim } from '@element-plus/icons-vue';
import { getJson } from '../api/request';
import { useChartAutoResize } from '../composables/useChartAutoResize';
import * as echarts from 'echarts';

const emit = defineEmits<{ navigate: [path: string] }>();
const { register } = useChartAutoResize();

interface StudentOverview {
  upcomingExams: number; finishedExams: number; wrongQuestions: number;
  scoreTrend: Array<{ date: string; score: number; examName: string }>;
  knowledgePoints: Array<{ name: string; mastery: number }>;
}

const data = ref<StudentOverview>({
  upcomingExams: 0, finishedExams: 0, wrongQuestions: 0, scoreTrend: [], knowledgePoints: []
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '🌙 夜深了';
  if (hour < 11) return '☀️ 早上好';
  if (hour < 13) return '👋 中午好';
  if (hour < 18) return '🌤️ 下午好';
  return '🌙 晚上好';
});

const scoreTrendChart = ref<HTMLElement>();
const kpChart = ref<HTMLElement>();

onMounted(async () => {
  try {
    data.value = (await getJson<StudentOverview>('/api/overview/student')).data;
    await (new Promise(r => setTimeout(r, 100)));
    renderCharts();
  } catch (e) {
    ElMessage.error('仪表盘数据加载失败');
  }
});

function renderCharts() {
  if (scoreTrendChart.value && data.value.scoreTrend.length) {
    const chart = echarts.init(scoreTrendChart.value);
    register(chart, scoreTrendChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8', textStyle: { color: '#333' } },
      xAxis: { type: 'category', data: data.value.scoreTrend.map(e => e.date || e.examName), axisLabel: { rotate: 30, color: '#666' }, axisLine: { lineStyle: { color: '#e8e8e8' } } },
      yAxis: { type: 'value', name: '分数', axisLabel: { color: '#666' }, splitLine: { lineStyle: { color: '#f0f0f0' } } },
      series: [{ type: 'line', data: data.value.scoreTrend.map(e => e.score), smooth: true, lineStyle: { width: 3, color: '#4f46e5' }, itemStyle: { color: '#4f46e5' }, areaStyle: { color: 'rgba(79, 70, 229, 0.1)' } }],
      grid: { top: 30, right: 20, bottom: 40, left: 50 }
    });
  }
  if (kpChart.value && data.value.knowledgePoints.length) {
    const chart = echarts.init(kpChart.value);
    register(chart, kpChart.value);
    // 知识点雷达图
    const indicators = data.value.knowledgePoints.map(k => ({ name: k.name, max: 100 }));
    const values = data.value.knowledgePoints.map(k => k.mastery);
    chart.setOption({
      tooltip: { backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8', textStyle: { color: '#333' } },
      radar: {
        indicator: indicators,
        radius: '65%',
        axisName: { color: '#666', fontSize: 11 },
        splitLine: { lineStyle: { color: '#f0f0f0' } },
        splitArea: { areaStyle: { color: ['rgba(79,70,229,0.02)', 'rgba(79,70,229,0.05)'] } }
      },
      series: [{
        type: 'radar',
        data: [{ value: values, name: '掌握度', areaStyle: { color: 'rgba(79, 70, 229, 0.2)' }, lineStyle: { color: '#4f46e5' }, itemStyle: { color: '#4f46e5' } }]
      }]
    });
  }
}
</script>

<style scoped>
.dashboard { }
.chart-box { width: 100%; height: 280px; }
</style>
