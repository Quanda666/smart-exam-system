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
          <span>试卷筛选</span>
          <el-tag type="success">阶段 5</el-tag>
        </div>
      </template>

      <div class="paper-toolbar">
        <el-input v-model="query.keyword" placeholder="按试卷名称、科目搜索" clearable @keyup.enter="loadPapers" />
        <el-select v-model="query.subjectId" placeholder="科目" clearable>
          <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable>
          <el-option label="已发布" :value="1" />
          <el-option label="草稿" :value="0" />
        </el-select>
        <el-button type="primary" @click="loadPapers">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
    </el-card>

    <el-card v-if="canManagePapers" shadow="never" class="question-workbench">
      <template #header>
        <div class="card-header">
          <span>{{ editingPaperId ? '编辑试卷' : '手动组卷' }}</span>
          <el-tag>{{ selectedQuestions.length }} 题 / {{ manualTotalScore }} 分</el-tag>
        </div>
      </template>

      <el-form class="paper-form" :model="paperForm" label-position="top">
        <el-form-item label="所属科目">
          <el-select v-model="paperForm.subjectId" placeholder="请选择科目" @change="handlePaperSubjectChange">
            <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷名称">
          <el-input v-model="paperForm.paperName" placeholder="例如：Java程序设计单元测试卷" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="paperForm.status">
            <el-option label="草稿" :value="0" />
            <el-option label="发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item class="question-stem" label="试卷说明">
          <el-input v-model="paperForm.description" type="textarea" :rows="2" placeholder="请输入试卷说明" />
        </el-form-item>

        <div class="option-editor">
          <div class="option-editor-header">
            <strong>选择题目</strong>
            <div class="paper-question-picker">
              <el-select v-model="selectedQuestionId" placeholder="选择已发布题目" filterable>
                <el-option
                  v-for="question in availableQuestions"
                  :key="question.id"
                  :label="`${typeText(question.questionType)} / ${question.stem}`"
                  :value="question.id"
                />
              </el-select>
              <el-button @click="addSelectedQuestion">加入试卷</el-button>
            </div>
          </div>

          <el-table :data="selectedQuestions" border>
            <el-table-column prop="sortOrder" label="序号" width="80" />
            <el-table-column label="题目" min-width="260" show-overflow-tooltip>
              <template #default="scope">
                <div class="stem-cell">
                  <strong>{{ scope.row.stem }}</strong>
                  <small>{{ typeText(scope.row.questionType) }} / {{ difficultyText(scope.row.difficulty) }}</small>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="分值" width="160">
              <template #default="scope">
                <el-input-number v-model="scope.row.score" :min="0.5" :step="0.5" :precision="1" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="scope">
                <el-button link type="danger" @click="removeSelectedQuestion(Number(scope.$index))">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <el-form-item class="question-form-actions" label="操作">
          <el-button type="primary" @click="savePaper">{{ editingPaperId ? '保存修改' : '创建试卷' }}</el-button>
          <el-button @click="resetPaperForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="canManagePapers" shadow="never" class="question-workbench">
      <template #header>
        <div class="card-header">
          <span>规则组卷</span>
          <el-tag type="warning">自动抽题</el-tag>
        </div>
      </template>

      <el-form class="paper-form" :model="generateForm" label-position="top">
        <el-form-item label="所属科目">
          <el-select v-model="generateForm.subjectId" placeholder="请选择科目" @change="handleGenerateSubjectChange">
            <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷名称">
          <el-input v-model="generateForm.paperName" placeholder="例如：Java程序设计自动组卷" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="generateForm.status">
            <el-option label="草稿" :value="0" />
            <el-option label="发布" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item class="question-stem" label="试卷说明">
          <el-input v-model="generateForm.description" type="textarea" :rows="2" placeholder="请输入自动组卷说明" />
        </el-form-item>

        <div class="option-editor">
          <div class="option-editor-header">
            <strong>组卷规则</strong>
            <el-button size="small" @click="addRule">新增规则</el-button>
          </div>
          <div v-for="(rule, index) in generateForm.rules" :key="index" class="paper-rule-row">
            <el-select v-model="rule.knowledgePointId" placeholder="知识点" clearable>
              <el-option v-for="point in generateKnowledgePoints" :key="point.id" :label="point.pointName" :value="point.id" />
            </el-select>
            <el-select v-model="rule.questionType" placeholder="题型">
              <el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="rule.difficulty" placeholder="难度" clearable>
              <el-option v-for="item in difficulties" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input-number v-model="rule.count" :min="1" controls-position="right" />
            <el-input-number v-model="rule.score" :min="0.5" :step="0.5" :precision="1" controls-position="right" />
            <el-button link type="danger" @click="removeRule(index)">删除</el-button>
          </div>
        </div>

        <el-form-item class="question-form-actions" label="操作">
          <el-button type="primary" @click="submitGeneratePaper">执行规则组卷</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" class="question-workbench">
      <template #header>
        <div class="card-header">
          <span>试卷列表</span>
          <el-tag>{{ papers.length }} 份</el-tag>
        </div>
      </template>

      <el-table :data="papers" border class="question-table">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="试卷" min-width="240" show-overflow-tooltip>
          <template #default="scope">
            <div class="stem-cell">
              <strong>{{ scope.row.paperName }}</strong>
              <small>{{ scope.row.subjectName }} / {{ scope.row.description || '无说明' }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="题量" width="90" />
        <el-table-column prop="totalScore" label="总分" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="120" />
        <el-table-column v-if="canManagePapers" label="操作" width="280" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="previewPaper(Number(scope.row.id))">预览</el-button>
            <el-button link type="primary" @click="editPaper(Number(scope.row.id))">编辑</el-button>
            <el-button link type="warning" @click="togglePaperStatus(scope.row as PaperInfo)">
              {{ scope.row.status === 1 ? '撤回' : '发布' }}
            </el-button>
            <el-button link type="danger" @click="removePaper(Number(scope.row.id))">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-drawer v-model="previewVisible" title="试卷预览" size="52%">
      <div v-if="preview" class="paper-preview">
        <h3>{{ preview.paperName }}</h3>
        <p>{{ preview.subjectName }} / {{ preview.totalScore }} 分 / {{ preview.questionCount }} 题</p>
        <div v-for="question in preview.questions || []" :key="question.questionId" class="paper-preview-question">
          <strong>{{ question.sortOrder }}. {{ question.stem }}（{{ question.score }}分）</strong>
          <small>{{ typeText(question.questionType) }} / {{ difficultyText(question.difficulty) }} / {{ question.knowledgePointName || '未指定知识点' }}</small>
        </div>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { listKnowledgePoints, listSubjects, type KnowledgePointInfo, type SubjectInfo } from '../api/basic';
import { listQuestions, type Difficulty, type QuestionInfo, type QuestionType } from '../api/question';
import {
  createPaper,
  deletePaper,
  fetchPaperSummary,
  generatePaper,
  getPaper,
  listPapers,
  updatePaper,
  updatePaperStatus,
  type GeneratePaperPayload,
  type GenerateRulePayload,
  type PaperInfo,
  type PaperPayload,
  type PaperQuestionInfo
} from '../api/paper';
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
const availableQuestions = ref<QuestionInfo[]>([]);
const papers = ref<PaperInfo[]>([]);
const summary = ref({ total: 0, published: 0, draft: 0, totalQuestions: 0 });
const selectedQuestionId = ref<number | null>(null);
const selectedQuestions = ref<PaperQuestionInfo[]>([]);
const editingPaperId = ref<number | null>(null);
const previewVisible = ref(false);
const preview = ref<PaperInfo | null>(null);

const query = reactive<{ keyword: string; subjectId: number | null; status: number | null }>({
  keyword: '',
  subjectId: null,
  status: null
});

const paperForm = reactive<PaperPayload>({
  subjectId: 0,
  paperName: '',
  description: '',
  status: 0,
  questions: []
});

const generateForm = reactive<GeneratePaperPayload>({
  subjectId: 0,
  paperName: '',
  description: '',
  status: 0,
  rules: [defaultRule()]
});

const canManagePapers = computed(() => props.role === 'ADMIN' || props.role === 'TEACHER');

const manualTotalScore = computed(() => selectedQuestions.value.reduce((sum, item) => sum + Number(item.score || 0), 0));

const summaryCards = computed(() => [
  { label: '试卷总数', value: summary.value.total || 0, remark: '已创建试卷' },
  { label: '已发布', value: summary.value.published || 0, remark: '可用于考试任务' },
  { label: '草稿', value: summary.value.draft || 0, remark: '待教师确认发布' },
  { label: '累计题量', value: summary.value.totalQuestions || 0, remark: '试卷题目总数' }
]);

const generateKnowledgePoints = computed(() => knowledgePoints.value.filter((point) => point.subjectId === generateForm.subjectId));

onMounted(async () => {
  await loadBootstrapData();
});

async function loadBootstrapData() {
  await Promise.all([loadSubjects(), loadKnowledgePoints(), loadPapers(), loadSummary()]);
  const firstSubjectId = subjects.value[0]?.id || 0;
  paperForm.subjectId = firstSubjectId;
  generateForm.subjectId = firstSubjectId;
  await loadAvailableQuestions();
}

async function loadSubjects() {
  const response = await listSubjects({ status: 1 });
  subjects.value = response.data;
}

async function loadKnowledgePoints() {
  const response = await listKnowledgePoints({ status: 1 });
  knowledgePoints.value = response.data;
}

async function loadPapers() {
  try {
    const response = await listPapers({ ...query });
    papers.value = response.data;
    await loadSummary();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试卷加载失败');
  }
}

async function loadSummary() {
  try {
    const response = await fetchPaperSummary();
    summary.value = response.data;
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '试卷统计加载失败');
  }
}

async function loadAvailableQuestions() {
  if (!paperForm.subjectId) {
    availableQuestions.value = [];
    return;
  }
  const response = await listQuestions({ subjectId: paperForm.subjectId, status: 1 });
  availableQuestions.value = response.data;
}

function resetQuery() {
  query.keyword = '';
  query.subjectId = null;
  query.status = null;
  loadPapers();
}

async function handlePaperSubjectChange() {
  selectedQuestionId.value = null;
  selectedQuestions.value = [];
  await loadAvailableQuestions();
}

function handleGenerateSubjectChange() {
  generateForm.rules = [defaultRule()];
}

function addSelectedQuestion() {
  if (!selectedQuestionId.value) {
    ElMessage.warning('请选择题目');
    return;
  }
  if (selectedQuestions.value.some((item) => item.questionId === selectedQuestionId.value)) {
    ElMessage.warning('试卷不能重复添加同一道题');
    return;
  }
  const question = availableQuestions.value.find((item) => item.id === selectedQuestionId.value);
  if (!question) return;
  selectedQuestions.value.push({
    questionId: question.id,
    score: Number(question.defaultScore || 5),
    sortOrder: selectedQuestions.value.length + 1,
    questionType: question.questionType,
    difficulty: question.difficulty,
    stem: question.stem,
    subjectId: question.subjectId,
    subjectName: question.subjectName,
    knowledgePointId: question.knowledgePointId,
    knowledgePointName: question.knowledgePointName
  });
  selectedQuestionId.value = null;
}

function removeSelectedQuestion(index: number) {
  selectedQuestions.value.splice(index, 1);
  selectedQuestions.value.forEach((item, itemIndex) => {
    item.sortOrder = itemIndex + 1;
  });
}

async function savePaper() {
  const payload = buildPaperPayload();
  if (!payload) return;
  try {
    if (editingPaperId.value) {
      await updatePaper(editingPaperId.value, payload);
      ElMessage.success('试卷已更新');
    } else {
      await createPaper(payload);
      ElMessage.success('试卷已创建');
    }
    resetPaperForm();
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试卷保存失败');
  }
}

function buildPaperPayload(): PaperPayload | null {
  if (!paperForm.subjectId) {
    ElMessage.warning('请选择所属科目');
    return null;
  }
  if (!paperForm.paperName.trim()) {
    ElMessage.warning('请输入试卷名称');
    return null;
  }
  if (selectedQuestions.value.length === 0) {
    ElMessage.warning('试卷至少需要一道题目');
    return null;
  }
  return {
    subjectId: paperForm.subjectId,
    paperName: paperForm.paperName.trim(),
    description: paperForm.description.trim(),
    status: paperForm.status,
    questions: selectedQuestions.value.map((item, index) => ({
      questionId: item.questionId,
      score: Number(item.score || 5),
      sortOrder: index + 1
    }))
  };
}

async function editPaper(id: number) {
  try {
    const response = await getPaper(id);
    const paper = response.data;
    editingPaperId.value = paper.id;
    paperForm.subjectId = paper.subjectId;
    paperForm.paperName = paper.paperName;
    paperForm.description = paper.description || '';
    paperForm.status = paper.status;
    await loadAvailableQuestions();
    selectedQuestions.value = (paper.questions || []).map((item, index) => ({ ...item, sortOrder: item.sortOrder || index + 1 }));
    ElMessage.success('已载入试卷，可编辑题目与分值');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试卷详情加载失败');
  }
}

async function previewPaper(id: number) {
  try {
    const response = await getPaper(id);
    preview.value = response.data;
    previewVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试卷预览加载失败');
  }
}

async function togglePaperStatus(row: PaperInfo) {
  const nextStatus = row.status === 1 ? 0 : 1;
  try {
    await updatePaperStatus(row.id, nextStatus);
    ElMessage.success(nextStatus === 1 ? '试卷已发布' : '试卷已撤回');
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败');
  }
}

async function removePaper(id: number) {
  await ElMessageBox.confirm('确认删除该试卷吗？后续已发布考试任务时应改为撤回。', '删除确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  });
  try {
    await deletePaper(id);
    ElMessage.success('试卷已删除');
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试卷删除失败');
  }
}

function resetPaperForm() {
  editingPaperId.value = null;
  paperForm.subjectId = subjects.value[0]?.id || 0;
  paperForm.paperName = '';
  paperForm.description = '';
  paperForm.status = 0;
  selectedQuestions.value = [];
  selectedQuestionId.value = null;
  loadAvailableQuestions();
}

function defaultRule(): GenerateRulePayload {
  return {
    knowledgePointId: null,
    questionType: 'SINGLE_CHOICE',
    difficulty: null,
    count: 1,
    score: 5
  };
}

function addRule() {
  generateForm.rules.push(defaultRule());
}

function removeRule(index: number) {
  generateForm.rules.splice(index, 1);
  if (generateForm.rules.length === 0) {
    generateForm.rules.push(defaultRule());
  }
}

async function submitGeneratePaper() {
  if (!generateForm.subjectId || !generateForm.paperName.trim()) {
    ElMessage.warning('请选择科目并填写试卷名称');
    return;
  }
  try {
    await generatePaper({ ...generateForm, paperName: generateForm.paperName.trim(), description: generateForm.description.trim() });
    ElMessage.success('规则组卷成功');
    generateForm.paperName = '';
    generateForm.description = '';
    generateForm.status = 0;
    generateForm.rules = [defaultRule()];
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '规则组卷失败');
  }
}

function typeText(type?: string) {
  return questionTypes.find((item) => item.value === type)?.label || type || '未知题型';
}

function difficultyText(value?: string) {
  return difficulties.find((item) => item.value === value)?.label || value || '未指定难度';
}

function statusText(status: number) {
  return status === 1 ? '已发布' : '草稿';
}
</script>
