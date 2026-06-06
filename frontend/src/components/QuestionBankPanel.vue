<template>
  <section class="question-panel">
    <div class="question-summary-grid">
      <div v-for="card in summaryCards" :key="card.label" class="question-summary-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.remark }}</small>
      </div>
    </div>

    <el-card shadow="never" class="question-workbench">
      <template #header>
        <div class="card-header">
          <span>题库筛选</span>
          <el-tag type="success">阶段 4</el-tag>
        </div>
      </template>

      <div class="question-toolbar">
        <el-input v-model="query.keyword" placeholder="按题干、科目、知识点搜索" clearable @keyup.enter="loadQuestions" />
        <el-select v-model="query.subjectId" placeholder="科目" clearable @change="handleQuerySubjectChange">
          <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
        </el-select>
        <el-select v-model="query.knowledgePointId" placeholder="知识点" clearable>
          <el-option v-for="point in queryKnowledgePoints" :key="point.id" :label="point.pointName" :value="point.id" />
        </el-select>
        <el-select v-model="query.questionType" placeholder="题型" clearable>
          <el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.difficulty" placeholder="难度" clearable>
          <el-option v-for="item in difficulties" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable>
          <el-option label="已发布" :value="1" />
          <el-option label="草稿" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadQuestions">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </el-card>

    <el-card v-if="canManageQuestions" shadow="never" class="question-workbench">
      <template #header>
        <div class="card-header">
          <span>{{ editingQuestionId ? '编辑题目' : '新增题目' }}</span>
          <el-tag>{{ typeText(questionForm.questionType) }}</el-tag>
        </div>
      </template>

      <el-form class="question-form" :model="questionForm" label-position="top">
        <el-form-item label="所属科目">
          <el-select v-model="questionForm.subjectId" placeholder="请选择科目" @change="handleFormSubjectChange">
            <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点">
          <el-select v-model="questionForm.knowledgePointId" placeholder="可选知识点" clearable>
            <el-option v-for="point in formKnowledgePoints" :key="point.id" :label="point.pointName" :value="point.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="questionForm.questionType" @change="handleQuestionTypeChange">
            <el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="questionForm.difficulty">
            <el-option v-for="item in difficulties" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认分值">
          <el-input-number v-model="questionForm.defaultScore" :min="0.5" :step="0.5" :precision="1" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="questionForm.status">
            <el-option label="草稿" :value="0" />
            <el-option label="发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item class="question-stem" label="题干">
          <el-input v-model="questionForm.stem" type="textarea" :rows="3" placeholder="请输入题干内容" />
        </el-form-item>

        <div v-if="isObjectiveType(questionForm.questionType)" class="option-editor">
          <div class="option-editor-header">
            <strong>题目选项</strong>
            <el-button size="small" @click="addOption">新增选项</el-button>
          </div>
          <div v-for="(option, index) in questionForm.options" :key="`${option.optionLabel}-${index}`" class="option-row">
            <el-input v-model="option.optionLabel" placeholder="标识" maxlength="4" />
            <el-input v-model="option.optionContent" placeholder="选项内容" />
            <el-checkbox v-model="option.correct">正确答案</el-checkbox>
            <el-button link type="danger" @click="removeOption(index)">删除</el-button>
          </div>
        </div>

        <el-form-item v-else class="question-stem" label="参考答案">
          <el-input v-model="questionForm.correctAnswer" type="textarea" :rows="2" placeholder="请输入填空题或主观题参考答案" />
        </el-form-item>

        <el-form-item class="question-stem" label="题目解析">
          <el-input v-model="questionForm.analysis" type="textarea" :rows="2" placeholder="请输入题目解析，便于成绩发布后反馈给学生" />
        </el-form-item>

        <el-form-item class="question-form-actions" label="操作">
          <el-button type="primary" @click="saveQuestion">{{ editingQuestionId ? '保存修改' : '新增题目' }}</el-button>
          <el-button @click="resetQuestionForm">重置</el-button>
          <el-button @click="aiGenerateQuestion" type="success" plain>AI辅助出题</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="question-workbench">
      <template #header>
        <div class="card-header">
          <span>题目列表</span>
          <el-tag>{{ questions.length }} 题</el-tag>
        </div>
      </template>

      <el-table :data="questions" border class="question-table">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="题干" min-width="260" show-overflow-tooltip>
          <template #default="scope">
            <div class="stem-cell">
              <strong>{{ scope.row.stem }}</strong>
              <small>{{ scope.row.subjectName }} / {{ scope.row.knowledgePointName || '未指定知识点' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="题型" width="120">
          <template #default="scope">
            <el-tag>{{ typeText(scope.row.questionType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="难度" width="100">
          <template #default="scope">
            <el-tag :type="difficultyTag(scope.row.difficulty)">{{ difficultyText(scope.row.difficulty) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="defaultScore" label="分值" width="90" />
        <el-table-column label="答案/选项" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <div v-if="isObjectiveType(scope.row.questionType)" class="option-tags">
              <el-tag v-for="option in scope.row.options" :key="option.optionLabel" :type="isCorrectOption(option.correct) ? 'success' : 'info'">
                {{ option.optionLabel }}.{{ option.optionContent }}
              </el-tag>
            </div>
            <span v-else>{{ scope.row.correctAnswer || '未填写' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="120" />
        <el-table-column v-if="canManageQuestions" label="操作" width="230" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="editQuestion(scope.row as QuestionInfo)">编辑</el-button>
            <el-button link type="warning" @click="toggleQuestionStatus(scope.row as QuestionInfo)">
              {{ scope.row.status === 1 ? '撤回' : '发布' }}
            </el-button>
            <el-button link type="danger" @click="removeQuestion(Number(scope.row.id))">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listKnowledgePoints, listSubjects, type KnowledgePointInfo, type SubjectInfo } from '../api/basic';
import {
  createQuestion,
  deleteQuestion,
  fetchQuestionSummary,
  listQuestions,
  updateQuestion,
  updateQuestionStatus,
  type Difficulty,
  type QuestionInfo,
  type QuestionOption,
  type QuestionPayload,
  type QuestionType
} from '../api/question';
import { generateQuestion } from '../api/ai';
import type { RoleCode } from '../api/auth';

const props = defineProps<{
  role: RoleCode;
}>();

const questionTypes: Array<{ label: string; value: QuestionType }> = [
  { label: '单选题', value: 'SINGLE_CHOICE' },
  { label: '多选题', value: 'MULTIPLE_CHOICE' },
  { label: '判断题', value: 'TRUE_FALSE' },
  { label: '填空题', value: 'FILL_BLANK' },
  { label: '主观题', value: 'SUBJECTIVE' }
];

const difficulties: Array<{ label: string; value: Difficulty }> = [
  { label: '简单', value: 'EASY' },
  { label: '中等', value: 'MEDIUM' },
  { label: '困难', value: 'HARD' }
];

const subjects = ref<SubjectInfo[]>([]);
const knowledgePoints = ref<KnowledgePointInfo[]>([]);
const questions = ref<QuestionInfo[]>([]);
const summary = ref({ total: 0, published: 0, draft: 0, types: {}, difficulties: {} });
const editingQuestionId = ref<number | null>(null);

const query = reactive<{
  keyword: string;
  subjectId: number | null;
  knowledgePointId: number | null;
  questionType: QuestionType | null;
  difficulty: Difficulty | null;
  status: number | null;
}>({
  keyword: '',
  subjectId: null,
  knowledgePointId: null,
  questionType: null,
  difficulty: null,
  status: null
});

const questionForm = reactive<QuestionPayload>({
  subjectId: 0,
  knowledgePointId: null,
  questionType: 'SINGLE_CHOICE',
  difficulty: 'EASY',
  stem: '',
  correctAnswer: '',
  analysis: '',
  defaultScore: 5,
  status: 0,
  options: defaultOptions('SINGLE_CHOICE')
});

const canManageQuestions = computed(() => props.role === 'ADMIN' || props.role === 'TEACHER');

const summaryCards = computed(() => [
  { label: '题目总数', value: summary.value.total || 0, remark: '题库累计题目' },
  { label: '已发布', value: summary.value.published || 0, remark: '可供后续组卷引用' },
  { label: '草稿', value: summary.value.draft || 0, remark: '待教师确认完善' },
  { label: '题型覆盖', value: Object.keys(summary.value.types || {}).length, remark: '支持五类核心题型' }
]);

const queryKnowledgePoints = computed(() => {
  if (!query.subjectId) return knowledgePoints.value;
  return knowledgePoints.value.filter((point) => point.subjectId === query.subjectId);
});

const formKnowledgePoints = computed(() => knowledgePoints.value.filter((point) => point.subjectId === questionForm.subjectId));

onMounted(async () => {
  await loadBootstrapData();
});

async function loadBootstrapData() {
  await Promise.all([loadSubjects(), loadKnowledgePoints(), loadQuestions(), loadSummary()]);
  if (!questionForm.subjectId && subjects.value.length > 0) {
    questionForm.subjectId = subjects.value[0].id;
  }
}

async function loadSubjects() {
  const response = await listSubjects({ status: 1 });
  subjects.value = response.data;
}

async function loadKnowledgePoints() {
  const response = await listKnowledgePoints({ status: 1 });
  knowledgePoints.value = response.data;
}

async function loadQuestions() {
  try {
    const response = await listQuestions({ ...query });
    questions.value = response.data;
    await loadSummary();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '题库加载失败');
  }
}

async function loadSummary() {
  try {
    const response = await fetchQuestionSummary();
    summary.value = response.data;
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '题库统计加载失败');
  }
}

function resetQuery() {
  query.keyword = '';
  query.subjectId = null;
  query.knowledgePointId = null;
  query.questionType = null;
  query.difficulty = null;
  query.status = null;
  loadQuestions();
}

function handleQuerySubjectChange() {
  query.knowledgePointId = null;
}

function handleFormSubjectChange() {
  questionForm.knowledgePointId = null;
}

function handleQuestionTypeChange() {
  questionForm.correctAnswer = '';
  questionForm.options = defaultOptions(questionForm.questionType);
}

function defaultOptions(type: QuestionType): QuestionOption[] {
  if (type === 'TRUE_FALSE') {
    return [
      { optionLabel: 'A', optionContent: '正确', correct: true },
      { optionLabel: 'B', optionContent: '错误', correct: false }
    ];
  }
  if (type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE') {
    return ['A', 'B', 'C', 'D'].map((label) => ({ optionLabel: label, optionContent: '', correct: false }));
  }
  return [];
}

function addOption() {
  const nextLabel = String.fromCharCode(65 + questionForm.options.length);
  questionForm.options.push({ optionLabel: nextLabel, optionContent: '', correct: false });
}

function removeOption(index: number) {
  questionForm.options.splice(index, 1);
}

async function saveQuestion() {
  const payload = buildPayload();
  if (!payload) return;
  try {
    if (editingQuestionId.value) {
      await updateQuestion(editingQuestionId.value, payload);
      ElMessage.success('题目已更新');
    } else {
      await createQuestion(payload);
      ElMessage.success('题目已新增');
    }
    resetQuestionForm();
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '题目保存失败');
  }
}

function buildPayload(): QuestionPayload | null {
  if (!questionForm.subjectId) {
    ElMessage.warning('请选择所属科目');
    return null;
  }
  if (!questionForm.stem.trim()) {
    ElMessage.warning('请输入题干');
    return null;
  }

  const payload: QuestionPayload = {
    ...questionForm,
    stem: questionForm.stem.trim(),
    correctAnswer: questionForm.correctAnswer.trim(),
    analysis: questionForm.analysis.trim(),
    options: questionForm.options.map((option) => ({
      optionLabel: option.optionLabel.trim(),
      optionContent: option.optionContent.trim(),
      correct: Boolean(option.correct)
    }))
  };

  if (isObjectiveType(payload.questionType)) {
    if (payload.options.length === 0 || payload.options.some((option) => !option.optionLabel || !option.optionContent)) {
      ElMessage.warning('客观题必须填写完整选项');
      return null;
    }
    const correctLabels = payload.options.filter((option) => Boolean(option.correct)).map((option) => option.optionLabel);
    if (payload.questionType === 'SINGLE_CHOICE' && correctLabels.length !== 1) {
      ElMessage.warning('单选题必须且只能设置一个正确选项');
      return null;
    }
    if (payload.questionType === 'MULTIPLE_CHOICE' && correctLabels.length < 2) {
      ElMessage.warning('多选题至少设置两个正确选项');
      return null;
    }
    if (payload.questionType === 'TRUE_FALSE' && correctLabels.length !== 1) {
      ElMessage.warning('判断题必须且只能设置一个正确选项');
      return null;
    }
    payload.correctAnswer = correctLabels.join(',');
  } else if (!payload.correctAnswer) {
    ElMessage.warning('非选择题必须填写参考答案');
    return null;
  }

  return payload;
}

function editQuestion(row: QuestionInfo) {
  editingQuestionId.value = row.id;
  questionForm.subjectId = row.subjectId;
  questionForm.knowledgePointId = row.knowledgePointId;
  questionForm.questionType = row.questionType;
  questionForm.difficulty = row.difficulty;
  questionForm.stem = row.stem;
  questionForm.correctAnswer = row.correctAnswer || '';
  questionForm.analysis = row.analysis || '';
  questionForm.defaultScore = Number(row.defaultScore || 5);
  questionForm.status = row.status;
  questionForm.options = row.options?.length
    ? row.options.map((option) => ({ ...option, correct: isCorrectOption(option.correct) }))
    : defaultOptions(row.questionType);
}

async function toggleQuestionStatus(row: QuestionInfo) {
  const nextStatus = row.status === 1 ? 0 : 1;
  try {
    await updateQuestionStatus(row.id, nextStatus);
    ElMessage.success(nextStatus === 1 ? '题目已发布' : '题目已撤回');
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败');
  }
}

async function removeQuestion(id: number) {
  await ElMessageBox.confirm('确认删除该题目吗？后续已被试卷引用时应改为撤回。', '删除确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  });
  try {
    await deleteQuestion(id);
    ElMessage.success('题目已删除');
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '题目删除失败');
  }
}

function resetQuestionForm() {
  editingQuestionId.value = null;
  questionForm.subjectId = subjects.value[0]?.id || 0;
  questionForm.knowledgePointId = null;
  questionForm.questionType = 'SINGLE_CHOICE';
  questionForm.difficulty = 'EASY';
  questionForm.stem = '';
  questionForm.correctAnswer = '';
  questionForm.analysis = '';
  questionForm.defaultScore = 5;
  questionForm.status = 0;
  questionForm.options = defaultOptions('SINGLE_CHOICE');
}

function isObjectiveType(type: string) {
  return type === 'SINGLE_CHOICE' || type === 'MULTIPLE_CHOICE' || type === 'TRUE_FALSE';
}

function isCorrectOption(value: boolean | number) {
  return value === true || value === 1;
}

function typeText(type: string) {
  return questionTypes.find((item) => item.value === type)?.label || type;
}

function difficultyText(value: string) {
  return difficulties.find((item) => item.value === value)?.label || value;
}

function difficultyTag(value: string) {
  if (value === 'EASY') return 'success';
  if (value === 'MEDIUM') return 'warning';
  return 'danger';
}

function statusText(status: number) {
  return status === 1 ? '已发布' : '草稿';
}
async function aiGenerateQuestion() {
  const subject = subjects.value.find(s => s.id === questionForm.subjectId);
  if (!subject) {
    ElMessage.warning('请先选择科目');
    return;
  }
  const knowledgePoint = knowledgePoints.value.find(k => k.id === questionForm.knowledgePointId);
  try {
    const response = await generateQuestion({
      subject: subject.subjectName,
      knowledgePoint: knowledgePoint?.pointName,
      questionType: questionForm.questionType,
      difficulty: questionForm.difficulty,
    });
    // For simplicity, this example just logs the AI-generated content.
    // A real implementation would parse this content and fill the form.
    console.log('AI-generated question content:', response.data);
    ElMessage.success('AI已生成题目内容，请在控制台查看并手动填充');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI辅助出题失败');
  }
}
</script>
