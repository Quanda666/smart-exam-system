<template>
  <div class="exam-list-panel">
    <div class="exam-list-toolbar">
      <el-radio-group v-model="activeList">
        <el-radio-button label="pending">待参加 {{ pendingExams.length }}</el-radio-button>
        <el-radio-button label="active">进行中 {{ activeExams.length }}</el-radio-button>
        <el-radio-button label="finished">已完成 {{ finishedExams.length }}</el-radio-button>
      </el-radio-group>
      <el-button :icon="Refresh" :loading="loading" @click="loadExams(true)">刷新</el-button>
    </div>

    <div class="mp-table-card exam-table-card">
      <el-table v-loading="loading" :data="visibleExams" border>
        <el-table-column label="考试名称" min-width="230">
          <template #default="scope">
            <div class="exam-name-cell">
              <strong>{{ scope.row.examName }}</strong>
              <span>{{ scope.row.paperName || scope.row.description || '—' }}</span>
              <small v-if="(scope.row.maxAttempts || 1) > 1">
                第 {{ scope.row.attemptNo || 1 }} / {{ scope.row.maxAttempts }} 次
              </small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="subjectName" label="所属科目" width="150" />
        <el-table-column label="考试时间" min-width="260">
          <template #default="scope">
            {{ formatDateTime(scope.row.startTime) }} 至 {{ formatDateTime(scope.row.endTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="durationMinutes" label="时长（分钟）" width="120" />
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="tagType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.status < 2"
              type="primary"
              :icon="AlarmClock"
              :disabled="scope.row.status === 0 && !isExamAvailable(scope.row as StudentExamInfo)"
              @click="startExam(scope.row as StudentExamInfo)"
            >
              {{ scope.row.status === 1 ? '继续答题' : '进入考试' }}
            </el-button>
            <el-button v-else link type="primary" :icon="View" @click="viewResult((scope.row as StudentExamInfo).attemptId)">
              查看结果
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && visibleExams.length === 0" :description="emptyText" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { AlarmClock, Refresh, View } from '@element-plus/icons-vue';
import { listStudentExams, type StudentExamInfo } from '../api/exam';
import { formatDateTime } from '../utils/dateFormat';

const activeList = ref<'pending' | 'active' | 'finished'>('pending');
const loading = ref(false);
const exams = ref<StudentExamInfo[]>([]);

const emits = defineEmits<{
  (e: 'view-result', attemptId: number): void;
  (e: 'start-exam', exam: StudentExamInfo): void;
}>();

const pendingExams = computed(() => exams.value.filter((exam) => exam.status === 0));
const activeExams = computed(() => exams.value.filter((exam) => exam.status === 1));
const finishedExams = computed(() => exams.value.filter((exam) => exam.status >= 2));

const visibleExams = computed(() => {
  if (activeList.value === 'active') return activeExams.value;
  if (activeList.value === 'finished') return finishedExams.value;
  return pendingExams.value;
});

const emptyText = computed(() => {
  if (activeList.value === 'active') return '暂无进行中的考试';
  if (activeList.value === 'finished') return '暂无已完成的考试';
  return '暂无待参加的考试';
});

onMounted(() => {
  loadExams();
});

async function loadExams(manual = false) {
  loading.value = true;
  try {
    const response = await listStudentExams(1, 10000);
    exams.value = response.data.list;
    if (manual) {
      ElMessage.success('考试列表已刷新');
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载考试列表失败');
  } finally {
    loading.value = false;
  }
}

function isExamAvailable(exam: StudentExamInfo) {
  const now = new Date();
  const start = new Date(exam.startTime);
  const end = new Date(exam.endTime);
  return now >= start && now <= end;
}

function startExam(exam: StudentExamInfo) {
  if (exam.status === 0 && !isExamAvailable(exam)) {
    ElMessage.warning('考试尚未开始或已结束');
    return;
  }
  emits('start-exam', exam);
}

function viewResult(attemptId: number) {
  emits('view-result', attemptId);
}

function statusText(status: number) {
  if (status === 0) return '待参加';
  if (status === 1) return '进行中';
  if (status === 4) return '待批阅';
  if (status === 5) return '已出分';
  return '已交卷';
}

function tagType(status: number) {
  if (status === 1) return 'warning';
  if (status >= 4) return 'success';
  if (status >= 2) return 'info';
  return 'primary';
}
</script>

<style scoped>
.exam-list-panel {
  display: grid;
  gap: 14px;
}

.exam-list-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.exam-table-card {
  padding: 12px;
}

.exam-name-cell {
  display: grid;
  gap: 4px;
}

.exam-name-cell span {
  color: #64748b;
  font-size: 12px;
}

.exam-name-cell small {
  color: #2563eb;
  font-size: 12px;
  font-weight: 600;
}
</style>
