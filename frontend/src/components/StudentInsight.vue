<template>
  <section class="mp-page">
    <div class="mp-toolbar">
      <el-select v-model="classId" placeholder="请选择班级" filterable style="width: 280px" @change="loadStudents">
        <el-option v-for="cls in classes" :key="cls.id" :label="cls.className" :value="cls.id" />
      </el-select>
      <el-button v-if="classId" type="success" plain :icon="Download" :disabled="students.length === 0" @click="exportRoster">导出名单</el-button>
      <span class="mp-hint">选择班级查看学生名单，点击「详情」查看该生历次成绩与趋势图</span>
    </div>

    <div v-if="classId" class="mp-table-card">
      <el-table v-loading="loadingStudents" :data="students">
        <el-table-column label="学号" width="150">
          <template #default="s">{{ s.row.studentNo || '—' }}</template>
        </el-table-column>
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column prop="username" label="账号" min-width="120" />
        <el-table-column label="已完成考试" width="120">
          <template #default="s">{{ s.row.completedCount }}</template>
        </el-table-column>
        <el-table-column label="平均分" width="100">
          <template #default="s">{{ s.row.avgScore }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="s">
            <el-button link type="primary" @click="openInsight(s.row as ClassStudent)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loadingStudents && students.length === 0" description="该班级暂无学生" :image-size="80" />
    </div>
    <el-empty v-if="!classId" description="请先选择班级查看学生名单" :image-size="100" />

    <el-drawer v-model="drawerVisible" :title="`${current?.student.realName || ''} · 学情档案`" size="60%">
      <div v-if="current" v-loading="loadingInsight">
        <div class="drawer-toolbar">
          <el-button type="success" plain size="small" :icon="Download" :disabled="current.exams.length === 0" @click="exportScores">
            导出该生成绩
          </el-button>
        </div>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="姓名">{{ current.student.realName }}</el-descriptions-item>
          <el-descriptions-item label="学号">{{ current.student.studentNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ current.student.className || '—' }}</el-descriptions-item>
          <el-descriptions-item label="账号">{{ current.student.username }}</el-descriptions-item>
        </el-descriptions>

        <div class="mp-stat-grid" style="margin: 16px 0;">
          <div class="mp-stat-card">
            <div class="mp-stat-header">已完成</div>
            <div class="mp-stat-row">
              <div class="mp-stat-content">
                <div class="mp-stat-value">{{ current.summary.count }}</div>
              </div>
            </div>
          </div>
          <div class="mp-stat-card">
            <div class="mp-stat-header">平均分</div>
            <div class="mp-stat-row">
              <div class="mp-stat-content">
                <div class="mp-stat-value text-primary">{{ current.summary.avgScore }}</div>
              </div>
            </div>
          </div>
          <div class="mp-stat-card">
            <div class="mp-stat-header">最高分</div>
            <div class="mp-stat-row">
              <div class="mp-stat-content">
                <div class="mp-stat-value mp-val-ok">{{ current.summary.maxScore }}</div>
              </div>
            </div>
          </div>
          <div class="mp-stat-card">
            <div class="mp-stat-header">最低分</div>
            <div class="mp-stat-row">
              <div class="mp-stat-content">
                <div class="mp-stat-value">{{ current.summary.minScore }}</div>
              </div>
            </div>
          </div>
        </div>

        <el-card shadow="never" class="trend-card">
          <template #header>历次成绩趋势（{{ current.exams.length }} 场考试）</template>
          <div v-show="current.exams.length > 0" ref="chartRef" class="chart"></div>
          <el-empty v-if="current.exams.length === 0" description="该生暂无已完成考试" :image-size="70" />
        </el-card>

        <el-table :data="current.exams" border style="margin-top: 16px;">
          <el-table-column prop="examName" label="考试" min-width="160" />
          <el-table-column prop="subjectName" label="科目" width="130" />
          <el-table-column label="得分" width="100">
            <template #default="s">{{ s.row.score }}</template>
          </el-table-column>
          <el-table-column label="满分" width="100">
            <template #default="s">{{ s.row.totalScore }}</template>
          </el-table-column>
          <el-table-column label="得分率" width="110">
            <template #default="s">
              <el-tag :type="rateType(s.row.score, s.row.totalScore)">
                {{ scoreRate(s.row.score, s.row.totalScore) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="submitTime" label="交卷时间" width="180" />
        </el-table>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Download } from '@element-plus/icons-vue';
import * as echarts from 'echarts';
import { listClasses, type ClassInfo } from '../api/basic';
import {
  getStudentInsight,
  listClassStudents,
  type ClassStudent,
  type StudentInsightData
} from '../api/insight';
import { useChartAutoResize } from '../composables/useChartAutoResize';
import { exportToCsv } from '../utils/exportCsv';

const classes = ref<ClassInfo[]>([]);
const classId = ref<number | null>(null);
const students = ref<ClassStudent[]>([]);
const loadingStudents = ref(false);
const drawerVisible = ref(false);
const loadingInsight = ref(false);
const current = ref<StudentInsightData | null>(null);
const currentUserId = ref<number | null>(null);
const chartRef = ref<HTMLElement | null>(null);
const { register } = useChartAutoResize();

const selectedClassName = computed(() => classes.value.find((cls) => cls.id === classId.value)?.className);

onMounted(async () => {
  try {
    classes.value = (await listClasses({ status: 1 })).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '班级加载失败');
  }
});

async function loadStudents() {
  if (!classId.value) return;
  loadingStudents.value = true;
  try {
    students.value = (await listClassStudents(classId.value)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学生列表加载失败');
  } finally {
    loadingStudents.value = false;
  }
}

async function openInsight(row: ClassStudent) {
  drawerVisible.value = true;
  loadingInsight.value = true;
  current.value = null;
  currentUserId.value = row.userId;
  try {
    current.value = (await getStudentInsight(row.userId)).data;
    await nextTick();
    renderChart();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '学情加载失败');
  } finally {
    loadingInsight.value = false;
  }
}

function exportRoster() {
  if (!classId.value || students.value.length === 0) return;
  const rows = students.value.map((s) => ({
    studentNo: s.studentNo || '',
    realName: s.realName,
    username: s.username,
    completedCount: s.completedCount,
    avgScore: s.avgScore
  }));
  exportToCsv(`${selectedClassName.value || '班级'}学生名单_${new Date().toISOString().slice(0, 10)}`, [
    { key: 'studentNo', label: '学号' },
    { key: 'realName', label: '姓名' },
    { key: 'username', label: '账号' },
    { key: 'completedCount', label: '已完成考试' },
    { key: 'avgScore', label: '平均分' }
  ], rows);
  ElMessage.success(`已导出 ${rows.length} 名学生`);
}

function exportScores() {
  if (!currentUserId.value || !current.value || current.value.exams.length === 0) return;
  const rows = current.value.exams.map((e) => ({
    examName: e.examName,
    subjectName: e.subjectName,
    score: e.score,
    totalScore: e.totalScore,
    rate: scoreRate(e.score, e.totalScore),
    submitTime: e.submitTime || ''
  }));
  exportToCsv(`${current.value.student.realName}_成绩单_${new Date().toISOString().slice(0, 10)}`, [
    { key: 'examName', label: '考试' },
    { key: 'subjectName', label: '科目' },
    { key: 'score', label: '得分' },
    { key: 'totalScore', label: '满分' },
    { key: 'rate', label: '得分率' },
    { key: 'submitTime', label: '交卷时间' }
  ], rows);
  ElMessage.success(`已导出 ${rows.length} 场考试成绩`);
}

function scoreRate(score: number | null | undefined, total: number | null | undefined): string {
  const s = Number(score ?? 0);
  const t = Number(total ?? 100);
  if (t === 0) return '0%';
  return `${Math.round((s / t) * 1000) / 10}%`;
}

function rateType(score: number | null | undefined, total: number | null | undefined): 'success' | 'warning' | 'danger' | undefined {
  const s = Number(score ?? 0);
  const t = Number(total ?? 100);
  if (t === 0) return undefined;
  const rate = (s / t) * 100;
  if (rate >= 80) return 'success';
  if (rate >= 60) return 'warning';
  return 'danger';
}

function renderChart() {
  if (!chartRef.value || !current.value || current.value.exams.length === 0) return;
  const chart = echarts.init(chartRef.value);
  register(chart, chartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis', backgroundColor: 'rgba(255,255,255,0.95)', borderColor: '#e8e8e8', textStyle: { color: '#333' } },
    grid: { left: 40, right: 20, top: 30, bottom: 70 },
    xAxis: { type: 'category', data: current.value.exams.map((e) => e.examName), axisLabel: { rotate: 30, interval: 0, color: '#666' } },
    yAxis: { type: 'value', name: '分数', axisLabel: { color: '#666' }, splitLine: { lineStyle: { color: '#f0f0f0' } } },
    series: [{
      type: 'line',
      smooth: true,
      areaStyle: { color: 'rgba(64, 158, 255, 0.15)' },
      lineStyle: { width: 3, color: '#409eff' },
      itemStyle: { color: '#409eff', borderWidth: 2, borderColor: '#fff' },
      data: current.value.exams.map((e) => Number(e.score ?? 0))
    }]
  });
}
</script>

<style scoped>
.text-primary {
  color: #409eff;
}
.drawer-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.trend-card {
  margin: 16px 0;
}
.chart {
  width: 100%;
  height: 280px;
}
</style>
