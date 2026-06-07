<template>
  <section class="insight">
    <div class="toolbar-line">
      <el-select v-model="classId" placeholder="请选择班级" filterable style="width: 280px" @change="loadStudents">
        <el-option v-for="cls in classes" :key="cls.id" :label="cls.className" :value="cls.id" />
      </el-select>
      <el-button v-if="classId" type="success" plain :disabled="students.length === 0" @click="exportRoster">导出名单</el-button>
      <span class="hint">选择班级查看学生名单，点击「成绩详情」查看其历次成绩与趋势</span>
    </div>

    <el-table v-if="classId" v-loading="loadingStudents" :data="students" border>
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
          <el-button link type="primary" @click="openInsight(s.row as ClassStudent)">成绩详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="classId && !loadingStudents && students.length === 0" description="该班级暂无学生" />
    <el-empty v-if="!classId" description="请先选择班级" />

    <el-drawer v-model="drawerVisible" :title="`${current?.student.realName || ''} · 学情`" size="60%">
      <div v-if="current" v-loading="loadingInsight">
        <div class="drawer-toolbar">
          <el-button type="success" plain size="small" :disabled="current.exams.length === 0" @click="exportScores">
            导出成绩
          </el-button>
        </div>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="姓名">{{ current.student.realName }}</el-descriptions-item>
          <el-descriptions-item label="学号">{{ current.student.studentNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ current.student.className || '—' }}</el-descriptions-item>
          <el-descriptions-item label="账号">{{ current.student.username }}</el-descriptions-item>
        </el-descriptions>

        <div class="summary-grid">
          <div class="summary-card"><span>已完成</span><strong>{{ current.summary.count }}</strong></div>
          <div class="summary-card"><span>平均分</span><strong class="text-primary">{{ current.summary.avgScore }}</strong></div>
          <div class="summary-card"><span>最高分</span><strong>{{ current.summary.maxScore }}</strong></div>
          <div class="summary-card"><span>最低分</span><strong>{{ current.summary.minScore }}</strong></div>
        </div>

        <el-card shadow="never" class="trend-card">
          <template #header>成绩趋势</template>
          <div v-show="current.exams.length > 0" ref="chartRef" class="chart"></div>
          <el-empty v-if="current.exams.length === 0" description="暂无已完成考试" :image-size="70" />
        </el-card>

        <el-table :data="current.exams" border>
          <el-table-column prop="examName" label="考试" min-width="160" />
          <el-table-column prop="subjectName" label="科目" width="130" />
          <el-table-column label="得分 / 满分" width="130">
            <template #default="s">{{ s.row.score }} / {{ s.row.totalScore }}</template>
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
import * as echarts from 'echarts';
import { listClasses, type ClassInfo } from '../api/basic';
import {
  exportClassStudents,
  exportStudentScores,
  getStudentInsight,
  listClassStudents,
  type ClassStudent,
  type StudentInsightData
} from '../api/insight';

const classes = ref<ClassInfo[]>([]);
const classId = ref<number | null>(null);
const students = ref<ClassStudent[]>([]);
const loadingStudents = ref(false);
const drawerVisible = ref(false);
const loadingInsight = ref(false);
const current = ref<StudentInsightData | null>(null);
const currentUserId = ref<number | null>(null);
const chartRef = ref<HTMLElement | null>(null);

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

async function exportRoster() {
  if (!classId.value) return;
  try {
    await exportClassStudents(classId.value, selectedClassName.value);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  }
}

async function exportScores() {
  if (!currentUserId.value || !current.value) return;
  try {
    await exportStudentScores(currentUserId.value, current.value.student.realName);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  }
}

function renderChart() {
  if (!chartRef.value || !current.value || current.value.exams.length === 0) return;
  const chart = echarts.init(chartRef.value);
  chart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { left: 40, right: 20, top: 30, bottom: 70 },
    xAxis: { type: 'category', data: current.value.exams.map((e) => e.examName), axisLabel: { rotate: 30, interval: 0 } },
    yAxis: { type: 'value' },
    series: [{
      type: 'line',
      smooth: true,
      areaStyle: {},
      itemStyle: { color: '#409eff' },
      data: current.value.exams.map((e) => Number(e.score ?? 0))
    }]
  });
}
</script>

<style scoped>
.insight {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar-line {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}
.hint {
  color: #909399;
  font-size: 13px;
}
.drawer-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin: 16px 0;
}
.summary-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.summary-card span {
  color: #909399;
  font-size: 13px;
}
.summary-card strong {
  font-size: 20px;
  color: #303133;
}
.text-primary {
  color: #409eff;
}
.trend-card {
  margin: 16px 0;
}
.chart {
  width: 100%;
  height: 280px;
}
</style>
