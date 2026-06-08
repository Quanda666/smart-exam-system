<template>
  <section class="dashboard">
    <!-- 问候语 -->
    <h2 class="mp-greeting">{{ greeting }}，管理员</h2>

    <!-- 快捷操作入口 -->
    <div class="mp-quick-actions">
      <div class="mp-quick-action" @click="emit('navigate', '/system/users')"><el-icon><Plus /></el-icon>新建用户</div>
      <div class="mp-quick-action" @click="emit('navigate', '/basic/notices')"><el-icon><Bell /></el-icon>发布公告</div>
      <div class="mp-quick-action" @click="emit('navigate', '/question-bank')"><el-icon><Collection /></el-icon>题库管理</div>
      <div class="mp-quick-action" @click="emit('navigate', '/exam/analysis')"><el-icon><DataAnalysis /></el-icon>成绩分析</div>
    </div>

    <!-- 统计卡片网格 -->
    <div class="mp-stat-grid">
      <!-- 学生数据 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><UserFilled /></el-icon>
          学生数据
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-blue">
            <el-icon><User /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">学生总数</div>
            <div class="mp-stat-value">{{ data.totalStudents }}</div>
          </div>
        </div>
      </div>

      <!-- 教师数据 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><Avatar /></el-icon>
          教师数据
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-green">
            <el-icon><Avatar /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">教师总数</div>
            <div class="mp-stat-value">{{ data.totalTeachers }}</div>
          </div>
        </div>
      </div>

      <!-- 考试数据 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><EditPen /></el-icon>
          考试数据
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-yellow">
            <el-icon><Calendar /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">今日考试</div>
            <div class="mp-stat-value">{{ data.todayExams }}</div>
          </div>
        </div>
      </div>

      <!-- 试卷数据 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><Document /></el-icon>
          试卷数据
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-purple">
            <el-icon><Tickets /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">试卷总量</div>
            <div class="mp-stat-value">{{ data.totalPapers }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="mp-card">
          <div class="mp-card-title">
            <el-icon><Histogram /></el-icon>
            教师学科分布
          </div>
          <div ref="teacherSubjectChart" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="mp-card">
          <div class="mp-card-title">
            <el-icon><PieChart /></el-icon>
            学生年级分布
          </div>
          <div ref="studentGradeChart" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top:16px">
      <el-col :span="24">
        <div class="mp-card">
          <div class="mp-card-title">
            <el-icon><TrendCharts /></el-icon>
            近7天考试通过率趋势
          </div>
          <div ref="examTrendChart" class="chart-box"></div>
        </div>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { UserFilled, User, Avatar, EditPen, Calendar, Document, Tickets, Histogram, PieChart, TrendCharts, Plus, Bell, Collection, DataAnalysis } from '@element-plus/icons-vue';
import { getJson } from '../api/request';
import { useChartAutoResize } from '../composables/useChartAutoResize';
import * as echarts from 'echarts';

const emit = defineEmits<{ navigate: [path: string] }>();
const { register } = useChartAutoResize();

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

// 问候语
const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '🌙 夜深了';
  if (hour < 11) return '☀️ 早上好';
  if (hour < 13) return '👋 中午好';
  if (hour < 18) return '🌤️ 下午好';
  return '🌙 晚上好';
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
    register(chart, teacherSubjectChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8', textStyle: { color: '#333' } },
      xAxis: { type: 'category', data: data.value.teacherSubjects.map(s => s.name), axisLabel: { rotate: 30, color: '#666' }, axisLine: { lineStyle: { color: '#e8e8e8' } } },
      yAxis: { type: 'value', axisLabel: { color: '#666' }, splitLine: { lineStyle: { color: '#f0f0f0' } } },
      series: [{ type: 'bar', data: data.value.teacherSubjects.map(s => s.value), itemStyle: { color: '#4f46e5', borderRadius: [6,6,0,0] }, barWidth: '50%' }],
      grid: { top: 10, right: 20, bottom: 50, left: 40 }
    });
  }

  if (studentGradeChart.value) {
    const chart = echarts.init(studentGradeChart.value);
    register(chart, studentGradeChart.value);
    chart.setOption({
      tooltip: { trigger: 'item', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8', textStyle: { color: '#333' } },
      color: ['#4f46e5', '#16a34a', '#d97706', '#dc2626', '#7c3aed', '#0891b2'],
      series: [{
        type: 'pie', radius: ['45%', '75%'],
        data: data.value.studentGrades.map(s => ({ name: s.name || '未分班', value: s.value })),
        label: { formatter: '{b}\n{c}人', fontSize: 12, color: '#666' },
        emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.2)' } }
      }]
    });
  }

  if (examTrendChart.value) {
    const chart = echarts.init(examTrendChart.value);
    register(chart, examTrendChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8', textStyle: { color: '#333' } },
      legend: { data: ['考试总数', '通过数'], top: 0, textStyle: { color: '#666' } },
      xAxis: { type: 'category', data: data.value.examTrend.map(e => e.date), axisLabel: { color: '#666' }, axisLine: { lineStyle: { color: '#e8e8e8' } } },
      yAxis: { type: 'value', axisLabel: { color: '#666' }, splitLine: { lineStyle: { color: '#f0f0f0' } } },
      series: [
        { name: '考试总数', type: 'line', data: data.value.examTrend.map(e => e.total), smooth: true, lineStyle: { width: 3, color: '#4f46e5' }, itemStyle: { color: '#4f46e5' }, areaStyle: { color: 'rgba(79, 70, 229, 0.1)' } },
        { name: '通过数', type: 'line', data: data.value.examTrend.map(e => e.passed), smooth: true, lineStyle: { width: 3, color: '#16a34a' }, itemStyle: { color: '#16a34a' }, areaStyle: { color: 'rgba(22, 163, 74, 0.1)' } }
      ],
      grid: { top: 40, right: 20, bottom: 30, left: 50 }
    });
  }
}
</script>

<style scoped>
.dashboard { }
.chart-box { width: 100%; height: 280px; }
</style>
