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

    <div v-if="rulesReminderExam" class="rules-reminder-alert">
      <div>
        <strong>Rules confirmation required</strong>
        <span>{{ rulesReminderExam.examName }}</span>
      </div>
      <el-button
        type="warning"
        plain
        size="small"
        :disabled="!canEnterExam(rulesReminderExam)"
        @click="startExam(rulesReminderExam)"
      >
        Confirm rules
      </el-button>
    </div>

    <div class="mp-table-card exam-table-card">
      <el-table v-loading="loading" :data="visibleExams" :row-class-name="examRowClassName" border>
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
            <div class="exam-status-cell">
              <el-tag :type="tagType(scope.row as StudentExamInfo)">{{ statusText(scope.row as StudentExamInfo) }}</el-tag>
              <small>{{ examAccessHint(scope.row as StudentExamInfo) }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="scope">
            <el-button
              v-if="scope.row.status < 2"
              type="primary"
              :icon="AlarmClock"
              :disabled="!canEnterExam(scope.row as StudentExamInfo)"
              :title="examAccessHint(scope.row as StudentExamInfo)"
              @click="startExam(scope.row as StudentExamInfo)"
            >
              {{ scope.row.status === 1 ? '继续答题' : '进入考试' }}
            </el-button>
            <el-button
              v-else
              link
              type="primary"
              :icon="View"
              :disabled="!canViewResult(scope.row as StudentExamInfo)"
              @click="viewResult(scope.row as StudentExamInfo)"
            >
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
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { AlarmClock, Refresh, View } from '@element-plus/icons-vue';
import { listStudentExams, type StudentExamInfo } from '../api/exam';
import { flushStoredMonitorQueues } from '../api/monitor';
import { formatDateTime } from '../utils/dateFormat';
import { persistRulesConfirmation, syncRulesConfirmationFromServer } from '../utils/rulesConfirmationStorage';

const activeList = ref<'pending' | 'active' | 'finished'>('pending');
const loading = ref(false);
const exams = ref<StudentExamInfo[]>([]);
const monitorFlushInFlight = ref(false);
const route = useRoute();
const router = useRouter();

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

const focusedAttemptId = computed(() => routeAttemptId());
const rulesReminderExam = computed(() => {
  if (route.query.notice !== 'rules' || !focusedAttemptId.value) return null;
  const exam = exams.value.find((item) => item.attemptId === focusedAttemptId.value) || null;
  if (!exam || exam.status >= 2 || exam.rulesConfirmedAt) return null;
  return exam;
});

const emptyText = computed(() => {
  if (activeList.value === 'active') return '暂无进行中的考试';
  if (activeList.value === 'finished') return '暂无已完成的考试';
  return '暂无待参加的考试';
});

onMounted(async () => {
  await loadExams();
});

watch(
  () => [route.query.attemptId, route.query.notice, exams.value.length],
  () => applyRouteAttemptFocus()
);

async function loadExams(manual = false) {
  loading.value = true;
  try {
    void flushPendingMonitorQueues();
    const response = await listStudentExams(1, 10000);
    response.data.list.forEach((exam) => syncRulesConfirmationFromServer(exam.attemptId, exam.rulesConfirmedAt));
    exams.value = response.data.list;
    applyRouteAttemptFocus();
    if (manual) {
      ElMessage.success('考试列表已刷新');
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '加载考试列表失败');
  } finally {
    loading.value = false;
  }
}

async function flushPendingMonitorQueues() {
  if (monitorFlushInFlight.value) return;
  monitorFlushInFlight.value = true;
  try {
    await flushStoredMonitorQueues();
  } catch {
    // Pending monitor events are retried opportunistically and must not block exam center loading.
  } finally {
    monitorFlushInFlight.value = false;
  }
}

function isExamAvailable(exam: StudentExamInfo) {
  const now = new Date();
  const start = new Date(exam.startTime);
  const end = new Date(exam.endTime);
  return now >= start && now <= end;
}

function canEnterExam(exam: StudentExamInfo) {
  if (exam.status >= 2) return false;
  if (exam.canStart === true || exam.canStart === 1) return true;
  if (exam.accessStatus) {
    return exam.accessStatus === 'READY' || exam.accessStatus === 'IN_PROGRESS';
  }
  return isExamAvailable(exam);
}

async function startExam(exam: StudentExamInfo) {
  if (!canEnterExam(exam)) {
    ElMessage.warning('考试尚未开始或已结束');
    return;
  }
  if (requiresRulesConfirmation(exam)) {
    try {
      await ElMessageBox.confirm(
        'Please confirm you will complete this exam independently, keep the exam window active, and submit before the deadline.',
        'Exam rules confirmation',
        {
          type: 'warning',
          confirmButtonText: 'Confirm and start',
          cancelButtonText: 'Cancel'
        }
      );
      persistRulesConfirmation(exam.attemptId);
    } catch {
      return;
    }
  }
  await clearRulesReminderRoute(exam.attemptId);
  emits('start-exam', exam);
}

function requiresRulesConfirmation(exam: StudentExamInfo) {
  if (exam.status === 0 && exam.accessStatus !== 'IN_PROGRESS') {
    return true;
  }
  return route.query.notice === 'rules' && focusedAttemptId.value === exam.attemptId && !exam.rulesConfirmedAt;
}

function routeAttemptId() {
  const raw = Array.isArray(route.query.attemptId) ? route.query.attemptId[0] : route.query.attemptId;
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function applyRouteAttemptFocus() {
  const attemptId = routeAttemptId();
  if (!attemptId || exams.value.length === 0) return;
  const exam = exams.value.find((item) => item.attemptId === attemptId);
  if (!exam) return;
  if (exam.status === 1) activeList.value = 'active';
  else if (exam.status >= 2) activeList.value = 'finished';
  else activeList.value = 'pending';
}

function examRowClassName({ row }: { row: StudentExamInfo }) {
  return row.attemptId === focusedAttemptId.value ? 'exam-row-focused' : '';
}

async function clearRulesReminderRoute(attemptId: number) {
  if (route.query.notice !== 'rules' || focusedAttemptId.value !== attemptId) return;
  const nextQuery = { ...route.query };
  delete nextQuery.notice;
  delete nextQuery.attemptId;
  await router.replace({ path: route.path, query: nextQuery }).catch(() => {});
}

function examAccessHint(exam: StudentExamInfo) {
  const accessStatus = exam.accessStatus || (isExamAvailable(exam) ? 'READY' : 'WAITING');
  if (accessStatus === 'WAITING') return `Starts in ${formatDuration(Math.max(0, Number(exam.secondsUntilStart || 0)))}`;
  if (accessStatus === 'CLOSED') return 'Exam window closed';
  if (accessStatus === 'UNPUBLISHED') return 'Exam is not published';
  if (accessStatus === 'SUBMITTED') return 'Submitted';
  if (accessStatus === 'IN_PROGRESS') return 'In progress';
  if (accessStatus === 'READY' && typeof exam.secondsUntilEnd === 'number') {
    return `Open, ${formatDuration(Math.max(0, exam.secondsUntilEnd))} left`;
  }
  return 'Ready';
}

function formatDuration(totalSeconds: number) {
  const seconds = Math.max(0, Math.floor(totalSeconds));
  const days = Math.floor(seconds / 86400);
  const hours = Math.floor((seconds % 86400) / 3600);
  const minutes = Math.floor((seconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

function canViewResult(exam: StudentExamInfo) {
  return (exam.scoreVisible === true || exam.scoreVisible === 1) && exam.scoreVisibility === 'RELEASED' && exam.status === 5;
}

function viewResult(exam: StudentExamInfo) {
  if (!canViewResult(exam)) {
    ElMessage.warning('成绩尚未发布');
    return;
  }
  emits('view-result', exam.attemptId);
}

function statusText(exam: StudentExamInfo) {
  const status = exam.status;
  if (status === 0) return '待参加';
  if (status === 1) return '进行中';
  if (status === 4) return '待批阅';
  if (exam.scoreVisibility === 'PENDING_RECHECK') return '复核中';
  if (status === 5 && exam.scoreReleaseStatus !== 1) return '待发布';
  if (status === 5) return '已出分';
  return '已交卷';
}

function tagType(exam: StudentExamInfo) {
  const status = exam.status;
  if (status === 1) return 'warning';
  if (exam.scoreVisibility === 'PENDING_RECHECK') return 'warning';
  if (status === 5 && exam.scoreReleaseStatus !== 1) return 'info';
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

.rules-reminder-alert {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border: 1px solid #fde68a;
  border-radius: 8px;
  background: #fffbeb;
}

.rules-reminder-alert > div {
  display: grid;
  gap: 3px;
}

.rules-reminder-alert strong {
  color: #92400e;
  font-size: 14px;
}

.rules-reminder-alert span {
  color: #78350f;
  font-size: 13px;
}

.exam-table-card :deep(.exam-row-focused td) {
  background: #fffbeb !important;
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

.exam-status-cell {
  display: grid;
  gap: 4px;
}

.exam-status-cell small {
  color: #64748b;
  font-size: 12px;
  line-height: 1.3;
}

@media (max-width: 640px) {
  .rules-reminder-alert {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
