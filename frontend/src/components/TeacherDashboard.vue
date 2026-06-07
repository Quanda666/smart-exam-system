<template>
  <section class="dashboard">
    <div class="stat-cards">
      <div class="stat-card"><span class="stat-label">我的考试</span><strong>{{ data.myExams }}</strong></div>
      <div class="stat-card"><span class="stat-label">待批阅试卷</span><strong class="text-warning">{{ data.pendingReviews }}</strong></div>
      <div class="stat-card"><span class="stat-label">我的试卷</span><strong>{{ data.myPapers }}</strong></div>
    </div>

    <el-row :gutter="16">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>学生成绩分布</span></template>
          <div ref="scoreDistChart" class="chart-box"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header><span>近期考试安排</span></template>
          <el-timeline v-if="data.recentExams?.length">
            <el-timeline-item v-for="exam in data.recentExams" :key="exam.name" :timestamp="exam.time || ''">
              {{ exam.name }}
              <el-tag :type="exam.status === 0 ? 'info' : 'success'" size="small" style="margin-left:8px">
                {{ exam.status === 0 ? '待开始' : '进行中' }}
              </el-tag>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无考试安排" :image-size="80" />
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

interface TeacherOverview {
  myExams: number; pendingReviews: number; myPapers: number;
  scoreDistribution: Array<{ name: string; value: number }>;
  recentExams: Array<{ name: string; time: string; status: number }>;
}

const data = ref<TeacherOverview>({
  myExams: 0, pendingReviews: 0, myPapers: 0, scoreDistribution: [], recentExams: []
});
const scoreDistChart = ref<HTMLElement>();

onMounted(async () => {
  try {
    data.value = (await getJson<TeacherOverview>('/api/overview/teacher')).data;
    await (new Promise(r => setTimeout(r, 100)));
    if (scoreDistChart.value) {
      const chart = echarts.init(scoreDistChart.value);
      chart.setOption({
        tooltip: { trigger: 'item' },
        series: [{
          type: 'pie', radius: '65%',
          data: data.value.scoreDistribution.map(s => ({ name: s.name, value: s.value })),
          label: { formatter: '{b}: {c}人' }
        }],
        color: ['#67c23a', '#409eff', '#e6a23c', '#909399', '#f56c6c']
      });
    }
  } catch (e) {
    ElMessage.error('仪表盘数据加载失败');
  }
});
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 16px; }
.stat-cards { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.stat-card { background: #f5f7fa; border-radius: 12px; padding: 20px; display: flex; flex-direction: column; gap: 8px; }
.stat-card .stat-label { color: #909399; font-size: 13px; }
.stat-card strong { font-size: 28px; color: #303133; }
.text-warning { color: #e6a23c; }
.chart-box { width: 100%; height: 280px; }
</style>
