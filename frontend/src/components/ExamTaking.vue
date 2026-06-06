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
        <div class="nav-footer">
          <el-button type="primary" @click="prevQuestion" :disabled="currentQuestionIndex === 0">上一题</el-button>
          <el-button type="primary" @click="nextQuestion" :disabled="currentQuestionIndex === (examDetails?.questions?.length || 0) - 1">下一题</el-button>
          <el-button type="success" @click="confirmSubmit">交卷</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { startExam, submitExam, type ExamDetail } from '../api/exam';
import type { PaperQuestionInfo as QuestionInExam } from '../api/paper';
import { ElMessage, ElMessageBox } from 'element-plus';
import { AlarmClock } from '@element-plus/icons-vue';

const props = defineProps<{
  attemptId: number;
}>();

const emit = defineEmits(['submit-success']);

const examDetails = ref<ExamDetail | null>(null);
const currentQuestionIndex = ref(0);
const answers = ref<Record<number, any>>({});
const timeLeft = ref(0);
let timer: any;

const currentQuestion = computed(() => examDetails.value?.questions[currentQuestionIndex.value]);

const formattedTime = computed(() => {
  const minutes = Math.floor(timeLeft.value / 60);
  const seconds = timeLeft.value % 60;
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
});

onMounted(async () => {
  try {
    const response = await startExam(props.attemptId);
    examDetails.value = response.data;
    timeLeft.value = (examDetails.value?.durationMinutes || 0) * 60;
    startTimer();
  } catch (error) {
    ElMessage.error('进入考试失败，可能考试已结束或您已参加过');
  }
});

onUnmounted(() => {
  clearInterval(timer);
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
  try {
    const finalAnswers = { ...answers.value };
    // Process checkbox answers
     examDetails.value?.questions?.forEach(q => {
      if (q.questionType === 'MULTIPLE_CHOICE' && Array.isArray(finalAnswers[q.questionId])) {
        finalAnswers[q.questionId] = (finalAnswers[q.questionId] as string[]).sort().join('');
      }
    });
    
    await submitExam(props.attemptId, { answers: finalAnswers });
    if(autoSubmit) {
        ElMessage.warning('考试时间到，已自动为您交卷！');
    } else {
        ElMessage.success('交卷成功！');
    }
    emit('submit-success');
  } catch (error) {
    ElMessage.error('交卷失败');
  }
};

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
