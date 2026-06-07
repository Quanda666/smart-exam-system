<template>
  <section class="dashboard">
    <div class="stat-cards">
      <div class="stat-card"><span class="stat-label">学生总数</span><strong>{{ data.totalStudents }}</strong></div>
      <div class="stat-card"><span class="stat-label">教师总数</span><strong>{{ data.totalTeachers }}</strong></div>
      <div class="stat-card"><span class="stat-label">今日考试</span><strong>{{ data.todayExams }}</strong></div>
      <div class="stat-card"><span class="stat-label">试卷总量</span><strong>{{ data.totalPapers }}</strong></div>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>教师学科分布</span></template>
          <div ref="teacherSubjectChart" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>学生年级分布</span></template>
          <div ref="studentGradeChart" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="24">
        <el-card shadow="never">
          <template #header><span>近7天考试通过率趋势</span></template>
          <div ref="examTrendChart" class="chart-box"></div>
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

interface OverviewData {
  totalStudents: number;
  totalTeachers: number;
  todayExams: number;
  totalPapers: number;
  teacherSubjects: Array<{ name: string; value: number }>;
  studentGrades: Array<{ name: string; value: number }>;
  examTrend: Array<{ date: string; total: number; passed: number }>;
}

const data = ref<OverviewData>({
  totalStudents: 0, totalTeachers: 0, todayExams: 0, totalPapers: 0,
  teacherSubjects: [], studentGrades: [], examTrend: []
});

const teacherSubjectChart = ref<HTMLElement>();
const studentGradeChart = ref<HTMLElement>();
const examTrendChart = ref<HTMLElement>();

onMounted(async () => {
  try {
    data.value = (await getJson<OverviewData>('/api/overview/admin')).data;
    await (new Promise(r => setTimeout(r, 100)));
    renderCharts();
  } catch (e) {
    ElMessage.error('仪表盘数据加载失败');
  }
});

function renderCharts() {
  if (teacherSubjectChart.value) {
    const chart = echarts.init(teacherSubjectChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.value.teacherSubjects.map(s => s.name), axisLabel: { rotate: 30 } },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: data.value.teacherSubjects.map(s => s.value), itemStyle: { color: '#1677FF', borderRadius: [4,4,0,0] } }],
      grid: { top: 10, right: 20, bottom: 40, left: 40 }
    });
  }

  if (studentGradeChart.value) {
    const chart = echarts.init(studentGradeChart.value);
    chart.setOption({
      tooltip: { trigger: 'item' },
      series: [{
        type: 'pie', radius: ['40%', '70%'],
        data: data.value.studentGrades.map(s => ({ name: s.name || '未分班', value: s.value })),
        label: { formatter: '{b}: {c}人' }
      }]
    });
  }

  if (examTrendChart.value) {
    const chart = echarts.init(examTrendChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['考试总数', '通过数'] },
      xAxis: { type: 'category', data: data.value.examTrend.map(e => e.date) },
      yAxis: { type: 'value' },
      series: [
        { name: '考试总数', type: 'line', data: data.value.examTrend.map(e => e.total), smooth: true },
        { name: '通过数', type: 'line', data: data.value.examTrend.map(e => e.passed), smooth: true, itemStyle: { color: '#67c23a' } }
      ],
      grid: { top: 30, right: 20, bottom: 30, left: 50 }
    });
  }
}
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 16px; }
.stat-cards { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; }
.stat-card { background: #f5f7fa; border-radius: 12px; padding: 20px; display: flex; flex-direction: column; gap: 8px; }
.stat-card .stat-label { color: #909399; font-size: 13px; }
.stat-card strong { font-size: 28px; color: #303133; }
.chart-box { width: 100%; height: 280px; }
</style>
