<template>
  <section class="dashboard">
    <!-- 问候语 -->
    <h2 class="mp-greeting">{{ greeting }}，老师</h2>

    <!-- 快捷操作入口 -->
    <div class="mp-quick-actions">
      <div class="mp-quick-action" @click="emit('navigate', '/reviews')"><el-icon><DocumentChecked /></el-icon>批阅试卷</div>
      <div class="mp-quick-action" @click="emit('navigate', '/papers')"><el-icon><Document /></el-icon>组卷</div>
      <div class="mp-quick-action" @click="emit('navigate', '/exam-tasks')"><el-icon><Calendar /></el-icon>考试任务</div>
      <div class="mp-quick-action" @click="emit('navigate', '/teacher/analysis')"><el-icon><DataAnalysis /></el-icon>学情分析</div>
    </div>

    <!-- 统计卡片网格 -->
    <div class="mp-stat-grid">
      <!-- 我的考试 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><EditPen /></el-icon>
          考试任务
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-blue">
            <el-icon><Tickets /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">我的考试</div>
            <div class="mp-stat-value">{{ data.myExams }}</div>
          </div>
        </div>
      </div>

      <!-- 待批阅试卷 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><Clock /></el-icon>
          待处理
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-orange">
            <el-icon><DocumentChecked /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">待批阅试卷</div>
            <div class="mp-stat-value" style="color: #ea580c;">{{ data.pendingReviews }}</div>
          </div>
        </div>
      </div>

      <!-- 我的试卷 -->
      <div class="mp-stat-card">
        <div class="mp-stat-header">
          <el-icon><Document /></el-icon>
          试卷管理
        </div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-purple">
            <el-icon><Files /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">我的试卷</div>
            <div class="mp-stat-value">{{ data.myPapers }}</div>
          </div>
        </div>
      </div>
    </div>

    <!-- 图表区域 -->
    <el-row :gutter="16">
      <el-col :span="12">
        <div class="mp-card">
          <div class="mp-card-title">
            <el-icon><PieChart /></el-icon>
            学生成绩分布
          </div>
          <div ref="scoreDistChart" class="chart-box"></div>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="mp-card">
          <div class="mp-card-title">
            <el-icon><Calendar /></el-icon>
            近期考试安排
          </div>
          <div style="padding: 12px 0;">
            <el-timeline v-if="data.recentExams?.length">
              <el-timeline-item v-for="exam in data.recentExams" :key="exam.name" :timestamp="exam.time || ''" color="#4f46e5">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <span style="font-weight: 500; color: #1a1a1a;">{{ exam.name }}</span>
                  <el-tag :type="exam.status === 0 ? 'info' : 'success'" size="small">
                    {{ exam.status === 0 ? '待开始' : '进行中' }}
                  </el-tag>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无考试安排" :image-size="80" />
          </div>
        </div>
      </el-col>
    </el-row>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue';
import { ElMessage } from 'element-plus';
import { EditPen, Clock, DocumentChecked, Document, Files, PieChart, Calendar, Tickets, DataAnalysis } from '@element-plus/icons-vue';
import { getJson } from '../api/request';
import { useChartAutoResize } from '../composables/useChartAutoResize';
import * as echarts from 'echarts';

const emit = defineEmits<{ navigate: [path: string] }>();
const { register } = useChartAutoResize();

interface TeacherOverview {
  myExams: number; pendingReviews: number; myPapers: number;
  scoreDistribution: Array<{ name: string; value: number }>;
  recentExams: Array<{ name: string; time: string; status: number }>;
}

const data = ref<TeacherOverview>({
  myExams: 0, pendingReviews: 0, myPapers: 0, scoreDistribution: [], recentExams: []
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '🌙 夜深了';
  if (hour < 11) return '☀️ 早上好';
  if (hour < 13) return '👋 中午好';
  if (hour < 18) return '🌤️ 下午好';
  return '🌙 晚上好';
});

const scoreDistChart = ref<HTMLElement>();

onMounted(async () => {
  try {
    data.value = (await getJson<TeacherOverview>('/api/overview/teacher')).data;
    await (new Promise(r => setTimeout(r, 100)));
    if (scoreDistChart.value) {
      const chart = echarts.init(scoreDistChart.value);
      register(chart, scoreDistChart.value);
      chart.setOption({
        tooltip: { trigger: 'item', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8', textStyle: { color: '#333' } },
        series: [{
          type: 'pie', radius: ['40%', '70%'],
          data: data.value.scoreDistribution.map(s => ({ name: s.name, value: s.value })),
          label: { formatter: '{b}\n{c}人', fontSize: 12, color: '#666' },
          emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.2)' } }
        }],
        color: ['#16a34a', '#4f46e5', '#d97706', '#999999', '#dc2626']
      });
    }
  } catch (e) {
    ElMessage.error('仪表盘数据加载失败');
  }
});
</script>

<style scoped>
.dashboard { }
.chart-box { width: 100%; height: 280px; }
</style>
