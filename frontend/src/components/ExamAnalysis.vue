<template>
  <section v-loading="loading" class="exam-analysis">
    <div class="mp-stat-grid">
      <div v-for="card in statCards" :key="card.label" class="mp-stat-card">
        <div class="mp-stat-header">{{ card.label }}</div>
        <div class="mp-stat-row">
          <div class="mp-stat-content">
            <div class="mp-stat-value" :class="accentClass(card.accent)">{{ card.value }}</div>
          </div>
        </div>
      </div>
    </div>

    <div :class="['chart-row', { 'chart-row--single': isTeacher }]">
      <el-card shadow="never" class="chart-card">
        <template #header>分数段分布（已完成考试）</template>
        <div v-show="hasScoreData" ref="scoreChartRef" class="chart"></div>
        <el-empty v-if="!loading && !hasScoreData" description="暂无已完成的考试成绩" :image-size="80" />
      </el-card>
      <el-card v-if="!isTeacher" shadow="never" class="chart-card">
        <template #header>角色用户分布</template>
        <div ref="roleChartRef" class="chart"></div>
      </el-card>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>{{ isTeacher ? '我的各科考试与平均分（按平均分排名）' : '各科目考试与平均分（按平均分排名）' }}</span>
          <el-button size="small" :icon="Download" :disabled="rankedSubjects.length === 0" @click="exportSubjects">导出</el-button>
        </div>
      </template>
      <el-table :data="rankedSubjects" border>
        <el-table-column label="排名" width="80">
          <template #default="scope">
            <el-tag v-if="scope.row.rank <= 3" :type="rankTagType(scope.row.rank)" effect="dark" round>{{ scope.row.rank }}</el-tag>
            <span v-else>{{ scope.row.rank }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="subjectName" label="科目" min-width="160" />
        <el-table-column prop="examCount" label="考试数" width="110" />
        <el-table-column prop="attemptCount" label="完成人次" width="110" />
        <el-table-column label="平均分" width="160">
          <template #default="scope">
            <div class="score-cell">
              <span class="score-num">{{ scope.row.avgScore ?? 0 }}</span>
              <el-progress
                :percentage="Math.min(100, Number(scope.row.avgScore || 0))"
                :stroke-width="8"
                :show-text="false"
                :color="scoreColor(Number(scope.row.avgScore || 0))"
              />
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && rankedSubjects.length === 0" description="暂无科目统计" :image-size="80" />
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Download } from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import { fetchAnalysisOverview, fetchTeacherAnalysis, type AnalysisOverview } from '../api/admin';
import { useChartAutoResize } from '../composables/useChartAutoResize';
import { exportToCsv } from '../utils/exportCsv';

const props = defineProps<{ scope?: 'global' | 'teacher' }>();
const isTeacher = computed(() => props.scope === 'teacher');

const overview = ref<AnalysisOverview | null>(null);
const loading = ref(false);
const scoreChartRef = ref<HTMLElement | null>(null);
const roleChartRef = ref<HTMLElement | null>(null);
const { register } = useChartAutoResize();

const hasScoreData = computed(() => {
  if (!overview.value) return false;
  return Object.values(overview.value.scoreDistribution || {}).some((value) => Number(value) > 0);
});

// 及格率：从分数段分布纯前端计算（60 分及以上为及格）
const passRate = computed(() => {
  const dist = overview.value?.scoreDistribution;
  if (!dist) return 0;
  const below = Number(dist.belowSixty || 0);
  const total = below
    + Number(dist.sixtyToSeventy || 0)
    + Number(dist.seventyToEighty || 0)
    + Number(dist.eightyToNinety || 0)
    + Number(dist.ninetyToHundred || 0);
  if (total === 0) return 0;
  return Math.round(((total - below) / total) * 1000) / 10;
});

const statCards = computed(() => {
  const o = overview.value;
  const cards: Array<{ label: string; value: number | string; accent?: string }> = [];
  if (!isTeacher.value) cards.push({ label: '用户数', value: o?.userCount ?? 0 });
  cards.push({ label: isTeacher.value ? '我的题目' : '题目数', value: o?.questionCount ?? 0 });
  cards.push({ label: isTeacher.value ? '我的试卷' : '试卷数', value: o?.paperCount ?? 0 });
  cards.push({ label: isTeacher.value ? '我的考试' : '考试数', value: o?.examCount ?? 0 });
  cards.push({ label: '参考人次', value: o?.attemptCount ?? 0 });
  cards.push({ label: '已完成', value: o?.completedCount ?? 0 });
  cards.push({ label: '平均分', value: o?.averageScore ?? 0, accent: 'primary' });
  cards.push({ label: '及格率', value: `${passRate.value}%`, accent: passRate.value >= 60 ? 'ok' : 'warn' });
  return cards;
});

// 科目统计按平均分降序排名
const rankedSubjects = computed(() => {
  const list = [...(overview.value?.subjectStats || [])];
  list.sort((a, b) => Number(b.avgScore || 0) - Number(a.avgScore || 0));
  return list.map((item, index) => ({ ...item, rank: index + 1 }));
});

function accentClass(accent?: string) {
  if (accent === 'primary') return 'text-primary';
  if (accent === 'ok') return 'mp-val-ok';
  if (accent === 'warn') return 'mp-val-warn';
  return '';
}

function rankTagType(rank: number) {
  if (rank === 1) return 'warning';
  if (rank === 2) return 'info';
  return 'success';
}

function scoreColor(score: number) {
  if (score >= 80) return '#16a34a';
  if (score >= 60) return '#d97706';
  return '#dc2626';
}

function exportSubjects() {
  const rows = rankedSubjects.value.map((s) => ({
    rank: s.rank,
    subjectName: s.subjectName,
    examCount: s.examCount,
    attemptCount: s.attemptCount,
    avgScore: s.avgScore ?? 0
  }));
  exportToCsv(`科目成绩分析_${new Date().toISOString().slice(0, 10)}`, [
    { key: 'rank', label: '排名' },
    { key: 'subjectName', label: '科目' },
    { key: 'examCount', label: '考试数' },
    { key: 'attemptCount', label: '完成人次' },
    { key: 'avgScore', label: '平均分' }
  ], rows);
  ElMessage.success(`已导出 ${rows.length} 个科目`);
}

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const response = isTeacher.value ? await fetchTeacherAnalysis() : await fetchAnalysisOverview();
    overview.value = response.data;
    await nextTick();
    renderScoreChart();
    if (!isTeacher.value) {
      renderRoleChart();
    }
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
  register(chart, scoreChartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 20, bottom: 30 },
    xAxis: { type: 'category', data: ['<60', '60-69', '70-79', '80-89', '90-100'] },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar',
      barWidth: '50%',
      itemStyle: { color: '#409eff', borderRadius: [6, 6, 0, 0] },
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
  register(chart, roleChartRef.value);
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
.text-primary {
  color: #409eff;
}
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.chart-row--single {
  grid-template-columns: 1fr;
}
.chart {
  width: 100%;
  height: 300px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.score-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.score-num {
  min-width: 42px;
  font-weight: 600;
  color: #303133;
}
.score-cell .el-progress {
  flex: 1;
}
@media (max-width: 900px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
