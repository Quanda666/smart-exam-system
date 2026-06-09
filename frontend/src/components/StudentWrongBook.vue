<template>
  <section class="student-wrong-page mp-page">
    <div class="mp-page-header">
      <div>
        <h3 class="mp-page-title">错题本</h3>
      </div>
      <div class="mp-page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadWrongQuestions(true)">刷新</el-button>
      </div>
    </div>

    <div class="mp-stat-grid">
      <div v-for="card in summaryCards" :key="card.label" class="mp-stat-card">
        <div class="mp-stat-header">{{ card.label }}</div>
        <div class="mp-stat-row">
          <div class="mp-stat-content">
            <div class="mp-stat-value" :class="card.className">{{ card.value }}</div>
            <div class="mp-stat-label">{{ card.remark }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="mp-table-card">
      <el-table v-loading="loading" :data="wrongQuestions" border>
        <el-table-column type="expand">
          <template #default="scope">
            <div class="wrong-expand">
              <div v-if="scope.row.options?.length" class="option-list">
                <div
                  v-for="option in scope.row.options"
                  :key="option.optionLabel"
                  :class="['option-item', { correct: isCorrectValue(option.correct ?? option.isCorrect) }]"
                >
                  <strong>{{ option.optionLabel }}.</strong>
                  <span>{{ option.optionContent }}</span>
                </div>
              </div>
              <p>正确答案：{{ scope.row.correctAnswer || '—' }}</p>
              <p v-if="scope.row.analysis">解析：{{ scope.row.analysis }}</p>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="题目" min-width="280" show-overflow-tooltip>
          <template #default="scope">
            <div class="wrong-stem">
              <strong>{{ scope.row.stem }}</strong>
              <span>{{ typeText(scope.row.questionType) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="wrongCount" label="错误次数" width="110" />
        <el-table-column label="最近错误时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.lastWrongTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="scope">
            <el-button
              link
              type="primary"
              :icon="MagicStick"
              :loading="aiExplainingQuestionId === scope.row.questionId"
              @click="aiExplainWrong(scope.row as WrongQuestion)"
            >
              AI讲解
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && wrongQuestions.length === 0" description="暂无错题" />
    </div>

    <el-dialog v-model="aiExplainVisible" :title="aiExplainTitle" width="720px" class="ai-explain-dialog">
      <el-skeleton v-if="aiExplainingQuestionId" :rows="5" animated />
      <div v-else class="ai-explain-content">{{ aiExplanation }}</div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { MagicStick, Refresh } from '@element-plus/icons-vue';
import { explainWrongQuestion } from '../api/ai';
import { getWrongQuestions, type WrongQuestion } from '../api/student';
import { formatDateTime } from '../utils/dateFormat';

const loading = ref(false);
const wrongQuestions = ref<WrongQuestion[]>([]);
const aiExplainVisible = ref(false);
const aiExplanation = ref('');
const aiExplainTitle = ref('AI错题讲解');
const aiExplainingQuestionId = ref<number | null>(null);

const summaryCards = computed(() => {
  const totalWrongTimes = wrongQuestions.value.reduce((sum, item) => sum + Number(item.wrongCount || 0), 0);
  const objectiveCount = wrongQuestions.value.filter((item) => ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(item.questionType)).length;
  const lastWrong = wrongQuestions.value[0]?.lastWrongTime ? formatDateTime(wrongQuestions.value[0].lastWrongTime) : '—';
  return [
    { label: '错题数', value: wrongQuestions.value.length, remark: '题目数量', className: 'mp-val-danger' },
    { label: '累计错误', value: totalWrongTimes, remark: '错误次数', className: 'mp-val-warn' },
    { label: '客观题', value: objectiveCount, remark: '可自动判分', className: '' },
    { label: '最近错误', value: lastWrong, remark: '最后记录', className: 'wrong-date' }
  ];
});

onMounted(() => {
  loadWrongQuestions();
});

async function loadWrongQuestions(manual = false) {
  loading.value = true;
  try {
    wrongQuestions.value = (await getWrongQuestions()).data;
    if (manual) {
      ElMessage.success('错题本已刷新');
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '错题本加载失败');
  } finally {
    loading.value = false;
  }
}

async function aiExplainWrong(item: WrongQuestion) {
  aiExplainVisible.value = true;
  aiExplanation.value = '';
  aiExplainTitle.value = `AI讲解：${item.stem.slice(0, 24)}`;
  aiExplainingQuestionId.value = item.questionId;
  try {
    const response = await explainWrongQuestion({
      questionId: item.questionId,
      stem: item.stem,
      questionType: item.questionType,
      correctAnswer: item.correctAnswer,
      analysis: item.analysis,
      wrongCount: item.wrongCount,
      options: (item.options || [])
        .filter((option) => Boolean(option.optionLabel && option.optionContent))
        .map((option) => ({
          optionLabel: option.optionLabel,
          optionContent: option.optionContent,
          correct: isCorrectValue(option.correct ?? option.isCorrect)
        }))
    });
    aiExplanation.value = response.data;
  } catch (error) {
    aiExplainVisible.value = false;
    ElMessage.error(error instanceof Error ? error.message : 'AI讲解请求失败');
  } finally {
    aiExplainingQuestionId.value = null;
  }
}

function isCorrectValue(value: boolean | number | undefined) {
  return value === true || value === 1;
}

function typeText(type?: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SUBJECTIVE: '主观题'
  };
  return type ? map[type] || type : '未知题型';
}
</script>

<style scoped>
.wrong-stem {
  display: grid;
  gap: 4px;
}

.wrong-stem span {
  color: #64748b;
  font-size: 12px;
}

.wrong-expand {
  display: grid;
  gap: 10px;
  padding: 10px 22px;
  color: #374151;
}

.wrong-expand p {
  margin: 0;
  line-height: 1.7;
}

.option-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
}

.option-item {
  display: flex;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.option-item.correct {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}

.ai-explain-content {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #1f2937;
}

.wrong-date {
  font-size: 16px;
}
</style>
