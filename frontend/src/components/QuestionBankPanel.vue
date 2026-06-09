<template>
  <section class="question-panel">
    <div class="mp-stat-grid">
      <div v-for="card in summaryCards" :key="card.label" class="mp-stat-card">
        <div class="mp-stat-header">{{ card.label }}</div>
        <div class="mp-stat-row">
          <div class="mp-stat-content">
            <div class="mp-stat-value">{{ card.value }}</div>
            <div class="mp-stat-label">{{ card.remark }}</div>
          </div>
        </div>
      </div>
    </div>

    <el-card shadow="never" class="question-workbench">
      <template #header>
        <div class="card-header">
          <span>题库筛选</span>
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

      <div class="ai-generator-strip">
        <div class="ai-generator-main">
          <span class="ai-generator-title">AI 出题台</span>
          <el-input-number v-model="aiSettings.count" :min="1" :max="10" controls-position="right" />
          <el-input
            v-model="aiSettings.requirements"
            clearable
            maxlength="200"
            placeholder="补充要求：考察重点、场景、易错点"
          />
        </div>
        <div class="ai-generator-actions">
          <el-button type="success" :icon="MagicStick" :loading="aiGenerating" @click="aiGenerateQuestion">生成草稿</el-button>
          <el-button type="primary" plain :icon="DocumentAdd" :loading="aiImporting" @click="pickQuestionDocument">识别题目文档</el-button>
        </div>
      </div>

      <div class="ai-material-strip">
        <div class="ai-material-title">
          <span>课程材料生成</span>
          <small>上传 txt / Word / PPT / PDF，按题型数量生成题库草稿</small>
        </div>
        <div class="ai-material-counts">
          <label v-for="item in questionTypes" :key="item.value" class="ai-count-item">
            <span>{{ item.label }}</span>
            <el-input-number v-model="materialCounts[item.value]" :min="0" :max="20" controls-position="right" />
          </label>
        </div>
        <el-button type="primary" :icon="Upload" :loading="aiMaterialGenerating" @click="pickMaterialDocument">上传材料生成</el-button>
      </div>

      <input ref="questionDocInputRef" class="hidden-file-input" type="file" accept=".txt,.text,.md,.doc,.docx,.ppt,.pptx,.pdf" @change="handleQuestionDocumentSelected" />
      <input ref="materialDocInputRef" class="hidden-file-input" type="file" accept=".txt,.text,.md,.doc,.docx,.ppt,.pptx,.pdf" @change="handleMaterialDocumentSelected" />

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
          <el-button @click="aiGenerateQuestion" :icon="MagicStick" :loading="aiGenerating" type="success" plain>AI生成草稿</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="question-workbench">
      <template #header>
        <div class="card-header">
          <span>题目列表（共 {{ totalQuestions }} 题）</span>
          <el-button size="small" :icon="Download" :loading="exporting" @click="exportQuestions">导出</el-button>
        </div>
      </template>

      <div v-if="canManageQuestions && selectedQuestions.length > 0" class="mp-batch-bar">
        已选择 <span class="mp-batch-count">{{ selectedQuestions.length }}</span> 题
        <span class="mp-batch-bar-spacer"></span>
        <el-button size="small" type="success" plain @click="batchSetStatus(1)">批量发布</el-button>
        <el-button size="small" type="warning" plain @click="batchSetStatus(0)">批量撤回</el-button>
        <el-button size="small" type="danger" plain @click="batchDelete">批量删除</el-button>
        <el-button size="small" text @click="clearSelection">取消选择</el-button>
      </div>

      <el-table ref="tableRef" :data="questions" border class="question-table" @selection-change="onSelectionChange">
        <el-table-column v-if="canManageQuestions" type="selection" width="46" />
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
        <el-table-column label="来源" width="190">
          <template #default="scope">
            <div class="source-cell" :title="scope.row.sourceDetail || sourceText(scope.row.sourceType)">
              <el-tag size="small" :type="sourceTag(scope.row.sourceType)">{{ sourceText(scope.row.sourceType) }}</el-tag>
              <small v-if="scope.row.sourceDetail">{{ scope.row.sourceDetail }}</small>
              <small v-if="scope.row.sourcePage || scope.row.sourceParagraph">
                页 {{ scope.row.sourcePage || '-' }} / 段 {{ scope.row.sourceParagraph || '-' }}
              </small>
              <small v-if="scope.row.aiModel || scope.row.promptVersion">
                {{ scope.row.aiModel || 'AI' }} · {{ scope.row.promptVersion || 'prompt' }}
              </small>
            </div>
          </template>
        </el-table-column>
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
      <div class="question-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalQuestions"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="aiDialogVisible" :title="aiDialogTitle" width="960px" class="ai-draft-dialog">
      <div class="ai-draft-toolbar">
        <span>共生成 {{ aiDrafts.length }} 道题，保存时统一进入草稿状态</span>
        <el-button type="primary" :icon="DocumentChecked" :loading="aiSaving" :disabled="aiDrafts.length === 0" @click="saveAiDrafts">
          全部保存
        </el-button>
      </div>

      <div class="ai-draft-list">
        <article v-for="(draft, index) in aiDrafts" :key="`${draft.questionType}-${index}`" class="ai-draft-item">
          <header class="ai-draft-head">
            <div class="ai-draft-tags">
              <el-tag>{{ typeText(draft.questionType) }}</el-tag>
              <el-tag :type="difficultyTag(draft.difficulty)">{{ difficultyText(draft.difficulty) }}</el-tag>
              <el-tag type="info">{{ draft.defaultScore }} 分</el-tag>
            </div>
            <el-button size="small" type="primary" plain :icon="Check" @click="applyAiDraft(draft)">套用</el-button>
          </header>

          <p class="ai-draft-stem">{{ index + 1 }}. {{ draft.stem }}</p>

          <div v-if="isObjectiveType(draft.questionType)" class="ai-draft-options">
            <span
              v-for="option in draft.options"
              :key="option.optionLabel"
              class="ai-option"
              :class="{ 'is-correct': isCorrectOption(option.correct) }"
            >
              {{ option.optionLabel }}. {{ option.optionContent }}
            </span>
          </div>

          <div class="ai-draft-meta">
            <strong>答案</strong>
            <span>{{ draft.correctAnswer || correctAnswerText(draft) }}</span>
          </div>
          <div class="ai-draft-meta">
            <strong>解析</strong>
            <span>{{ draft.analysis || '暂无解析' }}</span>
          </div>
        </article>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Check, DocumentAdd, DocumentChecked, Download, MagicStick, Upload } from '@element-plus/icons-vue';
import { exportToCsv } from '../utils/exportCsv';
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
import {
  generateQuestionDrafts,
  generateQuestionsFromMaterial,
  importQuestionDocument,
  saveGeneratedQuestions,
  type AiGeneratedQuestion
} from '../api/ai';
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
const currentPage = ref(1);
const pageSize = ref(10);
const totalQuestions = ref(0);
const summary = ref({ total: 0, published: 0, draft: 0, types: {}, difficulties: {} });
const editingQuestionId = ref<number | null>(null);

const tableRef = ref<{ clearSelection: () => void }>();
const questionDocInputRef = ref<HTMLInputElement | null>(null);
const materialDocInputRef = ref<HTMLInputElement | null>(null);
const selectedQuestions = ref<QuestionInfo[]>([]);
const exporting = ref(false);
const aiGenerating = ref(false);
const aiImporting = ref(false);
const aiMaterialGenerating = ref(false);
const aiSaving = ref(false);
const aiDialogVisible = ref(false);
const aiDialogTitle = ref('AI题目草稿');
const aiDrafts = ref<AiGeneratedQuestion[]>([]);
const aiSettings = reactive({
  count: 3,
  requirements: ''
});
const materialCounts = reactive<Record<QuestionType, number>>({
  SINGLE_CHOICE: 2,
  MULTIPLE_CHOICE: 1,
  TRUE_FALSE: 1,
  FILL_BLANK: 1,
  SUBJECTIVE: 0
});

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
  sourceType: 'MANUAL',
  sourceDetail: null,
  materialId: null,
  sourcePage: null,
  sourceParagraph: null,
  sourceExcerpt: null,
  aiModel: null,
  promptVersion: null,
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
    const response = await listQuestions({ ...query, page: currentPage.value, size: pageSize.value });
    questions.value = response.data.list;
    totalQuestions.value = response.data.total;
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
  currentPage.value = 1;
  loadQuestions();
}

function handlePageChange(page: number) {
  currentPage.value = page;
  loadQuestions();
}

function handleSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
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
  questionForm.sourceType = row.sourceType || 'MANUAL';
  questionForm.sourceDetail = row.sourceDetail || null;
  questionForm.materialId = row.materialId || null;
  questionForm.sourcePage = row.sourcePage || null;
  questionForm.sourceParagraph = row.sourceParagraph || null;
  questionForm.sourceExcerpt = row.sourceExcerpt || null;
  questionForm.aiModel = row.aiModel || null;
  questionForm.promptVersion = row.promptVersion || null;
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

function onSelectionChange(rows: QuestionInfo[]) {
  selectedQuestions.value = rows;
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

async function batchSetStatus(next: number) {
  const rows = selectedQuestions.value;
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认${next === 1 ? '发布' : '撤回'}选中的 ${rows.length} 道题目吗？`, '批量操作', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await Promise.all(rows.map((r) => updateQuestionStatus(r.id, next)));
    ElMessage.success(`已${next === 1 ? '发布' : '撤回'} ${rows.length} 道题目`);
    clearSelection();
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量操作失败');
  }
}

async function batchDelete() {
  const rows = selectedQuestions.value;
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${rows.length} 道题目吗？该操作不可恢复。`, '批量删除', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    await Promise.all(rows.map((r) => deleteQuestion(r.id)));
    ElMessage.success(`已删除 ${rows.length} 道题目`);
    clearSelection();
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败');
  }
}

async function exportQuestions() {
  exporting.value = true;
  try {
    const response = await listQuestions({ ...query, page: 1, size: 10000 });
    const rows = response.data.list.map((q) => ({
      id: q.id,
      stem: q.stem,
      subject: q.subjectName || '',
      knowledgePoint: q.knowledgePointName || '',
      type: typeText(q.questionType),
      difficulty: difficultyText(q.difficulty),
      score: q.defaultScore,
      answer: isObjectiveType(q.questionType)
        ? (q.options || []).filter((o) => isCorrectOption(o.correct)).map((o) => o.optionLabel).join('')
        : (q.correctAnswer || ''),
      status: statusText(q.status),
      creator: q.creatorName || '',
      source: sourceText(q.sourceType),
      sourceDetail: q.sourceDetail || '',
      sourceLocation: q.sourcePage || q.sourceParagraph ? `页${q.sourcePage || '-'} / 段${q.sourceParagraph || '-'}` : '',
      sourceExcerpt: q.sourceExcerpt || '',
      aiModel: q.aiModel || '',
      promptVersion: q.promptVersion || ''
    }));
    exportToCsv(`题库_${new Date().toISOString().slice(0, 10)}`, [
      { key: 'id', label: 'ID' },
      { key: 'stem', label: '题干' },
      { key: 'subject', label: '科目' },
      { key: 'knowledgePoint', label: '知识点' },
      { key: 'type', label: '题型' },
      { key: 'difficulty', label: '难度' },
      { key: 'score', label: '分值' },
      { key: 'answer', label: '答案' },
      { key: 'status', label: '状态' },
      { key: 'creator', label: '创建人' },
      { key: 'source', label: '来源' },
      { key: 'sourceDetail', label: '来源说明' },
      { key: 'sourceLocation', label: '来源页段' },
      { key: 'sourceExcerpt', label: '来源片段' },
      { key: 'aiModel', label: 'AI模型' },
      { key: 'promptVersion', label: '提示词版本' }
    ], rows);
    ElMessage.success(`已导出 ${rows.length} 道题目`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    exporting.value = false;
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
  questionForm.sourceType = 'MANUAL';
  questionForm.sourceDetail = null;
  questionForm.materialId = null;
  questionForm.sourcePage = null;
  questionForm.sourceParagraph = null;
  questionForm.sourceExcerpt = null;
  questionForm.aiModel = null;
  questionForm.promptVersion = null;
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

function sourceText(sourceType?: string) {
  const map: Record<string, string> = {
    MANUAL: '手动',
    AI_GENERATED: 'AI生成',
    AI_IMPORTED: '文档识别',
    AI_MATERIAL: '材料生成',
    AI_RAG: '资料库/RAG'
  };
  return map[sourceType || 'MANUAL'] || '手动';
}

function sourceTag(sourceType?: string) {
  if (sourceType === 'AI_GENERATED') return 'success';
  if (sourceType === 'AI_IMPORTED') return 'warning';
  if (sourceType === 'AI_MATERIAL') return 'primary';
  if (sourceType === 'AI_RAG') return 'danger';
  return 'info';
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
  aiGenerating.value = true;
  try {
    const response = await generateQuestionDrafts({
      subjectId: subject.id,
      subjectName: subject.subjectName,
      knowledgePointId: knowledgePoint?.id ?? null,
      knowledgePointName: knowledgePoint?.pointName ?? null,
      questionType: questionForm.questionType,
      difficulty: questionForm.difficulty,
      count: aiSettings.count,
      defaultScore: Number(questionForm.defaultScore || 5),
      requirements: aiSettings.requirements.trim()
    });
    showAiDrafts(response.data, 'AI题目草稿');
    ElMessage.success(`已生成 ${aiDrafts.value.length} 道题目草稿`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI辅助出题失败');
  } finally {
    aiGenerating.value = false;
  }
}

function pickQuestionDocument() {
  if (!currentDocumentContext()) return;
  questionDocInputRef.value?.click();
}

function pickMaterialDocument() {
  if (!currentDocumentContext()) return;
  if (materialQuestionTotal() <= 0) {
    ElMessage.warning('请至少设置一种题型数量');
    return;
  }
  materialDocInputRef.value?.click();
}

async function handleQuestionDocumentSelected(event: Event) {
  const file = selectedFile(event);
  if (!file) return;
  const context = currentDocumentContext();
  if (!context) return;
  aiImporting.value = true;
  try {
    const response = await importQuestionDocument(file, context);
    showAiDrafts(response.data, `题目文档识别：${file.name}`);
    ElMessage.success(`已识别 ${aiDrafts.value.length} 道题目草稿`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '题目文档识别失败');
  } finally {
    aiImporting.value = false;
  }
}

async function handleMaterialDocumentSelected(event: Event) {
  const file = selectedFile(event);
  if (!file) return;
  const context = currentDocumentContext();
  if (!context) return;
  if (materialQuestionTotal() <= 0) {
    ElMessage.warning('请至少设置一种题型数量');
    return;
  }
  aiMaterialGenerating.value = true;
  try {
    const response = await generateQuestionsFromMaterial(file, {
      ...context,
      requirements: aiSettings.requirements.trim(),
      typeCounts: { ...materialCounts }
    });
    showAiDrafts(response.data, `课程材料生成：${file.name}`);
    ElMessage.success(`已根据课程材料生成 ${aiDrafts.value.length} 道题目草稿`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '课程材料生成失败');
  } finally {
    aiMaterialGenerating.value = false;
  }
}

function showAiDrafts(drafts: AiGeneratedQuestion[], title: string) {
  aiDialogTitle.value = title;
  aiDrafts.value = drafts.map(normalizeAiDraft);
  aiDialogVisible.value = true;
}

function currentDocumentContext() {
  const subject = subjects.value.find((item) => item.id === questionForm.subjectId);
  if (!subject) {
    ElMessage.warning('请先选择科目');
    return null;
  }
  const knowledgePoint = knowledgePoints.value.find((item) => item.id === questionForm.knowledgePointId);
  return {
    subjectId: subject.id,
    subjectName: subject.subjectName,
    knowledgePointId: knowledgePoint?.id ?? null,
    knowledgePointName: knowledgePoint?.pointName ?? null,
    difficulty: questionForm.difficulty,
    defaultScore: Number(questionForm.defaultScore || 5)
  };
}

function selectedFile(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] || null;
  input.value = '';
  return file;
}

function materialQuestionTotal() {
  return Object.values(materialCounts).reduce((sum, value) => sum + Number(value || 0), 0);
}

async function saveAiDrafts() {
  if (aiDrafts.value.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认保存 ${aiDrafts.value.length} 道 AI 草稿到题库吗？`, '保存AI草稿', {
      type: 'warning',
      confirmButtonText: '保存为草稿',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  aiSaving.value = true;
  try {
    const response = await saveGeneratedQuestions(aiDrafts.value.map((draft) => ({ ...draft, status: 0 })));
    ElMessage.success(`已保存 ${response.data.savedCount} 道AI题目草稿`);
    aiDialogVisible.value = false;
    aiDrafts.value = [];
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI草稿保存失败');
  } finally {
    aiSaving.value = false;
  }
}

function applyAiDraft(draft: AiGeneratedQuestion) {
  editingQuestionId.value = null;
  setQuestionForm(draft);
  aiDialogVisible.value = false;
  ElMessage.success('已套用AI草稿，可继续编辑后保存');
}

function setQuestionForm(payload: QuestionPayload) {
  questionForm.subjectId = payload.subjectId;
  questionForm.knowledgePointId = payload.knowledgePointId;
  questionForm.questionType = payload.questionType;
  questionForm.difficulty = payload.difficulty;
  questionForm.stem = payload.stem;
  questionForm.correctAnswer = payload.correctAnswer || '';
  questionForm.analysis = payload.analysis || '';
  questionForm.defaultScore = Number(payload.defaultScore || 5);
  questionForm.status = payload.status ?? 0;
  questionForm.sourceType = payload.sourceType || 'MANUAL';
  questionForm.sourceDetail = payload.sourceDetail || null;
  questionForm.materialId = payload.materialId || null;
  questionForm.sourcePage = payload.sourcePage || null;
  questionForm.sourceParagraph = payload.sourceParagraph || null;
  questionForm.sourceExcerpt = payload.sourceExcerpt || null;
  questionForm.aiModel = payload.aiModel || null;
  questionForm.promptVersion = payload.promptVersion || null;
  questionForm.options = payload.options?.length
    ? payload.options.map((option) => ({ ...option, correct: isCorrectOption(option.correct) }))
    : defaultOptions(payload.questionType);
}

function normalizeAiDraft(draft: AiGeneratedQuestion): AiGeneratedQuestion {
  const normalized: AiGeneratedQuestion = {
    subjectId: Number(draft.subjectId || questionForm.subjectId),
    knowledgePointId: draft.knowledgePointId ?? null,
    questionType: draft.questionType,
    difficulty: draft.difficulty,
    stem: draft.stem || '',
    correctAnswer: draft.correctAnswer || '',
    analysis: draft.analysis || '',
    defaultScore: Number(draft.defaultScore || questionForm.defaultScore || 5),
    status: 0,
    sourceType: draft.sourceType || 'AI_GENERATED',
    sourceDetail: draft.sourceDetail || null,
    materialId: draft.materialId || null,
    sourcePage: draft.sourcePage || null,
    sourceParagraph: draft.sourceParagraph || null,
    sourceExcerpt: draft.sourceExcerpt || null,
    aiModel: draft.aiModel || null,
    promptVersion: draft.promptVersion || null,
    options: draft.options || []
  };
  if (isObjectiveType(normalized.questionType)) {
    normalized.correctAnswer = correctAnswerText(normalized);
  } else {
    normalized.options = [];
  }
  return normalized;
}

function correctAnswerText(question: QuestionPayload) {
  if (!isObjectiveType(question.questionType)) {
    return question.correctAnswer || '';
  }
  return (question.options || [])
    .filter((option) => isCorrectOption(option.correct))
    .map((option) => option.optionLabel)
    .join(',');
}
</script>
<style scoped>
.ai-generator-strip {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  margin-bottom: 16px;
  border: 1px solid #dce4f2;
  border-radius: 8px;
  background: #f8fbff;
}

.ai-generator-main {
  display: grid;
  grid-template-columns: minmax(92px, auto) 120px minmax(240px, 1fr);
  gap: 12px;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.ai-generator-title {
  font-weight: 700;
  color: #1f2937;
  white-space: nowrap;
}

.ai-generator-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.ai-material-strip {
  display: grid;
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px;
  margin: -4px 0 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.ai-material-title {
  display: grid;
  gap: 3px;
}

.ai-material-title span {
  font-weight: 700;
  color: #1f2937;
}

.ai-material-title small {
  color: #6b7280;
  line-height: 1.4;
}

.ai-material-counts {
  display: grid;
  grid-template-columns: repeat(5, minmax(92px, 1fr));
  gap: 8px;
}

.ai-count-item {
  display: grid;
  gap: 4px;
  color: #4b5563;
  font-size: 12px;
}

.ai-count-item :deep(.el-input-number) {
  width: 100%;
}

.hidden-file-input {
  display: none;
}

.ai-draft-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: #606266;
}

.ai-draft-list {
  display: grid;
  gap: 14px;
  max-height: 64vh;
  overflow: auto;
  padding-right: 4px;
}

.ai-draft-item {
  padding: 14px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
}

.ai-draft-head,
.ai-draft-tags {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-draft-head {
  justify-content: space-between;
}

.ai-draft-stem {
  margin: 12px 0;
  line-height: 1.7;
  color: #1f2937;
  font-weight: 600;
}

.ai-draft-options {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

.ai-option {
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fafafa;
  color: #374151;
  line-height: 1.5;
}

.ai-option.is-correct {
  border-color: #95d475;
  background: #f0f9eb;
  color: #2f6b1f;
  font-weight: 600;
}

.ai-draft-meta {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 8px;
  margin-top: 8px;
  color: #4b5563;
  line-height: 1.6;
}

.ai-draft-meta strong {
  color: #111827;
}

.source-cell {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.source-cell small {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .ai-generator-strip {
    align-items: stretch;
    flex-direction: column;
  }

  .ai-generator-main {
    grid-template-columns: 1fr;
  }

  .ai-generator-actions,
  .ai-material-strip {
    align-items: stretch;
    grid-template-columns: 1fr;
  }

  .ai-material-counts {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .ai-draft-toolbar,
  .ai-draft-head {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
