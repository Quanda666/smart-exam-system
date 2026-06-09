<template>
  <div class="exam-taking-page">
    <div class="exam-header">
      <h1>{{ examDetails?.examName }}</h1>
      <div class="exam-timer">
        <el-icon><AlarmClock /></el-icon>
        <span>剩余时间：{{ formattedTime }}</span>
      </div>
    </div>
    <div class="exam-layout">
      <div class="question-main">
        <div v-if="currentQuestion" class="question-content">
          <p class="question-stem">
            <strong>{{ currentQuestionIndex + 1 }}. {{ currentQuestion.stem }}</strong>
            <el-tag size="small">{{ currentQuestion.questionType }}</el-tag>
          </p>
          <div v-if="isObjective(currentQuestion.questionType)">
            <el-radio-group v-if="currentQuestion.questionType === 'SINGLE_CHOICE' || currentQuestion.questionType === 'TRUE_FALSE'" v-model="answers[currentQuestion.id as number]">
              <el-radio v-for="option in (currentQuestion.options as any)" :key="option.optionLabel" :label="option.optionLabel">
                {{ option.optionLabel }}. {{ option.optionContent }}
              </el-radio>
            </el-radio-group>
            <el-checkbox-group v-else-if="currentQuestion.questionType === 'MULTIPLE_CHOICE'" v-model="answers[currentQuestion.id as number]">
              <el-checkbox v-for="option in (currentQuestion.options as any)" :key="option.optionLabel" :label="option.optionLabel">
                {{ option.optionLabel }}. {{ option.optionContent }}
              </el-checkbox>
            </el-checkbox-group>
          </div>
          <div v-else>
            <el-input
              type="textarea"
              :rows="8"
              v-model="answers[currentQuestion.id as number]"
              placeholder="请输入你的答案"
            />
          </div>
        </div>
      </div>
      <div class="question-nav">
        <div class="nav-header">题目导航</div>
        <div class="nav-grid">
          <button
            v-for="(question, index) in examDetails?.questions"
            :key="question.id"
            :class="['nav-item', { active: index === currentQuestionIndex, answered: !!answers[question.id as number] }]"
            @click="currentQuestionIndex = index"
          >
            {{ index + 1 }}
          </button>
        </div>
        <div :class="['save-status', saveState]">
          {{ saveStateText }}
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
import type { PaperQuestionInfo as QuestionInExam } from '../api/paper';
import { ElMessage, ElMessageBox } from 'element-plus';
import { AlarmClock } from '@element-plus/icons-vue';

const props = defineProps<{
  attemptId: number;
}>();

const emit = defineEmits(['submit-success']);
interface LocalExamDraft {
  answers: Record<number, any>;
  savedAt: string;
  examName?: string;
}

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

const currentQuestion = computed(() => examDetails.value?.questions[currentQuestionIndex.value]);
const localDraftKey = computed(() => `smart_exam_local_draft_${props.attemptId}`);

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
    window.addEventListener('beforeunload', handleBeforeUnload);
    document.addEventListener('visibilitychange', handleVisibilityChange);
  } catch (error) {
    ElMessage.error('进入考试失败，可能考试已结束或您已参加过');
  }
});

onUnmounted(() => {
  clearInterval(timer);
  clearInterval(autoSaveTimer);
  clearTimeout(retrySaveTimer);
  window.removeEventListener('beforeunload', handleBeforeUnload);
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
  if (!hasUnsavedChanges.value) {
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
  height: 100vh;
  padding: 20px;
  box-sizing: border-box;
}
.exam-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #ddd;
  padding-bottom: 10px;
}
.exam-timer {
  font-size: 1.2em;
  font-weight: bold;
  display: flex;
  align-items: center;
}
.exam-timer .el-icon {
  margin-right: 8px;
}
.exam-layout {
  display: flex;
  flex-grow: 1;
  margin-top: 20px;
}
.question-main {
  flex-grow: 1;
  padding-right: 20px;
}
.question-stem {
  font-size: 1.1em;
  margin-bottom: 20px;
}
.question-nav {
  width: 280px;
  border-left: 1px solid #ddd;
  padding-left: 20px;
  display: flex;
  flex-direction: column;
}
.nav-header {
  font-weight: bold;
  margin-bottom: 10px;
}
.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(40px, 1fr));
  gap: 10px;
  margin-bottom: auto;
}
.save-status {
  margin: 14px 0;
  padding: 8px 10px;
  border-radius: 6px;
  background: #f4f7fb;
  color: #5f6b7a;
  font-size: 13px;
  line-height: 1.4;
}
.save-status.saving {
  background: #ecf5ff;
  color: #337ecc;
}
.save-status.saved {
  background: #f0f9eb;
  color: #529b2e;
}
.save-status.failed {
  background: #fef0f0;
  color: #c45656;
}
.nav-item {
  width: 40px;
  height: 40px;
  border: 1px solid #ddd;
  background-color: #fff;
  cursor: pointer;
  display: flex;
  justify-content: center;
  align-items: center;
}
.nav-item.active {
  border-color: #409eff;
  color: #409eff;
}
.nav-item.answered {
  background-color: #67c23a;
  color: #fff;
}
.nav-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 20px;
}
</style>
