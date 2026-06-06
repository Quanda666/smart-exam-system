<template>
  <section v-loading="loading" class="exam-analysis">
    <div class="summary-grid">
      <div class="summary-card"><span>用户数</span><strong>{{ overview?.userCount ?? 0 }}</strong></div>
      <div class="summary-card"><span>题目数</span><strong>{{ overview?.questionCount ?? 0 }}</strong></div>
      <div class="summary-card"><span>试卷数</span><strong>{{ overview?.paperCount ?? 0 }}</strong></div>
      <div class="summary-card"><span>考试数</span><strong>{{ overview?.examCount ?? 0 }}</strong></div>
      <div class="summary-card"><span>参考人次</span><strong>{{ overview?.attemptCount ?? 0 }}</strong></div>
      <div class="summary-card"><span>已完成</span><strong>{{ overview?.completedCount ?? 0 }}</strong></div>
      <div class="summary-card"><span>平均分</span><strong class="text-primary">{{ overview?.averageScore ?? 0 }}</strong></div>
    </div>

    <div class="chart-row">
      <el-card shadow="never" class="chart-card">
        <template #header>分数段分布（已完成考试）</template>
        <div v-show="hasScoreData" ref="scoreChartRef" class="chart"></div>
        <el-empty v-if="!loading && !hasScoreData" description="暂无已完成的考试成绩" :image-size="80" />
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header>角色用户分布</template>
        <div ref="roleChartRef" class="chart"></div>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>各科目考试与平均分</template>
      <el-table :data="overview?.subjectStats || []" border>
        <el-table-column prop="subjectName" label="科目" min-width="160" />
        <el-table-column prop="examCount" label="考试数" width="120" />
        <el-table-column prop="attemptCount" label="完成人次" width="120" />
        <el-table-column label="平均分" width="120">
          <template #default="scope">{{ scope.row.avgScore ?? 0 }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && (!overview || overview.subjectStats.length === 0)" description="暂无科目统计" :image-size="80" />
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import * as echarts from 'echarts';
import { fetchAnalysisOverview, type AnalysisOverview } from '../api/admin';

const overview = ref<AnalysisOverview | null>(null);
const loading = ref(false);
const scoreChartRef = ref<HTMLElement | null>(null);
const roleChartRef = ref<HTMLElement | null>(null);

const hasScoreData = computed(() => {
  if (!overview.value) return false;
  return Object.values(overview.value.scoreDistribution || {}).some((value) => Number(value) > 0);
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    overview.value = (await fetchAnalysisOverview()).data;
    await nextTick();
    renderScoreChart();
    renderRoleChart();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成绩分析加载失败');
  } finally {
    loading.value = false;
  }
}

function renderScoreChart() {
  if (!scoreChartRef.value || !overview.value || !hasScoreData.value) return;
  const dist = overview.value.scoreDistribution || {};
  const chart = echarts.init(scoreChartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: ['<60', '60-69', '70-79', '80-89', '90-100'] },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      barWidth: '50%',
      itemStyle: { color: '#409eff' },
      data: [
        Number(dist.belowSixty || 0),
        Number(dist.sixtyToSeventy || 0),
        Number(dist.seventyToEighty || 0),
        Number(dist.eightyToNinety || 0),
        Number(dist.ninetyToHundred || 0)
      ]
    }]
  });
}

function renderRoleChart() {
  if (!roleChartRef.value || !overview.value) return;
  const chart = echarts.init(roleChartRef.value);
  chart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['40%', '65%'],
      data: (overview.value.roleDistribution || []).map((role) => ({ name: role.roleName, value: Number(role.userCount) }))
    }]
  });
}
</script>

<style scoped>
.exam-analysis {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}
.summary-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.summary-card span {
  color: #909399;
  font-size: 13px;
}
.summary-card strong {
  font-size: 22px;
  color: #303133;
}
.text-primary {
  color: #409eff;
}
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.chart {
  width: 100%;
  height: 300px;
}
@media (max-width: 900px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
