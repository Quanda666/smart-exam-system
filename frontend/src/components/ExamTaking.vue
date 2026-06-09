<template>
  <div class="exam-taking-page">
    <div class="exam-header">
      <div class="exam-title-block">
        <span>考试作答</span>
        <h1>{{ examDetails?.examName || '考试' }}</h1>
      </div>
      <div class="exam-header-meta">
        <div :class="['exam-save-pill', saveState]">{{ saveStateText }}</div>
        <div :class="['exam-timer', { warning: timeLeft <= 300 }]">
          <el-icon><AlarmClock /></el-icon>
          <span>{{ formattedTime }}</span>
        </div>
        <el-button plain :icon="Close" @click="confirmLeaveExam">退出考试</el-button>
      </div>
    </div>
    <div class="exam-layout">
      <div class="question-main">
        <div v-if="currentQuestion" class="question-card">
          <div class="question-meta">
            <el-tag>{{ questionTypeText(currentQuestion.questionType) }}</el-tag>
            <el-tag type="info">{{ difficultyText(currentQuestion.difficulty) }}</el-tag>
            <span>{{ currentQuestion.score }} 分</span>
          </div>
          <h2 class="question-stem">{{ currentQuestionIndex + 1 }}. {{ currentQuestion.stem }}</h2>
          <div v-if="isObjective(currentQuestion.questionType)">
            <div class="option-grid">
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
          </div>
          <div v-else>
            <el-input
              type="textarea"
              :rows="10"
              v-model="answers[currentQuestionId]"
              placeholder="请输入你的答案"
            />
          </div>
          <div class="question-actions">
            <el-button @click="prevQuestion" :disabled="currentQuestionIndex === 0">上一题</el-button>
            <el-button type="primary" @click="nextQuestion" :disabled="currentQuestionIndex === (examDetails?.questions?.length || 0) - 1">下一题</el-button>
          </div>
        </div>
      </div>
      <div class="question-nav">
        <div class="nav-header">
          <strong>题目导航</strong>
          <span>{{ answeredCount }} / {{ examDetails?.questions?.length || 0 }}</span>
        </div>
        <div class="nav-grid">
          <button
            v-for="(question, index) in examDetails?.questions"
            :key="questionKey(question)"
            :class="['nav-item', { active: index === currentQuestionIndex, answered: hasAnswer(question) }]"
            @click="currentQuestionIndex = index"
          >
            {{ index + 1 }}
          </button>
        </div>
        <div class="nav-footer">
          <el-button type="primary" @click="prevQuestion" :disabled="currentQuestionIndex === 0">上一题</el-button>
          <el-button type="primary" @click="nextQuestion" :disabled="currentQuestionIndex === (examDetails?.questions?.length || 0) - 1">下一题</el-button>
          <el-button type="primary" plain :loading="saveState === 'saving'" @click="persistDraft(true)">保存草稿</el-button>
          <el-button type="success" @click="confirmSubmit">交卷</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { startExam, submitExam, saveExamDraft, type ExamDetail } from '../api/exam';
import { ElMessage, ElMessageBox } from 'element-plus';
import { AlarmClock, Close } from '@element-plus/icons-vue';

const props = defineProps<{
  attemptId: number;
}>();

const emit = defineEmits<{
  (e: 'submit-success'): void;
  (e: 'leave-exam'): void;
}>();
interface LocalExamDraft {
  answers: Record<number, any>;
  savedAt: string;
  examName?: string;
}
type ExamQuestion = ExamDetail['questions'][number];

const examDetails = ref<ExamDetail | null>(null);
const currentQuestionIndex = ref(0);
const answers = ref<Record<number, any>>({});
const timeLeft = ref(0);
const saveState = ref<'idle' | 'saving' | 'saved' | 'failed'>('idle');
const lastSavedAt = ref('');
const hasUnsavedChanges = ref(false);
let timer: any;
let autoSaveTimer: any;
let retrySaveTimer: any;
let draftHydrated = false;
let allowPageLeave = false;
let leaveConfirmOpen = false;

const currentQuestion = computed(() => examDetails.value?.questions[currentQuestionIndex.value]);
const currentQuestionId = computed(() => currentQuestion.value ? questionKey(currentQuestion.value) : 0);
const localDraftKey = computed(() => `smart_exam_local_draft_${props.attemptId}`);
const answeredCount = computed(() => (examDetails.value?.questions || []).filter((question) => hasAnswer(question)).length);

const formattedTime = computed(() => {
  const minutes = Math.floor(timeLeft.value / 60);
  const seconds = timeLeft.value % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
});

const saveStateText = computed(() => {
  if (saveState.value === 'saving') {
    return '正在保存草稿...';
  }
  if (saveState.value === 'saved') {
    return lastSavedAt.value ? `草稿已保存 ${lastSavedAt.value}` : '草稿已保存';
  }
  if (saveState.value === 'failed') {
    return '草稿保存失败，网络恢复后会自动重试';
  }
  return hasUnsavedChanges.value ? '有未保存作答' : '草稿自动保存已开启';
});

watch(answers, () => {
  if (!draftHydrated) {
    return;
  }
  hasUnsavedChanges.value = true;
  writeLocalDraft();
  if (saveState.value === 'saved') {
    saveState.value = 'idle';
  }
}, { deep: true });

onMounted(async () => {
  try {
    const response = await startExam(props.attemptId);
    examDetails.value = response.data;
    // 恢复已暂存的草稿答案（刷新/断线重进后不丢作答）
    if (response.data.draftAnswers) {
      try {
        answers.value = JSON.parse(response.data.draftAnswers);
      } catch (e) {
        // 草稿解析失败则忽略，按空白开始
      }
    }
    await restoreLocalDraftIfNeeded();
    draftHydrated = true;
    // 优先使用服务端剩余时间（基于开始时间计算），刷新不重置、也无法靠刷新刷时长
    timeLeft.value = response.data.remainingSeconds != null
      ? response.data.remainingSeconds
      : (examDetails.value?.durationMinutes || 0) * 60;
    startTimer();
    startAutoSave();
    bindHistoryGuard();
    window.addEventListener('beforeunload', handleBeforeUnload);
    window.addEventListener('popstate', handlePopState);
    document.addEventListener('visibilitychange', handleVisibilityChange);
  } catch (error) {
    ElMessage.error('进入考试失败，可能考试已结束或您已参加过');
    allowPageLeave = true;
    emit('leave-exam');
  }
});

onUnmounted(() => {
  clearInterval(timer);
  clearInterval(autoSaveTimer);
  clearTimeout(retrySaveTimer);
  window.removeEventListener('beforeunload', handleBeforeUnload);
  window.removeEventListener('popstate', handlePopState);
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});

const startTimer = () => {
  timer = setInterval(() => {
    if (timeLeft.value > 0) {
      timeLeft.value--;
    } else {
      clearInterval(timer);
      submit(true);
    }
  }, 1000);
};

const startAutoSave = () => {
  autoSaveTimer = setInterval(() => {
    persistDraft(false);
  }, 20000);
};

const persistDraft = async (manual: boolean) => {
  if (!examDetails.value) {
    return;
  }
  writeLocalDraft();
  clearTimeout(retrySaveTimer);
  saveState.value = 'saving';
  try {
    await saveExamDraft(props.attemptId, JSON.stringify(answers.value));
    hasUnsavedChanges.value = false;
    saveState.value = 'saved';
    lastSavedAt.value = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    if (manual) {
      ElMessage.success('草稿已保存');
    }
  } catch (error) {
    saveState.value = 'failed';
    hasUnsavedChanges.value = true;
    writeLocalDraft();
    retrySaveTimer = setTimeout(() => {
      persistDraft(false);
    }, 5000);
    if (manual) {
      ElMessage.error('草稿保存失败，稍后会自动重试');
    }
  }
};

const handleBeforeUnload = (event: BeforeUnloadEvent) => {
  if (allowPageLeave) {
    return;
  }
  event.preventDefault();
  event.returnValue = '';
};

const handleVisibilityChange = () => {
  if (document.visibilityState === 'hidden' && hasUnsavedChanges.value) {
    persistDraft(false);
  }
};

const isObjective = (type: string | undefined) => !!type && ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type);

function questionKey(question: Partial<ExamQuestion>) {
  return Number(question.questionId ?? question.id ?? 0);
}

function hasAnswer(question: Partial<ExamQuestion>) {
  const value = answers.value[questionKey(question)];
  if (Array.isArray(value)) {
    return value.length > 0;
  }
  return value !== undefined && value !== null && String(value).trim() !== '';
}

function isOptionSelected(label: string) {
  const value = answers.value[currentQuestionId.value];
  if (Array.isArray(value)) {
    return value.includes(label);
  }
  return value === label;
}

function toggleOption(label: string) {
  if (!currentQuestion.value) {
    return;
  }
  const key = currentQuestionId.value;
  if (currentQuestion.value.questionType === 'MULTIPLE_CHOICE') {
    const current = Array.isArray(answers.value[key]) ? [...answers.value[key]] : [];
    answers.value[key] = current.includes(label)
      ? current.filter((item) => item !== label)
      : [...current, label];
  } else {
    answers.value[key] = label;
  }
}

function questionTypeText(type?: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SUBJECTIVE: '主观题'
  };
  return type ? map[type] || type : '未知题型';
}

function difficultyText(value?: string) {
  const map: Record<string, string> = {
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难'
  };
  return value ? map[value] || value : '未设难度';
}

const prevQuestion = () => {
  if (currentQuestionIndex.value > 0) {
    currentQuestionIndex.value--;
  }
};

const nextQuestion = () => {
  if (examDetails.value && currentQuestionIndex.value < examDetails.value.questions.length - 1) {
    currentQuestionIndex.value++;
  }
};

const confirmSubmit = () => {
  ElMessageBox.confirm('确认要交卷吗？交卷后无法修改。', '提示', {
    confirmButtonText: '确认交卷',
    cancelButtonText: '继续答题',
    type: 'warning',
  })
    .then(() => {
      submit(false);
    })
    .catch(() => {});
};

const confirmLeaveExam = async () => {
  if (leaveConfirmOpen) {
    return;
  }
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
    allowPageLeave = true;
    emit('leave-exam');
  } catch {
    // 继续考试。
  } finally {
    leaveConfirmOpen = false;
  }
};

const submit = async (autoSubmit: boolean) => {
  clearInterval(timer);
  clearInterval(autoSaveTimer);
  clearTimeout(retrySaveTimer);
  try {
    const finalAnswers = { ...answers.value };
    // Process checkbox answers
     examDetails.value?.questions?.forEach(q => {
      const questionId = Number((q as any).questionId ?? q.id);
      if (q.questionType === 'MULTIPLE_CHOICE' && Array.isArray(finalAnswers[questionId])) {
        finalAnswers[questionId] = (finalAnswers[questionId] as string[]).sort().join('');
      }
    });
    
    await submitExam(props.attemptId, { answers: finalAnswers });
    allowPageLeave = true;
    clearLocalDraft();
    if(autoSubmit) {
        ElMessage.warning('考试时间到，已自动为您交卷！');
    } else {
        ElMessage.success('交卷成功！');
    }
    emit('submit-success');
  } catch (error) {
    ElMessage.error('交卷失败');
    if (!autoSubmit && timeLeft.value > 0) {
      startTimer();
      startAutoSave();
    }
  }
};

async function restoreLocalDraftIfNeeded() {
  const localDraft = readLocalDraft();
  if (!localDraft || !hasAnyAnswer(localDraft.answers)) {
    return;
  }
  if (sameAnswers(localDraft.answers, answers.value)) {
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
    answers.value = localDraft.answers;
    hasUnsavedChanges.value = true;
    saveState.value = 'idle';
    ElMessage.success('已恢复本机草稿');
  } catch {
    writeLocalDraft();
  }
}

function readLocalDraft(): LocalExamDraft | null {
  try {
    const raw = localStorage.getItem(localDraftKey.value);
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as LocalExamDraft;
    return parsed && parsed.answers ? parsed : null;
  } catch {
    return null;
  }
}

function writeLocalDraft() {
  try {
    localStorage.setItem(localDraftKey.value, JSON.stringify({
      answers: answers.value,
      savedAt: new Date().toISOString(),
      examName: examDetails.value?.examName
    }));
  } catch {
    // 浏览器存储不可用时，继续依赖服务端草稿。
  }
}

function clearLocalDraft() {
  try {
    localStorage.removeItem(localDraftKey.value);
  } catch {
    // 忽略浏览器存储异常。
  }
}

function bindHistoryGuard() {
  try {
    window.history.pushState({ smartExamGuard: props.attemptId }, '', window.location.href);
  } catch {
    // History API 不可用时，仅保留刷新/关闭提醒。
  }
}

function handlePopState() {
  if (allowPageLeave) {
    return;
  }
  bindHistoryGuard();
  confirmLeaveExam();
}

function hasAnyAnswer(value: Record<number, any>) {
  return Object.values(value || {}).some((item) => {
    if (Array.isArray(item)) {
      return item.length > 0;
    }
    return item !== undefined && item !== null && String(item).trim() !== '';
  });
}

function sameAnswers(a: Record<number, any>, b: Record<number, any>) {
  return JSON.stringify(a || {}) === JSON.stringify(b || {});
}

</script>

<style scoped>
.exam-taking-page {
  display: flex;
  flex-direction: column;
  position: fixed;
  inset: 0;
  z-index: 2000;
  width: 100vw;
  height: 100dvh;
  box-sizing: border-box;
  background: #f5f7fb;
  color: #111827;
  overflow: hidden;
}
.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 18px 28px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.04);
}
.exam-title-block {
  min-width: 0;
}
.exam-title-block span {
  display: block;
  color: #64748b;
  font-size: 12px;
  margin-bottom: 4px;
}
.exam-title-block h1 {
  margin: 0;
  font-size: 22px;
  line-height: 1.25;
  overflow-wrap: anywhere;
}
.exam-header-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  justify-content: flex-end;
}
.exam-timer {
  min-width: 132px;
  height: 42px;
  padding: 0 14px;
  border-radius: 8px;
  background: #111827;
  color: #ffffff;
  font-size: 18px;
  font-weight: bold;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
.exam-timer.warning {
  background: #dc2626;
}
.exam-timer .el-icon {
  margin-right: 0;
}
.exam-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
  flex: 1;
  min-height: 0;
  padding: 20px 28px;
}
.question-main {
  min-width: 0;
  min-height: 0;
  overflow: auto;
}
.question-card {
  min-height: 100%;
  padding: 26px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.04);
  box-sizing: border-box;
}
.question-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.question-meta span {
  color: #64748b;
  font-size: 13px;
}
.question-stem {
  margin: 0 0 24px;
  font-size: 22px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.option-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
  max-width: 860px;
}
.option-card {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr);
  gap: 12px;
  align-items: flex-start;
  min-height: 64px;
  padding: 14px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background: #ffffff;
  color: #1f2937;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s, background 0.18s, box-shadow 0.18s;
}
.option-card:hover {
  border-color: #409eff;
  background: #f8fbff;
}
.option-card.active {
  border-color: #2563eb;
  background: #eff6ff;
  box-shadow: inset 0 0 0 1px #2563eb;
}
.option-label {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e5e7eb;
  color: #374151;
  font-weight: 700;
}
.option-card.active .option-label {
  background: #2563eb;
  color: #ffffff;
}
.option-content {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.question-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}
.question-nav {
  display: flex;
  flex-direction: column;
  min-height: 0;
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.04);
}
.nav-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: #111827;
}
.nav-header span {
  color: #64748b;
  font-size: 13px;
}
.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(44px, 1fr));
  gap: 10px;
  align-content: start;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 2px;
}
.exam-save-pill {
  max-width: min(420px, 48vw);
  padding: 9px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.exam-save-pill.saving {
  background: #ecf5ff;
  color: #337ecc;
  border-color: #bfdbfe;
}
.exam-save-pill.saved {
  background: #f0f9eb;
  color: #529b2e;
  border-color: #bbf7d0;
}
.exam-save-pill.failed {
  background: #fef0f0;
  color: #c45656;
  border-color: #fecaca;
}
.nav-item {
  width: 44px;
  height: 44px;
  border: 1px solid #dbe3ef;
  border-radius: 8px;
  background-color: #fff;
  cursor: pointer;
  display: flex;
  justify-content: center;
  align-items: center;
  font-weight: 700;
  color: #475569;
}
.nav-item.active {
  border-color: #2563eb;
  color: #2563eb;
  box-shadow: inset 0 0 0 1px #2563eb;
}
.nav-item.answered {
  background-color: #16a34a;
  border-color: #16a34a;
  color: #fff;
}
.nav-footer {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 18px;
}
.nav-footer .el-button {
  margin-left: 0;
}
.nav-footer .el-button:nth-last-child(-n + 2) {
  grid-column: span 1;
}

@media (max-width: 980px) {
  .exam-taking-page {
    overflow: auto;
  }

  .exam-header {
    align-items: flex-start;
    padding: 16px;
    flex-direction: column;
  }

  .exam-header-meta {
    width: 100%;
    justify-content: space-between;
  }

  .exam-save-pill {
    max-width: 100%;
  }

  .exam-layout {
    grid-template-columns: 1fr;
    padding: 16px;
  }

  .question-card {
    min-height: auto;
    padding: 18px;
  }

  .question-nav {
    min-height: 280px;
  }
}

@media (max-width: 640px) {
  .exam-title-block h1 {
    font-size: 18px;
  }

  .question-stem {
    font-size: 18px;
  }

  .option-grid {
    grid-template-columns: 1fr;
  }

  .nav-footer {
    grid-template-columns: 1fr;
  }
}
</style>
