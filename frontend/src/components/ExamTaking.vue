<template>
  <div class="exam-taking-page">
    <header class="exam-header">
      <div class="exam-title-block">
        <span>考试作答</span>
        <h1>{{ examDetails?.examName || '考试' }}</h1>
      </div>
      <div class="exam-header-meta">
        <div :class="['exam-save-pill', saveState]">{{ saveStateText }}</div>
        <div v-if="draftSource" class="exam-save-pill recovery">{{ draftSourceText }}</div>
        <div :class="['exam-timer', { warning: timeLeft <= 300 }]">
          <el-icon><AlarmClock /></el-icon>
          <span>{{ formattedTime }}</span>
        </div>
        <el-button plain :icon="Close" @click="confirmLeaveExam">退出考试</el-button>
      </div>
    </header>

    <el-alert
      v-if="monitorNotice"
      class="monitor-notice"
      :title="monitorNoticeTitle"
      :description="monitorNotice.content"
      type="warning"
      show-icon
      closable
      @close="monitorNotice = null"
    />

    <section v-if="examDetails" :class="['recovery-panel', recoveryPanelTone]">
      <div class="recovery-status">
        <el-tag :type="recoveryTagType" effect="dark">{{ recoveryLevelText }}</el-tag>
        <div>
          <strong>{{ recoveryTitle }}</strong>
          <span>{{ recoverySubtitle }}</span>
        </div>
      </div>
      <div class="recovery-grid">
        <div class="recovery-item">
          <span>网络/心跳</span>
          <strong>{{ networkStatusText }}</strong>
          <small>{{ heartbeatEvidenceText }}</small>
        </div>
        <div class="recovery-item">
          <span>服务器草稿</span>
          <strong>{{ serverDraftEvidenceText }}</strong>
          <small>{{ serverDraftMetaText }}</small>
        </div>
        <div class="recovery-item">
          <span>本机备份</span>
          <strong>{{ localDraftEvidenceText }}</strong>
          <small>{{ localDraftMetaText }}</small>
        </div>
        <div class="recovery-item">
          <span>提交保护</span>
          <strong>{{ submitRetryEvidenceText }}</strong>
          <small>{{ monitorQueueEvidenceText }}</small>
        </div>
      </div>
      <div class="recovery-actions">
        <el-button
          type="primary"
          plain
          :icon="Refresh"
          :loading="recoverySyncing"
          @click="retryRecoverySync"
        >
          立即同步
        </el-button>
        <el-button
          plain
          :icon="FolderOpened"
          :disabled="!hasRecoverableLocalDraft"
          @click="restoreLocalDraftFromPanel"
        >
          恢复本机备份
        </el-button>
      </div>
    </section>

    <main class="exam-layout">
      <section class="question-main">
        <div v-if="currentQuestion" class="question-card">
          <div class="question-meta">
            <el-tag>{{ questionTypeText(currentQuestion.questionType) }}</el-tag>
            <el-tag type="info">{{ difficultyText(currentQuestion.difficulty) }}</el-tag>
            <span>{{ currentQuestion.score }} 分</span>
          </div>
          <h2 class="question-stem">{{ currentQuestionIndex + 1 }}. {{ currentQuestion.stem }}</h2>

          <div v-if="isObjective(currentQuestion.questionType)" class="option-grid">
            <button
              v-for="option in currentQuestion.options || []"
              :key="option.optionLabel"
              type="button"
              :class="['option-card', { active: isOptionSelected(option.optionLabel) }]"
              @click="toggleOption(option.optionLabel)"
            >
              <span class="option-label">{{ option.optionLabel }}</span>
              <span class="option-content">{{ option.optionContent }}</span>
            </button>
          </div>

          <el-input
            v-else
            v-model="textAnswer"
            type="textarea"
            :rows="10"
            placeholder="请输入你的答案"
          />

          <div class="question-actions">
            <el-button @click="prevQuestion" :disabled="currentQuestionIndex === 0">上一题</el-button>
            <el-button
              type="primary"
              @click="nextQuestion"
              :disabled="currentQuestionIndex === questionCount - 1"
            >
              下一题
            </el-button>
          </div>
        </div>
        <el-empty v-else description="正在加载试题" />
      </section>

      <aside class="question-nav">
        <div class="nav-header">
          <strong>题目导航</strong>
          <span>{{ answeredCount }} / {{ questionCount }}</span>
        </div>
        <div class="nav-grid">
          <button
            v-for="(question, index) in examDetails?.questions || []"
            :key="questionKey(question)"
            type="button"
            :class="['nav-item', { active: index === currentQuestionIndex, answered: hasAnswer(question) }]"
            @click="currentQuestionIndex = index"
          >
            {{ index + 1 }}
          </button>
        </div>
        <div class="nav-footer">
          <el-button type="primary" plain :loading="saveState === 'saving'" @click="persistDraft(true)">
            保存草稿
          </el-button>
          <el-button type="success" @click="confirmSubmit">交卷</el-button>
        </div>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { AlarmClock, Close, FolderOpened, Refresh } from '@element-plus/icons-vue';
import { examAttemptHeartbeat, saveExamDraft, startExam, submitExam, type ExamDetail } from '../api/exam';
import { recordCheatEventsBatch, type CheatEventPayload } from '../api/monitor';
import { getMyNotifications, markRead, type Notification as SiteNotification } from '../api/notification';
import { ApiError } from '../api/request';
import { persistRulesConfirmation, readRulesConfirmation, syncRulesConfirmationFromServer } from '../utils/rulesConfirmationStorage';

const props = defineProps<{ attemptId: number }>();
const emit = defineEmits<{
  (e: 'submit-success'): void;
  (e: 'leave-exam'): void;
}>();

type ExamQuestion = ExamDetail['questions'][number];
type SaveState = 'idle' | 'saving' | 'saved' | 'failed';
type AnswerValue = string | string[];

interface LocalExamDraft {
  answers: Record<number, AnswerValue>;
  savedAt: string;
  draftRevision?: number;
  examName?: string;
}

const examDetails = ref<ExamDetail | null>(null);
const currentQuestionIndex = ref(0);
const answers = ref<Record<number, AnswerValue>>({});
const timeLeft = ref(0);
const saveState = ref<SaveState>('idle');
const lastSavedAt = ref('');
const hasUnsavedChanges = ref(false);
const draftRevision = ref(0);
const draftConflictLocked = ref(false);
const submitInFlight = ref(false);
const clientDraftId = ref('');
const submitToken = ref('');
const draftSource = ref('');
const networkOnline = ref(typeof navigator === 'undefined' ? true : navigator.onLine);
const heartbeatError = ref('');
const lastHeartbeatAt = ref('');
const serverDraftSavedAt = ref('');
const serverDraftSavedCount = ref(0);
const localDraftSavedAt = ref('');
const localDraftRevision = ref(0);
const draftRetryPending = ref(false);
const submitRetryPending = ref(false);
const monitorQueueSize = ref(0);
const recoverySyncing = ref(false);

let timer: ReturnType<typeof setInterval> | null = null;
let autoSaveTimer: ReturnType<typeof setInterval> | null = null;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let monitorFlushTimer: ReturnType<typeof setInterval> | null = null;
let monitorNoticeTimer: ReturnType<typeof setInterval> | null = null;
let retrySaveTimer: ReturnType<typeof setTimeout> | null = null;
let submitRetryTimer: ReturnType<typeof setTimeout> | null = null;
let draftHydrated = false;
let allowPageLeave = false;
let leaveConfirmOpen = false;
let monitorFlushInFlight = false;
let rulesReminderConfirmOpen = false;
let monitorNoticeStartedAt = Date.now();
let latestMonitorNotificationId = 0;
const monitorEventQueue: CheatEventPayload[] = [];

const localDraftKey = computed(() => `smart_exam_local_draft_${props.attemptId}`);
const clientDraftIdKey = computed(() => `smart_exam_client_draft_id_${props.attemptId}`);
const submitTokenKey = computed(() => `smart_exam_submit_token_${props.attemptId}`);
const monitorQueueKey = computed(() => `smart_exam_monitor_queue_${props.attemptId}`);
const currentQuestion = computed(() => examDetails.value?.questions[currentQuestionIndex.value] || null);
const currentQuestionId = computed(() => (currentQuestion.value ? questionKey(currentQuestion.value) : 0));
const textAnswer = computed({
  get() {
    const value = answers.value[currentQuestionId.value];
    return Array.isArray(value) ? value.join('') : value || '';
  },
  set(value: string) {
    answers.value[currentQuestionId.value] = value;
  }
});
const questionCount = computed(() => examDetails.value?.questions?.length || 0);
const answeredCount = computed(() => (examDetails.value?.questions || []).filter(hasAnswer).length);
const unansweredCount = computed(() => Math.max(0, questionCount.value - answeredCount.value));

const formattedTime = computed(() => {
  const minutes = Math.floor(Math.max(0, timeLeft.value) / 60);
  const seconds = Math.max(0, timeLeft.value) % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
});

const saveStateText = computed(() => {
  if (draftConflictLocked.value) return '服务器已有更新草稿，请重新进入考试后继续';
  if (saveState.value === 'saving') return '正在保存草稿...';
  if (saveState.value === 'saved') return lastSavedAt.value ? `草稿已保存 ${lastSavedAt.value}` : '草稿已保存';
  if (saveState.value === 'failed') return '草稿保存失败，网络恢复后会自动重试';
  return hasUnsavedChanges.value ? '有未保存作答' : '草稿自动保存已开启';
});

const draftSourceText = computed(() => {
  if (draftSource.value === 'REDIS') return '缓存恢复';
  if (draftSource.value === 'DB') return '草稿恢复';
  return draftSource.value;
});
const monitorNotice = ref<SiteNotification | null>(null);
const rulesReminderNoticeTitle = 'Rules confirmation required';
const monitorNoticeTitle = computed(() => {
  if (monitorNotice.value?.type === 'MONITOR_RULES_REMINDER') return rulesReminderNoticeTitle;
  if (!monitorNotice.value) return '监考提醒';
  if (monitorNotice.value.type === 'MONITOR_FORCE_SUBMIT') return '答卷已被强制交卷';
  return '监考提醒';
});

const recoveryLevel = computed(() => {
  if (draftConflictLocked.value || submitRetryPending.value) return 'HIGH';
  if (!networkOnline.value || heartbeatError.value || saveState.value === 'failed' || draftRetryPending.value) {
    return 'WARN';
  }
  if (hasUnsavedChanges.value || monitorQueueSize.value > 0) return 'WARN';
  return 'OK';
});

const recoveryPanelTone = computed(() => recoveryLevel.value.toLowerCase());
const recoveryTagType = computed(() => {
  if (recoveryLevel.value === 'HIGH') return 'danger';
  if (recoveryLevel.value === 'WARN') return 'warning';
  return 'success';
});
const recoveryLevelText = computed(() => {
  if (recoveryLevel.value === 'HIGH') return '需要处理';
  if (recoveryLevel.value === 'WARN') return '待同步';
  return '已同步';
});
const recoveryTitle = computed(() => {
  if (draftConflictLocked.value) return '服务器已有更新草稿';
  if (submitRetryPending.value) return '交卷正在重试';
  if (!networkOnline.value) return '当前离线，已保留本机备份';
  if (heartbeatError.value) return '服务器心跳暂未同步';
  if (saveState.value === 'failed' || draftRetryPending.value) return '草稿等待重试';
  if (hasUnsavedChanges.value) return '存在未同步作答';
  if (monitorQueueSize.value > 0) return '监考事件等待上传';
  return '作答恢复状态正常';
});
const recoverySubtitle = computed(() => {
  const answered = `${answeredCount.value}/${questionCount.value} 已答`;
  const serverDraft = serverDraftSavedAt.value ? `服务器草稿 ${formatRecoveryDate(serverDraftSavedAt.value)}` : '暂无服务器草稿';
  return `${answered} · ${serverDraft}`;
});
const networkStatusText = computed(() => networkOnline.value ? '在线' : '离线');
const heartbeatEvidenceText = computed(() => {
  if (heartbeatError.value) return heartbeatError.value;
  return lastHeartbeatAt.value ? `最后心跳 ${formatRecoveryDate(lastHeartbeatAt.value)}` : '等待首次心跳';
});
const serverDraftEvidenceText = computed(() => {
  if (draftConflictLocked.value) return '存在更新版本';
  if (!serverDraftSavedAt.value && draftRevision.value <= 0) return '暂无服务器草稿';
  return `r${draftRevision.value || 0} · ${draftSourceText.value || '服务器'}`;
});
const serverDraftMetaText = computed(() => {
  const savedAt = serverDraftSavedAt.value ? formatRecoveryDate(serverDraftSavedAt.value) : '-';
  return `保存 ${savedAt} · ${serverDraftSavedCount.value || 0} 次`;
});
const localDraftEvidenceText = computed(() => {
  if (!localDraftSavedAt.value) return '暂无本机备份';
  return `r${localDraftRevision.value || 0} · ${formatRecoveryDate(localDraftSavedAt.value)}`;
});
const localDraftMetaText = computed(() => hasRecoverableLocalDraft.value ? '可用于异常恢复' : '无可恢复备份');
const submitRetryEvidenceText = computed(() => submitRetryPending.value ? '自动重试中' : '无待重试交卷');
const monitorQueueEvidenceText = computed(() => monitorQueueSize.value > 0
  ? `监考事件队列 ${monitorQueueSize.value} 条`
  : '监考事件已同步');
const hasRecoverableLocalDraft = computed(() => Boolean(localDraftSavedAt.value) && !draftConflictLocked.value);

watch(answers, () => {
  if (!draftHydrated) return;
  hasUnsavedChanges.value = true;
  writeLocalDraft();
  if (saveState.value === 'saved') saveState.value = 'idle';
}, { deep: true });

onMounted(async () => {
  try {
    const response = await startExam(props.attemptId, { rulesConfirmed: readRulesConfirmation(props.attemptId) });
    syncRulesConfirmationFromServer(props.attemptId, response.data.rulesConfirmedAt);
    if (response.data.autoSubmitted || response.data.submitted || response.data.alreadySubmitted || Number(response.data.status || 0) >= 2) {
      allowPageLeave = true;
      clearLocalDraft();
      clearSubmitToken();
      ElMessage.warning('考试已超时，系统已根据已保存草稿自动交卷。');
      emit('submit-success');
      return;
    }

    examDetails.value = response.data;
    draftRevision.value = Number(response.data.draftRevision || 0);
    draftSource.value = response.data.draftSource || '';
    lastHeartbeatAt.value = response.data.lastHeartbeatAt || '';
    serverDraftSavedAt.value = response.data.draftSavedAt || response.data.lastDraftSavedAt || '';
    serverDraftSavedCount.value = Number(response.data.draftSavedCount || 0);
    clientDraftId.value = resolveClientDraftId(response.data.draftClientDraftId || undefined);
    submitToken.value = restoreSubmitToken();
    hydrateServerDraft(response.data.draftAnswers);
    await restoreLocalDraftIfNeeded();
    refreshLocalDraftEvidence();
    draftHydrated = true;
    timeLeft.value = response.data.remainingSeconds ?? (response.data.durationMinutes || 0) * 60;

    restoreMonitorQueue();
    updateMonitorQueueSize();
    startTimer();
    startAutoSave();
    startHeartbeat();
    startMonitorFlush();
    await initializeMonitorNotifications();
    startMonitorNoticePolling();
    bindGuards();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '进入考试失败，可能考试已结束或答卷不可用。');
    allowPageLeave = true;
    emit('leave-exam');
  }
});

onUnmounted(() => {
  stopTimers();
  persistMonitorQueue();
  unbindGuards();
});

function hydrateServerDraft(raw?: string | null) {
  if (!raw) return;
  try {
    answers.value = JSON.parse(raw) as Record<number, AnswerValue>;
  } catch {
    answers.value = {};
  }
}

function startTimer() {
  clearTimer(timer);
  timer = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value -= 1;
      return;
    }
    clearTimer(timer);
    submit(true);
  }, 1000);
}

function startAutoSave() {
  clearTimer(autoSaveTimer);
  autoSaveTimer = setInterval(() => persistDraft(false), 20000);
}

function startHeartbeat() {
  clearTimer(heartbeatTimer);
  heartbeatTimer = setInterval(() => syncServerTime(), 30000);
}

function startMonitorFlush() {
  clearTimer(monitorFlushTimer);
  monitorFlushTimer = setInterval(() => flushMonitorEvents(), 10000);
}

async function initializeMonitorNotifications() {
  monitorNoticeStartedAt = Date.now();
  try {
    const response = await getMyNotifications(1, 10, currentAttemptNotificationQuery());
    const monitorNotifications = response.data.list
      .filter(isMonitorNotification)
      .filter(isCurrentExamNotification);
    latestMonitorNotificationId = Math.max(
      0,
      ...monitorNotifications.map((item) => Number(item.id || 0))
    );
    await handleExistingUnreadRulesReminder(monitorNotifications);
  } catch {
    latestMonitorNotificationId = 0;
  }
}

async function handleExistingUnreadRulesReminder(notifications: SiteNotification[]) {
  const unreadRulesReminder = notifications
    .filter((item) => item.type === 'MONITOR_RULES_REMINDER' && isUnreadNotification(item))
    .sort((a, b) => Number(b.id || 0) - Number(a.id || 0))[0];
  if (!unreadRulesReminder) return;
  monitorNotice.value = unreadRulesReminder;
  ElMessage.warning('Please confirm the exam rules before continuing.');
  await confirmRulesFromMonitorNotice(unreadRulesReminder);
}

function startMonitorNoticePolling() {
  clearTimer(monitorNoticeTimer);
  monitorNoticeTimer = setInterval(() => loadMonitorNotice(), 15000);
}

async function loadMonitorNotice() {
  if (allowPageLeave) return;
  try {
    const response = await getMyNotifications(1, 10, currentAttemptNotificationQuery());
    const notices = response.data.list
      .filter(isMonitorNotification)
      .filter((item) => Number(item.id || 0) > latestMonitorNotificationId)
      .filter(isCurrentExamNotification)
      .sort((a, b) => Number(b.id || 0) - Number(a.id || 0));
    if (notices.length === 0) return;
    monitorNotice.value = notices[0];
    latestMonitorNotificationId = Math.max(latestMonitorNotificationId, ...notices.map((item) => Number(item.id || 0)));
    if (notices[0].type === 'MONITOR_RULES_REMINDER') {
      ElMessage.warning('Please confirm the exam rules before continuing.');
      await confirmRulesFromMonitorNotice(notices[0]);
    }
    ElMessage.warning(notices[0].type === 'MONITOR_FORCE_SUBMIT' ? '监考教师已强制交卷' : '收到新的监考提醒');
  } catch {
    // Notification polling must never disturb answering.
  }
}

function currentAttemptNotificationQuery() {
  return {
    relatedType: 'EXAM_ATTEMPT',
    relatedId: props.attemptId
  };
}

function isMonitorNotification(item: SiteNotification) {
  return item.type === 'MONITOR_WARNING'
    || item.type === 'MONITOR_FORCE_SUBMIT'
    || item.type === 'MONITOR_RULES_REMINDER';
}

async function confirmRulesFromMonitorNotice(notice?: SiteNotification) {
  if (rulesReminderConfirmOpen) return;
  if (readRulesConfirmation(props.attemptId)) {
    await acknowledgeAlreadyConfirmedRulesReminder(notice);
    return;
  }
  rulesReminderConfirmOpen = true;
  try {
    await ElMessageBox.confirm(
      'Please confirm that you have read and will follow the exam rules.',
      rulesReminderNoticeTitle,
      {
        confirmButtonText: 'Confirm rules',
        cancelButtonText: 'Later',
        type: 'warning'
      }
    );
    const response = await startExam(props.attemptId, { rulesConfirmed: true });
    if (response.data.submitted || response.data.autoSubmitted || response.data.alreadySubmitted || Number(response.data.status || 0) >= 2) {
      allowPageLeave = true;
      clearLocalDraft();
      clearSubmitToken();
      clearSubmitRetryTimer();
      ElMessage.warning('Exam attempt is already submitted.');
      emit('submit-success');
      return;
    }
    persistRulesConfirmation(props.attemptId);
    await markMonitorNoticeRead(notice);
    if (monitorNotice.value?.type === 'MONITOR_RULES_REMINDER') {
      monitorNotice.value = null;
    }
    ElMessage.success('Exam rules confirmed.');
  } catch (error) {
    if (error instanceof Error) {
      ElMessage.error(error.message);
    }
  } finally {
    rulesReminderConfirmOpen = false;
  }
}

async function acknowledgeAlreadyConfirmedRulesReminder(notice?: SiteNotification) {
  try {
    const response = await startExam(props.attemptId, { rulesConfirmed: true });
    if (response.data.submitted || response.data.autoSubmitted || response.data.alreadySubmitted || Number(response.data.status || 0) >= 2) {
      allowPageLeave = true;
      clearLocalDraft();
      clearSubmitToken();
      clearSubmitRetryTimer();
      ElMessage.warning('Exam attempt is already submitted.');
      emit('submit-success');
      return;
    }
    await markMonitorNoticeRead(notice);
    if (monitorNotice.value?.type === 'MONITOR_RULES_REMINDER') {
      monitorNotice.value = null;
    }
  } catch {
    // Server confirmation is still the audit source; keep the notice unread if the retry fails.
  }
}

async function markMonitorNoticeRead(notice?: SiteNotification) {
  if (!notice?.id) return;
  try {
    await markRead(notice.id);
    notice.isRead = 1;
  } catch {
    // Read-state sync is audit help only; answering must continue.
  }
}

function isUnreadNotification(notice: SiteNotification) {
  const value = String(notice.isRead);
  return Number(notice.isRead) === 0 || value === 'false';
}

function isCurrentExamNotification(item: SiteNotification) {
  if (item.relatedType === 'EXAM_ATTEMPT' && Number(item.relatedId || 0) > 0) {
    return Number(item.relatedId) === props.attemptId;
  }
  const createdAt = Date.parse(String(item.createdAt || '').replace(' ', 'T'));
  if (Number.isNaN(createdAt)) return true;
  return createdAt >= monitorNoticeStartedAt - 5000;
}

async function syncServerTime() {
  try {
    const response = await examAttemptHeartbeat(props.attemptId);
    const data = response.data;
    heartbeatError.value = '';
    lastHeartbeatAt.value = data.lastHeartbeatAt || new Date().toISOString();
    if (data.submitted || data.autoSubmitted || data.status >= 2) {
      allowPageLeave = true;
      clearLocalDraft();
      clearSubmitToken();
      clearSubmitRetryTimer();
      ElMessage.warning(heartbeatSubmitMessage(data));
      emit('submit-success');
      return;
    }
    if (data.remainingSeconds >= 0) {
      timeLeft.value = Math.max(0, data.remainingSeconds);
    }
    if (typeof data.draftRevision === 'number') {
      draftRevision.value = Math.max(draftRevision.value, data.draftRevision);
    }
    if (data.draftSource) {
      draftSource.value = data.draftSource;
    }
    if (data.draftSavedAt || data.lastDraftSavedAt) {
      serverDraftSavedAt.value = data.draftSavedAt || data.lastDraftSavedAt || '';
    }
    if (typeof data.draftSavedCount === 'number') {
      serverDraftSavedCount.value = data.draftSavedCount;
    }
  } catch {
    heartbeatError.value = '心跳同步失败，网络恢复后会自动重试';
    reportExamEvent('HEARTBEAT_FAILED');
  }
}

function heartbeatSubmitMessage(data: {
  autoSubmitted?: boolean;
  forcedSubmitted?: boolean;
  submitType?: string;
  message?: string;
}) {
  if (data.forcedSubmitted || data.submitType === 'FORCED') {
    return '考试已关闭，系统已根据已保存草稿强制交卷。';
  }
  if (data.autoSubmitted || data.submitType === 'TIMEOUT') {
    return '考试已超时，系统已自动交卷。';
  }
  return data.message || '答卷已提交。';
}

async function persistDraft(manual: boolean) {
  if (draftConflictLocked.value) {
    if (manual) ElMessage.warning('服务器已有更新的草稿，请重新进入考试后继续。');
    return;
  }
  if (!examDetails.value || !hasUnsavedChanges.value) {
    if (manual) ElMessage.info('当前没有新的作答需要保存');
    return;
  }
  saveState.value = 'saving';
  try {
    const nextRevision = Date.now();
    const response = await saveExamDraft(props.attemptId, JSON.stringify(answers.value), {
      clientDraftId: clientDraftId.value || resolveClientDraftId(),
      revision: nextRevision
    });
    if (response.data.stale) {
      draftRevision.value = Math.max(draftRevision.value, response.data.serverRevision || 0);
      draftConflictLocked.value = true;
      saveState.value = 'failed';
      clearRetryTimer();
      writeLocalDraft(response.data.revision || nextRevision);
      serverDraftSavedAt.value = response.data.savedAt || serverDraftSavedAt.value;
      if (manual) ElMessage.warning('服务器已有更新的草稿，本次旧草稿未覆盖，请重新进入考试后继续。');
      return;
    }
    draftRevision.value = Math.max(draftRevision.value, response.data.serverRevision || nextRevision);
    if (response.data.draftSource) {
      draftSource.value = response.data.draftSource;
    }
    serverDraftSavedAt.value = response.data.savedAt || new Date().toISOString();
    serverDraftSavedCount.value += 1;
    saveState.value = 'saved';
    lastSavedAt.value = new Date().toLocaleTimeString();
    hasUnsavedChanges.value = false;
    draftRetryPending.value = false;
    clearRetryTimer();
    if (manual) ElMessage.success('草稿已保存');
  } catch (error) {
    saveState.value = 'failed';
    writeLocalDraft();
    clearRetryTimer();
    draftRetryPending.value = true;
    retrySaveTimer = setTimeout(() => persistDraft(false), 5000);
    if (manual) ElMessage.error(error instanceof Error ? error.message : '草稿保存失败，稍后会自动重试');
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent) {
  if (allowPageLeave) return;
  reportExamEvent('PAGE_UNLOAD_ATTEMPT');
  persistMonitorQueue();
  event.preventDefault();
  event.returnValue = '';
}

function handleVisibilityChange() {
  if (document.visibilityState !== 'hidden') return;
  reportExamEvent('VISIBILITY_HIDDEN');
  if (hasUnsavedChanges.value) persistDraft(false);
}

function handleWindowBlur() {
  reportExamEvent('WINDOW_BLUR');
}

function handleCopy() {
  reportExamEvent('COPY');
}

function handlePaste() {
  reportExamEvent('PASTE');
}

function handleContextMenu() {
  reportExamEvent('CONTEXT_MENU');
}

function handleFullscreenChange() {
  if (!document.fullscreenElement) reportExamEvent('FULLSCREEN_EXIT');
}

function handleNetworkOffline() {
  networkOnline.value = false;
  reportExamEvent('NETWORK_OFFLINE');
  writeLocalDraft();
  persistMonitorQueue();
}

function handleNetworkOnline() {
  networkOnline.value = true;
  heartbeatError.value = '';
  reportExamEvent('NETWORK_ONLINE');
  flushMonitorEvents();
  if (hasUnsavedChanges.value) persistDraft(false);
  syncServerTime();
}

function reportExamEvent(eventType: string, extraInfo?: string) {
  if (allowPageLeave || !examDetails.value) return;
  monitorEventQueue.push({
    attemptId: props.attemptId,
    eventType,
    clientEventId: createMonitorEventId(),
    clientEventTime: new Date().toISOString(),
    extraInfo: extraInfo || JSON.stringify({
      examName: examDetails.value.examName,
      questionIndex: currentQuestionIndex.value + 1,
      timeLeft: timeLeft.value,
      online: navigator.onLine,
      at: new Date().toISOString()
    })
  });
  persistMonitorQueue();
  if (monitorEventQueue.length >= 5 || eventType === 'NETWORK_ONLINE') {
    flushMonitorEvents();
  }
}

async function flushMonitorEvents(force = false) {
  if (monitorFlushInFlight) return false;
  if (monitorEventQueue.length === 0) return true;
  if (!force && navigator.onLine === false) {
    persistMonitorQueue();
    updateMonitorQueueSize();
    return false;
  }
  monitorFlushInFlight = true;
  try {
    do {
      const batch = monitorEventQueue.slice(0, 100);
      try {
        await recordCheatEventsBatch(batch);
        monitorEventQueue.splice(0, batch.length);
      } catch (error) {
        if (!isPermanentMonitorUploadReject(error)) {
          throw error;
        }
        const isolatedRemaining = await isolateMonitorUploadFailures(batch);
        monitorEventQueue.splice(0, batch.length, ...isolatedRemaining);
        if (isolatedRemaining.length > 0) {
          persistMonitorQueue();
          updateMonitorQueueSize();
          return false;
        }
      }
      persistMonitorQueue();
      updateMonitorQueueSize();
    } while (force && monitorEventQueue.length > 0);
    updateMonitorQueueSize();
    return monitorEventQueue.length === 0;
  } catch {
    persistMonitorQueue();
    updateMonitorQueueSize();
    return false;
  } finally {
    monitorFlushInFlight = false;
  }
}

async function isolateMonitorUploadFailures(events: CheatEventPayload[]) {
  const remaining = [...events];
  let index = 0;
  while (index < remaining.length) {
    try {
      await recordCheatEventsBatch([remaining[index]]);
      remaining.splice(index, 1);
    } catch (error) {
      if (!isPermanentMonitorUploadReject(error)) {
        return remaining;
      }
      remaining.splice(index, 1);
    }
  }
  return remaining;
}

function isPermanentMonitorUploadReject(error: unknown) {
  return error instanceof ApiError && error.status === 400;
}

function restoreMonitorQueue() {
  try {
    const raw = localStorage.getItem(monitorQueueKey.value);
    if (!raw) {
      updateMonitorQueueSize();
      return;
    }
    const parsed = JSON.parse(raw) as CheatEventPayload[];
    if (Array.isArray(parsed)) {
      monitorEventQueue.splice(0, monitorEventQueue.length, ...parsed.slice(-200));
    }
    updateMonitorQueueSize();
  } catch {
    localStorage.removeItem(monitorQueueKey.value);
    updateMonitorQueueSize();
  }
}

function persistMonitorQueue() {
  try {
    if (monitorEventQueue.length === 0) {
      localStorage.removeItem(monitorQueueKey.value);
      updateMonitorQueueSize();
      return;
    }
    localStorage.setItem(monitorQueueKey.value, JSON.stringify(monitorEventQueue.slice(-200)));
    updateMonitorQueueSize();
  } catch {
    updateMonitorQueueSize();
    // Monitoring must never block answering.
  }
}

function updateMonitorQueueSize() {
  monitorQueueSize.value = monitorEventQueue.length;
}

function createMonitorEventId() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function createClientId(prefix: string) {
  if (window.crypto?.randomUUID) return `${prefix}-${window.crypto.randomUUID()}`;
  return `${prefix}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

function resolveClientDraftId(serverValue?: string) {
  const existing = serverValue || localStorage.getItem(clientDraftIdKey.value);
  const value = existing || createClientId('draft');
  try {
    localStorage.setItem(clientDraftIdKey.value, value);
  } catch {
    // Local storage is a convenience only.
  }
  clientDraftId.value = value;
  return value;
}

function restoreSubmitToken() {
  const existing = sessionStorage.getItem(submitTokenKey.value) || localStorage.getItem(submitTokenKey.value);
  const value = existing || createClientId('submit');
  try {
    sessionStorage.setItem(submitTokenKey.value, value);
    localStorage.setItem(submitTokenKey.value, value);
  } catch {
    // Token also travels in memory if storage is unavailable.
  }
  return value;
}

function clearSubmitToken() {
  submitToken.value = '';
  try {
    sessionStorage.removeItem(submitTokenKey.value);
    localStorage.removeItem(submitTokenKey.value);
  } catch {
    // Ignore storage cleanup failure.
  }
}

function isObjective(type?: string) {
  return !!type && ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type);
}

function questionKey(question: Partial<ExamQuestion>) {
  return Number(question.questionId ?? question.id ?? 0);
}

function hasAnswer(question: Partial<ExamQuestion>) {
  const value = answers.value[questionKey(question)];
  if (Array.isArray(value)) return value.length > 0;
  return value !== undefined && value !== null && String(value).trim() !== '';
}

function isOptionSelected(label: string) {
  const value = answers.value[currentQuestionId.value];
  if (Array.isArray(value)) return value.includes(label);
  return value === label;
}

function toggleOption(label: string) {
  if (!currentQuestion.value) return;
  const key = currentQuestionId.value;
  if (currentQuestion.value.questionType === 'MULTIPLE_CHOICE') {
    const current = Array.isArray(answers.value[key]) ? [...answers.value[key] as string[]] : [];
    answers.value[key] = current.includes(label)
      ? current.filter((item) => item !== label)
      : [...current, label];
    return;
  }
  answers.value[key] = label;
}

function questionTypeText(value?: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SUBJECTIVE: '主观题'
  };
  return value ? map[value] || value : '题目';
}

function difficultyText(value?: string | null) {
  const map: Record<string, string> = {
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难'
  };
  return value ? map[value] || value : '未设难度';
}

function prevQuestion() {
  currentQuestionIndex.value = Math.max(0, currentQuestionIndex.value - 1);
}

function nextQuestion() {
  currentQuestionIndex.value = Math.min(questionCount.value - 1, currentQuestionIndex.value + 1);
}

function confirmSubmit() {
  const message = unansweredCount.value > 0
    ? `There are ${unansweredCount.value} unanswered questions. Submit anyway?`
    : 'Submit exam now? You cannot change answers after submission.';
  ElMessageBox.confirm(message, 'Submit exam', {
    confirmButtonText: 'Submit',
    cancelButtonText: 'Continue answering',
    type: 'warning'
  }).then(() => submit(false)).catch(() => {});
}

async function confirmLeaveExam() {
  if (leaveConfirmOpen) return;
  leaveConfirmOpen = true;
  try {
    await ElMessageBox.confirm(
      '正在考试中，离开前会先尝试保存草稿。下次进入可继续作答。',
      '确认退出考试？',
      {
        confirmButtonText: '保存并退出',
        cancelButtonText: '继续考试',
        type: 'warning'
      }
    );
    await persistDraft(false);
    await flushMonitorEvents(true);
    allowPageLeave = true;
    emit('leave-exam');
  } catch {
    // Continue answering.
  } finally {
    leaveConfirmOpen = false;
  }
}

async function retryRecoverySync() {
  if (recoverySyncing.value) return;
  recoverySyncing.value = true;
  try {
    networkOnline.value = navigator.onLine;
    if (!networkOnline.value) {
      writeLocalDraft();
      persistMonitorQueue();
      ElMessage.warning('当前离线，已保留本机备份');
      return;
    }
    if (hasUnsavedChanges.value || saveState.value === 'failed') {
      await persistDraft(true);
    }
    await syncServerTime();
    const monitorFlushed = await flushMonitorEvents(true);
    updateMonitorQueueSize();
    if (saveState.value === 'failed' || heartbeatError.value || !monitorFlushed) {
      ElMessage.warning('仍有恢复数据等待同步');
      return;
    }
    ElMessage.success('恢复数据已同步');
  } finally {
    recoverySyncing.value = false;
  }
}

async function restoreLocalDraftFromPanel() {
  const localDraft = readLocalDraft();
  if (!localDraft || !hasAnyAnswer(localDraft.answers)) {
    refreshLocalDraftEvidence();
    ElMessage.info('暂无可恢复的本机备份');
    return;
  }
  if (localDraftIsOlderThanServer(localDraft)) {
    clearLocalDraft();
    ElMessage.warning('本机备份早于服务器草稿，已忽略');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `恢复本机备份（${new Date(localDraft.savedAt).toLocaleString()}）并重新保存到服务器？`,
      '恢复本机备份',
      {
        confirmButtonText: '恢复并同步',
        cancelButtonText: '取消',
        type: 'warning'
      }
    );
    applyLocalDraft(localDraft);
    await retryRecoverySync();
  } catch {
    // Keep current answers.
  }
}

async function submit(autoSubmit: boolean) {
  if (submitInFlight.value || allowPageLeave) return;
  submitInFlight.value = true;
  stopTimers();
  try {
    const monitorFlushed = await flushMonitorEvents(true);
    const finalAnswers = normalizeFinalAnswers();
    const token = submitToken.value || restoreSubmitToken();
    submitToken.value = token;
    const response = await submitExam(props.attemptId, { answers: finalAnswers, submitToken: token });
    allowPageLeave = true;
    const lateMonitorFlushed = monitorFlushed || await retryLateMonitorEventsAfterSubmit();
    clearLocalDraft();
    clearSubmitToken();
    if (lateMonitorFlushed) {
      monitorEventQueue.splice(0, monitorEventQueue.length);
      persistMonitorQueue();
    } else {
      persistMonitorQueue();
    }
    ElMessage[autoSubmit ? 'warning' : 'success'](
      response.data.alreadySubmitted ? '答卷已提交，本次为恢复确认。' : (autoSubmit ? '考试时间到，已自动交卷。' : '交卷成功')
    );
    const submitSummary = submitResultSummary(response.data);
    if (submitSummary) ElMessage.info(submitSummary);
    const replayWarning = submitReplayWarning(response.data);
    if (replayWarning) ElMessage.warning(replayWarning);
    emit('submit-success');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '交卷失败');
    if (autoSubmit || timeLeft.value <= 0) {
      scheduleSubmitRetry();
      startHeartbeat();
      startMonitorFlush();
    } else {
      startTimer();
      startAutoSave();
      startHeartbeat();
      startMonitorFlush();
    }
  } finally {
    submitInFlight.value = false;
  }
}

function scheduleSubmitRetry() {
  if (allowPageLeave) return;
  clearSubmitRetryTimer();
  submitRetryPending.value = true;
  submitRetryTimer = setTimeout(() => {
    submitRetryTimer = null;
    submitRetryPending.value = false;
    submit(true);
  }, 5000);
}

async function retryLateMonitorEventsAfterSubmit() {
  if (monitorEventQueue.length === 0) return true;
  return flushMonitorEvents(true);
}

function submitResultSummary(data: { answeredCount?: number; questionCount?: number; unansweredCount?: number }) {
  const questionTotal = Number(data.questionCount);
  const answered = Number(data.answeredCount);
  const unanswered = Number(data.unansweredCount);
  if (![questionTotal, answered, unanswered].every(Number.isFinite)) return '';
  return `Server accepted answers: ${answered}/${questionTotal} answered, ${unanswered} unanswered.`;
}

function submitReplayWarning(data: { submitPayloadMismatch?: boolean; submitTokenMismatch?: boolean }) {
  if (data.submitPayloadMismatch) {
    return 'Server kept the first submitted answers; this retry sent different answers.';
  }
  if (data.submitTokenMismatch) {
    return 'Server kept the first submitted answers; this retry used a different submit token.';
  }
  return '';
}

function normalizeFinalAnswers() {
  const finalAnswers: Record<number, string> = {};
  for (const question of examDetails.value?.questions || []) {
    const key = questionKey(question);
    const value = answers.value[key];
    if (Array.isArray(value)) {
      finalAnswers[key] = [...value].map(String).sort().join('');
    } else {
      finalAnswers[key] = value === undefined || value === null ? '' : String(value);
    }
  }
  return finalAnswers;
}

async function restoreLocalDraftIfNeeded() {
  const localDraft = readLocalDraft();
  if (!localDraft || !hasAnyAnswer(localDraft.answers)) return;
  if (sameAnswers(localDraft.answers, answers.value)) return;
  if (localDraftIsOlderThanServer(localDraft)) {
    clearLocalDraft();
    return;
  }
  try {
    await ElMessageBox.confirm(
      `检测到本机保存的离线草稿（${new Date(localDraft.savedAt).toLocaleString()}），是否恢复？`,
      '恢复本机草稿',
      {
        confirmButtonText: '恢复',
        cancelButtonText: '保留服务器草稿',
        type: 'warning'
      }
    );
    applyLocalDraft(localDraft);
    ElMessage.success('已恢复本机草稿');
  } catch {
    writeLocalDraft();
  }
}

function applyLocalDraft(localDraft: LocalExamDraft) {
  answers.value = localDraft.answers;
  hasUnsavedChanges.value = true;
  saveState.value = 'idle';
  localDraftRevision.value = Number(localDraft.draftRevision || draftRevision.value || 0);
  localDraftSavedAt.value = localDraft.savedAt || new Date().toISOString();
  writeLocalDraft(localDraftRevision.value || draftRevision.value);
}

function readLocalDraft(): LocalExamDraft | null {
  try {
    const raw = localStorage.getItem(localDraftKey.value);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as LocalExamDraft;
    return parsed?.answers ? parsed : null;
  } catch {
    return null;
  }
}

function writeLocalDraft(revision = draftRevision.value) {
  try {
    localStorage.setItem(localDraftKey.value, JSON.stringify({
      answers: answers.value,
      savedAt: new Date().toISOString(),
      draftRevision: revision,
      examName: examDetails.value?.examName
    }));
  } catch {
    // Browser storage may be unavailable.
  } finally {
    refreshLocalDraftEvidence();
  }
}

function localDraftIsOlderThanServer(localDraft: LocalExamDraft) {
  const localRevision = Number(localDraft.draftRevision || 0);
  if (draftRevision.value > 0 && localRevision > 0 && localRevision < draftRevision.value) {
    return true;
  }
  const localSavedAt = Date.parse(localDraft.savedAt || '');
  const serverSavedAt = Date.parse(String(serverDraftSavedAt.value || examDetails.value?.draftSavedAt || '').replace(' ', 'T'));
  return !Number.isNaN(localSavedAt)
    && !Number.isNaN(serverSavedAt)
    && localSavedAt < serverSavedAt;
}

function clearLocalDraft() {
  try {
    localStorage.removeItem(localDraftKey.value);
  } catch {
    // Ignore storage failure.
  } finally {
    refreshLocalDraftEvidence();
  }
}

function refreshLocalDraftEvidence() {
  const localDraft = readLocalDraft();
  localDraftSavedAt.value = localDraft?.savedAt || '';
  localDraftRevision.value = Number(localDraft?.draftRevision || 0);
}

function formatRecoveryDate(value?: string | null) {
  if (!value) return '-';
  const timestamp = Date.parse(String(value).replace(' ', 'T'));
  if (Number.isNaN(timestamp)) return String(value);
  return new Date(timestamp).toLocaleString();
}

function bindGuards() {
  bindHistoryGuard();
  window.addEventListener('beforeunload', handleBeforeUnload);
  window.addEventListener('popstate', handlePopState);
  window.addEventListener('blur', handleWindowBlur);
  window.addEventListener('copy', handleCopy);
  window.addEventListener('paste', handlePaste);
  window.addEventListener('contextmenu', handleContextMenu);
  window.addEventListener('offline', handleNetworkOffline);
  window.addEventListener('online', handleNetworkOnline);
  document.addEventListener('visibilitychange', handleVisibilityChange);
  document.addEventListener('fullscreenchange', handleFullscreenChange);
}

function unbindGuards() {
  window.removeEventListener('beforeunload', handleBeforeUnload);
  window.removeEventListener('popstate', handlePopState);
  window.removeEventListener('blur', handleWindowBlur);
  window.removeEventListener('copy', handleCopy);
  window.removeEventListener('paste', handlePaste);
  window.removeEventListener('contextmenu', handleContextMenu);
  window.removeEventListener('offline', handleNetworkOffline);
  window.removeEventListener('online', handleNetworkOnline);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
  document.removeEventListener('fullscreenchange', handleFullscreenChange);
}

function bindHistoryGuard() {
  try {
    window.history.pushState({ smartExamGuard: props.attemptId }, '', window.location.href);
  } catch {
    // Keep beforeunload guard only.
  }
}

function handlePopState() {
  if (allowPageLeave) return;
  reportExamEvent('HISTORY_BACK_ATTEMPT');
  bindHistoryGuard();
  confirmLeaveExam();
}

function hasAnyAnswer(value: Record<number, AnswerValue>) {
  return Object.values(value || {}).some((item) => {
    if (Array.isArray(item)) return item.length > 0;
    return item !== undefined && item !== null && String(item).trim() !== '';
  });
}

function sameAnswers(a: Record<number, AnswerValue>, b: Record<number, AnswerValue>) {
  return JSON.stringify(a || {}) === JSON.stringify(b || {});
}

function stopTimers() {
  clearTimer(timer);
  clearTimer(autoSaveTimer);
  clearTimer(heartbeatTimer);
  clearTimer(monitorFlushTimer);
  clearTimer(monitorNoticeTimer);
  clearRetryTimer();
  clearSubmitRetryTimer();
}

function clearTimer(timerRef: ReturnType<typeof setInterval> | null) {
  if (timerRef) clearInterval(timerRef);
}

function clearRetryTimer() {
  if (retrySaveTimer) {
    clearTimeout(retrySaveTimer);
    retrySaveTimer = null;
  }
  draftRetryPending.value = false;
}

function clearSubmitRetryTimer() {
  if (submitRetryTimer) {
    clearTimeout(submitRetryTimer);
    submitRetryTimer = null;
  }
  submitRetryPending.value = false;
}
</script>

<style scoped>
.exam-taking-page {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: #f3f4f6;
  color: #111827;
}

.exam-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 24px;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
}

.exam-title-block span {
  color: #64748b;
  font-size: 13px;
}

.exam-title-block h1 {
  margin: 4px 0 0;
  font-size: 22px;
  font-weight: 700;
}

.exam-header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
}

.monitor-notice {
  margin: 12px 18px 0;
  width: auto;
}

.recovery-panel {
  display: grid;
  grid-template-columns: minmax(220px, 1.15fr) minmax(0, 2.4fr) auto;
  align-items: center;
  gap: 12px;
  margin: 12px 18px 0;
  padding: 12px;
  border: 1px solid #d1fae5;
  border-radius: 8px;
  background: #f0fdf4;
}

.recovery-panel.warn {
  border-color: #fde68a;
  background: #fffbeb;
}

.recovery-panel.high {
  border-color: #fecaca;
  background: #fef2f2;
}

.recovery-status {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.recovery-status > div,
.recovery-item {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.recovery-status strong,
.recovery-item strong {
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recovery-status span,
.recovery-item span,
.recovery-item small {
  color: #64748b;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recovery-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.recovery-item {
  min-height: 70px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.72);
}

.recovery-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.exam-save-pill,
.exam-timer {
  min-height: 34px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  border-radius: 18px;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 13px;
}

.exam-save-pill.failed {
  border-color: #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.exam-save-pill.saving {
  border-color: #fde68a;
  background: #fffbeb;
  color: #92400e;
}

.exam-save-pill.saved {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.exam-save-pill.recovery {
  border-color: #ddd6fe;
  background: #f5f3ff;
  color: #6d28d9;
}

.exam-timer {
  min-width: 100px;
  justify-content: center;
  border-color: #d1d5db;
  background: #111827;
  color: #ffffff;
  font-weight: 700;
}

.exam-timer.warning {
  background: #b91c1c;
}

.exam-layout {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 18px;
  padding: 18px;
}

.question-main,
.question-nav {
  min-width: 0;
}

.question-card,
.question-nav {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.question-card {
  min-height: calc(100vh - 138px);
  padding: 22px;
}

.question-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 16px;
  color: #64748b;
}

.question-stem {
  margin: 0 0 20px;
  font-size: 20px;
  line-height: 1.6;
  word-break: break-word;
}

.option-grid {
  display: grid;
  gap: 12px;
}

.option-card {
  width: 100%;
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  text-align: left;
  cursor: pointer;
}

.option-card:hover,
.option-card.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.option-label {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #e5e7eb;
  font-weight: 700;
}

.option-card.active .option-label {
  background: #2563eb;
  color: #ffffff;
}

.option-content {
  line-height: 1.6;
  word-break: break-word;
}

.question-actions {
  display: flex;
  justify-content: space-between;
  margin-top: 22px;
}

.question-nav {
  align-self: start;
  padding: 16px;
  position: sticky;
  top: 18px;
}

.nav-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 14px;
}

.nav-header span {
  color: #64748b;
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
}

.nav-item {
  aspect-ratio: 1;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #ffffff;
  color: #374151;
  font-weight: 700;
  cursor: pointer;
}

.nav-item.answered {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.nav-item.active {
  border-color: #2563eb;
  background: #2563eb;
  color: #ffffff;
}

.nav-footer {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

@media (max-width: 960px) {
  .exam-header,
  .exam-header-meta {
    align-items: flex-start;
    flex-direction: column;
  }

  .recovery-panel {
    grid-template-columns: 1fr;
  }

  .recovery-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .recovery-actions {
    flex-wrap: wrap;
  }

  .exam-layout {
    grid-template-columns: 1fr;
  }

  .question-card {
    min-height: 0;
  }

  .question-nav {
    position: static;
  }
}

@media (max-width: 560px) {
  .recovery-grid {
    grid-template-columns: 1fr;
  }

  .recovery-actions .el-button {
    width: 100%;
  }
}
</style>
