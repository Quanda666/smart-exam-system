<template>
  <section class="dashboard">
    <div class="stat-cards">
      <div class="stat-card"><span class="stat-label">待参加考试</span><strong class="text-primary">{{ data.upcomingExams }}</strong></div>
      <div class="stat-card"><span class="stat-label">已完成考试</span><strong class="text-ok">{{ data.finishedExams }}</strong></div>
      <div class="stat-card"><span class="stat-label">错题数</span><strong class="text-warning">{{ data.wrongQuestions }}</strong></div>
    </div>

    <el-row :gutter="16">
      <el-col :span="14">
        <el-card shadow="never">
          <template #header><span>成绩趋势</span></template>
          <div v-if="data.scoreTrend?.length" ref="scoreTrendChart" class="chart-box"></div>
          <el-empty v-else description="暂无考试记录" :image-size="80" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never">
          <template #header><span>知识点掌握度</span></template>
          <div v-if="data.knowledgePoints?.length" ref="kpChart" class="chart-box"></div>
          <el-empty v-else description="暂无学习数据" :image-size="80" />
        </el-card>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { getJson } from '../api/request';
import * as echarts from 'echarts';

interface StudentOverview {
  upcomingExams: number; finishedExams: number; wrongQuestions: number;
  scoreTrend: Array<{ date: string; score: number; examName: string }>;
  knowledgePoints: Array<{ name: string; mastery: number }>;
}

const data = ref<StudentOverview>({
  upcomingExams: 0, finishedExams: 0, wrongQuestions: 0, scoreTrend: [], knowledgePoints: []
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
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.value.scoreTrend.map(e => e.date || e.examName), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value', name: '分数' },
      series: [{ type: 'line', data: data.value.scoreTrend.map(e => e.score), smooth: true, itemStyle: { color: '#1677FF' }, areaStyle: { color: 'rgba(22,119,255,0.1)' } }],
      grid: { top: 10, right: 20, bottom: 40, left: 50 }
    });
  }
  if (kpChart.value && data.value.knowledgePoints.length) {
    const chart = echarts.init(kpChart.value);
    chart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: '65%',
        data: data.value.knowledgePoints.map(k => ({ name: k.name, value: k.mastery })),
        label: { formatter: '{b}' }
      }]
    });
  }
}
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 16px; }
.stat-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.stat-card { background: #f5f7fa; border-radius: 12px; padding: 20px; display: flex; flex-direction: column; gap: 8px; }
.stat-card .stat-label { color: #909399; font-size: 13px; }
.stat-card strong { font-size: 28px; color: #303133; }
.text-primary { color: #1677FF; }
.text-ok { color: #67c23a; }
.text-warning { color: #e6a23c; }
.chart-box { width: 100%; height: 280px; }
</style>
