<template>
  <section class="paper-page mp-page">
    <div class="mp-page-header">
      <div>
        <h3 class="mp-page-title">试卷生成</h3>
      </div>
      <div v-if="canManagePapers" class="mp-page-actions">
        <el-radio-group v-model="creationMode">
          <el-radio-button label="manual">手动挑题</el-radio-button>
          <el-radio-button label="auto">规则组卷</el-radio-button>
        </el-radio-group>
        <el-button :icon="Refresh" @click="resetCurrentBuilder">重置工作台</el-button>
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

    <el-alert
      v-if="lastPaperOperationAudit"
      class="paper-operation-audit"
      type="success"
      :closable="true"
      show-icon
      @close="lastPaperOperationAudit = null"
    >
      <template #title>
        <div class="paper-operation-audit-content">
          <span>{{ lastPaperOperationAudit.action }} audit recorded: {{ paperOperationAuditText(lastPaperOperationAudit.operationLogIds) }}</span>
          <el-button link type="primary" :icon="CopyDocument" @click="copyLatestPaperOperationAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="CopyDocument" @click="copyLatestPaperOperationAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

    <div v-if="canManagePapers" class="paper-builder-grid">
      <section class="paper-card builder-card">
        <div class="paper-card-header">
          <div>
            <strong>{{ creationMode === 'manual' ? '手动挑题工作台' : '规则组卷工作台' }}</strong>
            <span>{{ editingPaperId ? `正在编辑 #${editingPaperId}` : '新建试卷' }}</span>
          </div>
          <el-tag :type="creationMode === 'manual' ? 'primary' : 'success'">
            {{ creationMode === 'manual' ? '手动' : '自动' }}
          </el-tag>
        </div>

        <el-form v-if="creationMode === 'manual'" class="paper-builder-form" :model="paperForm" label-position="top">
          <div class="paper-form-grid">
            <el-form-item label="所属科目">
              <el-select v-model="paperForm.subjectId" placeholder="请选择科目" filterable @change="handlePaperSubjectChange">
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
          </div>
          <el-form-item label="试卷说明">
            <el-input v-model="paperForm.description" type="textarea" :rows="2" placeholder="请输入试卷说明" />
          </el-form-item>
        </el-form>

        <el-form v-else class="paper-builder-form" :model="generateForm" label-position="top">
          <div class="paper-form-grid">
            <el-form-item label="所属科目">
              <el-select v-model="generateForm.subjectId" placeholder="请选择科目" filterable @change="handleGenerateSubjectChange">
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
          </div>
          <el-form-item label="试卷说明">
            <el-input v-model="generateForm.description" type="textarea" :rows="2" placeholder="请输入试卷说明" />
          </el-form-item>
        </el-form>

        <div v-if="creationMode === 'manual'" class="manual-builder">
          <div class="question-filter-row">
            <el-input v-model="manualFilter.keyword" :prefix-icon="Search" placeholder="搜索题干、知识点" clearable />
            <el-select v-model="manualFilter.questionType" placeholder="题型" clearable>
              <el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="manualFilter.difficulty" placeholder="难度" clearable>
              <el-option v-for="item in difficulties" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="manualFilter.knowledgePointId" placeholder="知识点" clearable filterable>
              <el-option v-for="point in manualKnowledgePoints" :key="point.id" :label="point.pointName" :value="point.id" />
            </el-select>
          </div>

          <div class="question-pool">
            <div class="pool-header">
              <strong>可选题目</strong>
              <span>{{ filteredAvailableQuestions.length }} / {{ availableQuestions.length }}</span>
            </div>
            <div class="question-pool-list">
              <article
                v-for="question in filteredAvailableQuestions"
                :key="question.id"
                :class="['question-pool-item', { selected: selectedQuestionIds.has(question.id) }]"
                @click="addSelectedQuestion(question)"
              >
                <div class="question-pool-meta">
                  <el-tag size="small">{{ typeText(question.questionType) }}</el-tag>
                  <el-tag size="small" :type="difficultyTagType(question.difficulty)">{{ difficultyText(question.difficulty) }}</el-tag>
                  <span>{{ question.knowledgePointName || '未指定知识点' }}</span>
                </div>
                <p>{{ question.stem }}</p>
                <div class="question-pool-footer">
                  <span>{{ Number(question.defaultScore || 5) }} 分</span>
                  <el-button
                    size="small"
                    type="primary"
                    plain
                    :icon="Plus"
                    :disabled="selectedQuestionIds.has(question.id)"
                    @click.stop="addSelectedQuestion(question)"
                  >
                    {{ selectedQuestionIds.has(question.id) ? '已加入' : '加入' }}
                  </el-button>
                </div>
              </article>
              <el-empty v-if="filteredAvailableQuestions.length === 0" description="暂无可选题目" :image-size="80" />
            </div>
          </div>
        </div>

        <div v-else class="auto-builder">
          <div class="rule-toolbar">
            <strong>组卷规则</strong>
            <el-button size="small" type="primary" plain :icon="Plus" @click="addRule">新增规则</el-button>
          </div>
          <div class="rule-list">
            <article v-for="(rule, index) in generateForm.rules" :key="index" class="rule-card">
              <div class="rule-index">{{ index + 1 }}</div>
              <el-select v-model="rule.knowledgePointId" placeholder="知识点" clearable filterable>
                <el-option v-for="point in generateKnowledgePoints" :key="point.id" :label="point.pointName" :value="point.id" />
              </el-select>
              <el-select v-model="rule.questionType" placeholder="题型">
                <el-option v-for="item in questionTypes" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-select v-model="rule.difficulty" placeholder="难度" clearable>
                <el-option v-for="item in difficulties" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-input-number v-model="rule.count" :min="0" controls-position="right" />
              <el-input-number v-model="rule.score" :min="0.5" :step="0.5" :precision="1" controls-position="right" />
              <div :class="['rule-state', { shortage: Number(rule.count || 0) > ruleAvailableCount(rule) }]">
                <span>可用 {{ ruleAvailableCount(rule) }} 题</span>
                <strong>{{ Number(rule.count || 0) * Number(rule.score || 0) }} 分</strong>
              </div>
              <el-button link type="danger" :icon="Close" @click="removeRule(index)">删除</el-button>
            </article>
          </div>
        </div>
      </section>

      <aside class="paper-card basket-card">
        <div class="paper-card-header">
          <div>
            <strong>{{ creationMode === 'manual' ? '当前试卷篮' : '规则预览' }}</strong>
            <span>{{ creationMode === 'manual' ? `${selectedQuestions.length} 题 / ${manualTotalScore} 分` : `${ruleTotalCount} 题 / ${ruleTotalScore} 分` }}</span>
          </div>
        </div>

        <div class="paper-blueprint">
          <div
            v-for="item in creationMode === 'manual' ? manualBlueprint : ruleBlueprint"
            :key="item.value"
            :class="['blueprint-chip', { active: item.count > 0 }]"
          >
            <strong>{{ item.label }}</strong>
            <span>{{ item.count }} 题 / {{ item.score }} 分</span>
          </div>
        </div>

        <div v-if="creationMode === 'manual'" class="basket-scroll">
          <section v-for="group in selectedQuestionGroups" :key="group.value" class="basket-group">
            <div class="basket-group-header">
              <strong>{{ group.label }}</strong>
              <span>{{ group.count }} 题 / {{ group.score }} 分</span>
            </div>
            <article v-for="item in group.items" :key="item.question.questionId" class="basket-item">
              <div class="basket-item-main">
                <strong>{{ item.index + 1 }}. {{ item.question.stem }}</strong>
                <span>{{ difficultyText(item.question.difficulty) }} · {{ item.question.knowledgePointName || '未指定知识点' }}</span>
              </div>
              <div class="basket-item-actions">
                <div class="basket-order-actions">
                  <el-button link :icon="ArrowUp" :disabled="!canMoveSelectedQuestion(item.index, -1)" @click="moveSelectedQuestion(item.index, -1)" />
                  <el-button link :icon="ArrowDown" :disabled="!canMoveSelectedQuestion(item.index, 1)" @click="moveSelectedQuestion(item.index, 1)" />
                </div>
                <el-input-number v-model="item.question.score" size="small" :min="0.5" :step="0.5" :precision="1" />
                <el-button link type="danger" :icon="Close" @click="removeSelectedQuestion(item.index)" />
              </div>
            </article>
          </section>
          <el-empty v-if="selectedQuestions.length === 0" description="尚未选择题目" :image-size="80" />
        </div>

        <div v-else class="basket-scroll">
          <article v-for="(rule, index) in activeGenerateRules" :key="index" class="rule-summary-item">
            <strong>{{ index + 1 }}. {{ typeText(rule.questionType) }}</strong>
            <span>{{ rule.knowledgePointId ? pointName(rule.knowledgePointId) : '不限知识点' }} · {{ difficultyText(rule.difficulty || undefined) }}</span>
            <small>{{ rule.count }} 题 × {{ rule.score }} 分 · 可用 {{ ruleAvailableCount(rule) }} 题</small>
          </article>
          <el-empty v-if="activeGenerateRules.length === 0" description="尚未设置抽题数量" :image-size="80" />
        </div>

        <div class="basket-footer">
          <el-button v-if="creationMode === 'manual'" type="primary" :icon="Check" @click="savePaper">
            {{ editingPaperId ? '保存修改' : '创建试卷' }}
          </el-button>
          <el-button v-else type="success" :icon="MagicStick" @click="submitGeneratePaper">执行组卷</el-button>
          <el-button @click="resetCurrentBuilder">重置</el-button>
        </div>
      </aside>
    </div>

    <div class="mp-toolbar paper-list-toolbar">
      <el-input v-model="query.keyword" :prefix-icon="Search" placeholder="按试卷名称、科目搜索" clearable @keyup.enter="loadPapers" />
      <el-select v-model="query.subjectId" placeholder="科目" clearable>
        <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable>
        <el-option label="已发布" :value="1" />
        <el-option label="草稿" :value="0" />
      </el-select>
      <span class="mp-toolbar-spacer"></span>
      <el-button type="primary" :icon="Search" @click="loadPapers">查询</el-button>
      <el-button :icon="Refresh" @click="resetQuery">重置</el-button>
      <el-button :icon="Download" :loading="exporting" @click="exportPapers">导出</el-button>
    </div>

    <div class="mp-table-card">
      <div v-if="canManagePapers && selectedPapers.length > 0" class="mp-batch-bar">
        已选择 <span class="mp-batch-count">{{ selectedPapers.length }}</span> 份
        <span class="mp-batch-bar-spacer"></span>
        <el-button size="small" type="success" plain @click="batchSetStatus(1)">批量发布</el-button>
        <el-button size="small" type="warning" plain @click="batchSetStatus(0)">批量撤回</el-button>
        <el-button size="small" type="danger" plain @click="batchDelete">批量删除</el-button>
        <el-button size="small" text @click="clearSelection">取消选择</el-button>
      </div>

      <el-table ref="tableRef" :data="papers" border @selection-change="onSelectionChange">
        <el-table-column v-if="canManagePapers" type="selection" width="46" :selectable="canSelectPaper" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="试卷" min-width="260" show-overflow-tooltip>
          <template #default="scope">
            <div class="paper-name-cell">
              <strong>{{ scope.row.paperName }}</strong>
              <span>{{ scope.row.subjectName }} · {{ scope.row.description || '无说明' }}</span>
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
        <el-table-column label="锁定" width="150">
          <template #default="scope">
            <div class="paper-lock-cell">
              <el-tag v-if="scope.row.locked" size="small" type="warning">已锁定</el-tag>
              <el-tag v-else size="small" type="success">可编辑</el-tag>
              <span v-if="Number(scope.row.examCount || 0) > 0">{{ scope.row.examCount }} 场考试</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="creatorName" label="创建人" width="120" />
        <el-table-column v-if="canManagePapers" label="操作" width="340" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :icon="View" @click="previewPaper(Number(scope.row.id))">预览</el-button>
            <el-button link type="primary" :icon="CopyDocument" :disabled="!canCopyPaper(scope.row as PaperInfo)" @click="duplicatePaper(scope.row as PaperInfo)">复制</el-button>
            <el-button link type="primary" :icon="EditPen" :disabled="!canEditPaper(scope.row as PaperInfo)" @click="editPaper(Number(scope.row.id))">编辑</el-button>
            <el-button link type="warning" :disabled="!canTogglePaperStatus(scope.row as PaperInfo)" @click="togglePaperStatus(scope.row as PaperInfo)">
              {{ scope.row.status === 1 ? '撤回' : '发布' }}
            </el-button>
            <el-button link type="danger" :icon="Delete" :disabled="!canDeletePaper(scope.row as PaperInfo)" @click="removePaper(scope.row as PaperInfo)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="mp-pager">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalPapers"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <el-drawer v-model="previewVisible" title="试卷预览" size="56%">
      <div v-if="preview" class="paper-preview">
        <div class="preview-title">
          <h3>{{ preview.paperName }}</h3>
          <span>{{ preview.subjectName }} · {{ preview.totalScore }} 分 · {{ preview.questionCount }} 题</span>
        </div>
        <section v-for="group in previewQuestionGroups" :key="group.value" class="paper-preview-group">
          <div class="paper-preview-group-header">
            <strong>{{ group.label }}</strong>
            <span>{{ group.count }} 题 / {{ group.score }} 分</span>
          </div>
          <article v-for="question in group.items" :key="question.questionId" class="paper-preview-question">
            <strong>{{ question.sortOrder }}. {{ question.stem }}（{{ question.score }}分）</strong>
            <small>{{ difficultyText(question.difficulty) }} · {{ question.knowledgePointName || '未指定知识点' }}</small>
          </article>
        </section>
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  ArrowDown,
  ArrowUp,
  Check,
  Close,
  CopyDocument,
  Delete,
  Download,
  EditPen,
  MagicStick,
  Plus,
  Refresh,
  Search,
  View
} from '@element-plus/icons-vue';
import { exportToCsv } from '../utils/exportCsv';
import {
  copyOperationLogIdToClipboard,
  copyOperationLogLinkToClipboard
} from '../utils/clipboard';
import { listKnowledgePoints, listSubjects, type KnowledgePointInfo, type SubjectInfo } from '../api/basic';
import { listQuestions, type Difficulty, type QuestionInfo, type QuestionType } from '../api/question';
import {
  copyPaper,
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
const generateAvailableQuestions = ref<QuestionInfo[]>([]);
const papers = ref<PaperInfo[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const totalPapers = ref(0);
const summary = ref({ total: 0, published: 0, draft: 0, totalQuestions: 0 });
const selectedQuestions = ref<PaperQuestionInfo[]>([]);
const editingPaperId = ref<number | null>(null);
const previewVisible = ref(false);
const preview = ref<PaperInfo | null>(null);
const creationMode = ref<'manual' | 'auto'>('manual');
const tableRef = ref<{ clearSelection: () => void }>();
const selectedPapers = ref<PaperInfo[]>([]);
const exporting = ref(false);
const lastPaperOperationAudit = ref<{ action: string; operationLogIds: Array<number | string> } | null>(null);

const query = reactive<{ keyword: string; subjectId: number | null; status: number | null }>({
  keyword: '',
  subjectId: null,
  status: null
});

const manualFilter = reactive<{
  keyword: string;
  questionType: QuestionType | null;
  difficulty: Difficulty | null;
  knowledgePointId: number | null;
}>({
  keyword: '',
  questionType: null,
  difficulty: null,
  knowledgePointId: null
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
  rules: defaultRuleSet()
});

const canManagePapers = computed(() => props.role === 'ADMIN' || props.role === 'TEACHER');
const manualTotalScore = computed(() => selectedQuestions.value.reduce((sum, item) => sum + Number(item.score || 0), 0));
const activeGenerateRules = computed(() => generateForm.rules.filter((rule) => Number(rule.count || 0) > 0));
const ruleTotalCount = computed(() => activeGenerateRules.value.reduce((sum, rule) => sum + Number(rule.count || 0), 0));
const ruleTotalScore = computed(() => activeGenerateRules.value.reduce((sum, rule) => sum + Number(rule.count || 0) * Number(rule.score || 0), 0));
const selectedQuestionIds = computed(() => new Set(selectedQuestions.value.map((item) => item.questionId)));
const manualBlueprint = computed(() => questionTypes.map((type) => {
  const items = selectedQuestions.value.filter((question) => question.questionType === type.value);
  return {
    label: type.label,
    value: type.value,
    count: items.length,
    score: items.reduce((sum, question) => sum + Number(question.score || 0), 0)
  };
}));
const ruleBlueprint = computed(() => questionTypes.map((type) => {
  const rules = generateForm.rules.filter((rule) => rule.questionType === type.value && Number(rule.count || 0) > 0);
  return {
    label: type.label,
    value: type.value,
    count: rules.reduce((sum, rule) => sum + Number(rule.count || 0), 0),
    score: rules.reduce((sum, rule) => sum + Number(rule.count || 0) * Number(rule.score || 0), 0)
  };
}));
const selectedQuestionGroups = computed(() => questionTypes
  .map((type) => {
    const items = selectedQuestions.value
      .map((question, index) => ({ question, index }))
      .filter((item) => item.question.questionType === type.value);
    return {
      label: type.label,
      value: type.value,
      count: items.length,
      score: items.reduce((sum, item) => sum + Number(item.question.score || 0), 0),
      items
    };
  })
  .filter((group) => group.items.length > 0));
const previewQuestionGroups = computed(() => questionTypes
  .map((type) => {
    const items = (preview.value?.questions || [])
      .filter((question) => question.questionType === type.value)
      .sort((a, b) => Number(a.sortOrder || 0) - Number(b.sortOrder || 0));
    return {
      label: type.label,
      value: type.value,
      count: items.length,
      score: items.reduce((sum, question) => sum + Number(question.score || 0), 0),
      items
    };
  })
  .filter((group) => group.items.length > 0));

const summaryCards = computed(() => [
  { label: '试卷总数', value: summary.value.total || 0, remark: '已创建', className: '' },
  { label: '已发布', value: summary.value.published || 0, remark: '可用于考试', className: 'mp-val-ok' },
  { label: '草稿', value: summary.value.draft || 0, remark: '待确认', className: 'mp-val-warn' },
  { label: '累计题量', value: summary.value.totalQuestions || 0, remark: '题目总数', className: '' }
]);

const manualKnowledgePoints = computed(() => knowledgePoints.value.filter((point) => point.subjectId === paperForm.subjectId));
const generateKnowledgePoints = computed(() => knowledgePoints.value.filter((point) => point.subjectId === generateForm.subjectId));

const filteredAvailableQuestions = computed(() => {
  const keyword = manualFilter.keyword.trim().toLowerCase();
  return availableQuestions.value.filter((question) => {
    const matchKeyword = !keyword
      || question.stem.toLowerCase().includes(keyword)
      || (question.knowledgePointName || '').toLowerCase().includes(keyword);
    return matchKeyword
      && (!manualFilter.questionType || question.questionType === manualFilter.questionType)
      && (!manualFilter.difficulty || question.difficulty === manualFilter.difficulty)
      && (!manualFilter.knowledgePointId || question.knowledgePointId === manualFilter.knowledgePointId);
  });
});

onMounted(async () => {
  await loadBootstrapData();
});

async function loadBootstrapData() {
  await Promise.all([loadSubjects(), loadKnowledgePoints(), loadPapers(), loadSummary()]);
  const firstSubjectId = subjects.value[0]?.id || 0;
  paperForm.subjectId = firstSubjectId;
  generateForm.subjectId = firstSubjectId;
  await Promise.all([loadAvailableQuestions(), loadGenerateAvailableQuestions()]);
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
    const response = await listPapers({ ...query, page: currentPage.value, size: pageSize.value });
    papers.value = response.data.list;
    totalPapers.value = response.data.total;
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

function rememberPaperOperationAudit(action: string, ids: Array<number | string | null | undefined>) {
  const operationLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (operationLogIds.length === 0) return;
  lastPaperOperationAudit.value = { action, operationLogIds };
}

function paperOperationAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestPaperOperationAuditId() {
  const ids = lastPaperOperationAudit.value?.operationLogIds;
  if (!ids?.length) return;
  try {
    await copyOperationLogIdToClipboard(ids.join(','));
    ElMessage.success('Audit ID copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit ID copy failed');
  }
}

async function copyLatestPaperOperationAuditLink() {
  const id = lastPaperOperationAudit.value?.operationLogIds[0];
  if (!id) return;
  try {
    await copyOperationLogLinkToClipboard(id);
    ElMessage.success('Audit link copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit link copy failed');
  }
}

async function loadAvailableQuestions() {
  if (!paperForm.subjectId) {
    availableQuestions.value = [];
    return;
  }
  const response = await listQuestions({ subjectId: paperForm.subjectId, status: 1, size: 10000 });
  availableQuestions.value = response.data.list;
}

async function loadGenerateAvailableQuestions() {
  if (!generateForm.subjectId) {
    generateAvailableQuestions.value = [];
    return;
  }
  const response = await listQuestions({ subjectId: generateForm.subjectId, status: 1, size: 10000 });
  generateAvailableQuestions.value = response.data.list;
}

function resetQuery() {
  query.keyword = '';
  query.subjectId = null;
  query.status = null;
  currentPage.value = 1;
  loadPapers();
}

function handlePageChange(page: number) {
  currentPage.value = page;
  loadPapers();
}

function handleSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
  loadPapers();
}

async function handlePaperSubjectChange() {
  selectedQuestions.value = [];
  manualFilter.knowledgePointId = null;
  await loadAvailableQuestions();
}

async function handleGenerateSubjectChange() {
  generateForm.rules = defaultRuleSet();
  await loadGenerateAvailableQuestions();
}

function addSelectedQuestion(question: QuestionInfo) {
  if (selectedQuestionIds.value.has(question.id)) {
    return;
  }
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
  normalizeSelectedQuestionOrder();
}

function removeSelectedQuestion(index: number) {
  selectedQuestions.value.splice(index, 1);
  normalizeSelectedQuestionOrder();
}

function moveSelectedQuestion(index: number, direction: -1 | 1) {
  const nextIndex = sameTypeNeighborIndex(index, direction);
  if (nextIndex === -1) {
    return;
  }
  const current = selectedQuestions.value[index];
  selectedQuestions.value[index] = selectedQuestions.value[nextIndex];
  selectedQuestions.value[nextIndex] = current;
  selectedQuestions.value.forEach((item, itemIndex) => {
    item.sortOrder = itemIndex + 1;
  });
}

function canMoveSelectedQuestion(index: number, direction: -1 | 1) {
  return sameTypeNeighborIndex(index, direction) !== -1;
}

function sameTypeNeighborIndex(index: number, direction: -1 | 1) {
  const current = selectedQuestions.value[index];
  if (!current) {
    return -1;
  }
  for (let nextIndex = index + direction; nextIndex >= 0 && nextIndex < selectedQuestions.value.length; nextIndex += direction) {
    if (selectedQuestions.value[nextIndex].questionType === current.questionType) {
      return nextIndex;
    }
  }
  return -1;
}

function normalizeSelectedQuestionOrder() {
  selectedQuestions.value.sort((a, b) => {
    const typeDiff = questionTypeOrder(a.questionType) - questionTypeOrder(b.questionType);
    return typeDiff || Number(a.sortOrder || 0) - Number(b.sortOrder || 0);
  });
  selectedQuestions.value.forEach((item, itemIndex) => {
    item.sortOrder = itemIndex + 1;
  });
}

function questionTypeOrder(type?: string) {
  const index = questionTypes.findIndex((item) => item.value === type);
  return index === -1 ? questionTypes.length : index;
}

async function savePaper() {
  const payload = buildPaperPayload();
  if (!payload) return;
  try {
    let savedPaper: PaperInfo;
    if (editingPaperId.value) {
      const response = await updatePaper(editingPaperId.value, payload);
      savedPaper = response.data;
      rememberPaperOperationAudit('Update paper', [response.data.operationLogId]);
      ElMessage.success('试卷已更新');
    } else {
      const response = await createPaper(payload);
      savedPaper = response.data;
      rememberPaperOperationAudit('Create paper', [response.data.operationLogId]);
      ElMessage.success('试卷已创建');
    }
    preview.value = savedPaper;
    previewVisible.value = true;
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

function canEditPaper(row: PaperInfo) {
  return row.canEdit ?? (row.status === 0 && !row.locked);
}

function canDeletePaper(row: PaperInfo) {
  return row.canDelete ?? (row.status === 0 && !row.locked);
}

function canTogglePaperStatus(row: PaperInfo) {
  return row.status === 1
    ? (row.canRevoke ?? !row.locked)
    : (row.canPublish ?? !row.locked);
}

function canCopyPaper(row: PaperInfo) {
  return row.canCopy ?? true;
}

function canSelectPaper(row: PaperInfo) {
  return canDeletePaper(row) || canTogglePaperStatus(row);
}

async function editPaper(id: number) {
  try {
    const response = await getPaper(id);
    const paper = response.data;
    if (!canEditPaper(paper)) {
      ElMessage.warning(paper.lockReason || '该试卷已锁定，不能直接编辑');
      return;
    }
    creationMode.value = 'manual';
    editingPaperId.value = paper.id;
    paperForm.subjectId = paper.subjectId;
    paperForm.paperName = paper.paperName;
    paperForm.description = paper.description || '';
    paperForm.status = paper.status;
    await loadAvailableQuestions();
    selectedQuestions.value = (paper.questions || []).map((item, index) => ({ ...item, sortOrder: item.sortOrder || index + 1 }));
    normalizeSelectedQuestionOrder();
    ElMessage.success('已载入试卷');
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

async function duplicatePaper(row: PaperInfo) {
  if (!canCopyPaper(row)) {
    ElMessage.warning('当前账号不能复制该试卷');
    return;
  }
  try {
    const response = await copyPaper(row.id);
    const paper = response.data;
    rememberPaperOperationAudit('Copy paper', [response.data.operationLogId]);
    creationMode.value = 'manual';
    editingPaperId.value = paper.id;
    paperForm.subjectId = paper.subjectId;
    paperForm.paperName = paper.paperName;
    paperForm.description = paper.description || '';
    paperForm.status = 0;
    await loadAvailableQuestions();
    selectedQuestions.value = (paper.questions || []).map((item, index) => ({ ...item, sortOrder: item.sortOrder || index + 1 }));
    normalizeSelectedQuestionOrder();
    ElMessage.success('已复制为新草稿，可继续编辑');
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试卷复制失败');
  }
}

async function togglePaperStatus(row: PaperInfo) {
  const nextStatus = row.status === 1 ? 0 : 1;
  if (!canTogglePaperStatus(row)) {
    ElMessage.warning(row.lockReason || '当前试卷状态不允许该操作');
    return;
  }
  try {
    const response = await updatePaperStatus(row.id, nextStatus);
    rememberPaperOperationAudit(nextStatus === 1 ? 'Publish paper' : 'Revoke paper', [response.data.operationLogId]);
    ElMessage.success(nextStatus === 1 ? '试卷已发布' : '试卷已撤回');
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败');
  }
}

async function removePaper(row: PaperInfo) {
  if (!canDeletePaper(row)) {
    ElMessage.warning(row.lockReason || '该试卷已锁定，不能删除');
    return;
  }
  try {
    await ElMessageBox.confirm('确认删除该试卷吗？', '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    const response = await deletePaper(row.id);
    rememberPaperOperationAudit('Delete paper', [response.data.operationLogId]);
    ElMessage.success('试卷已删除');
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试卷删除失败');
  }
}

function onSelectionChange(rows: PaperInfo[]) {
  selectedPapers.value = rows;
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

async function batchSetStatus(next: number) {
  const rows = selectedPapers.value.filter((row) => next === 1 ? (row.canPublish ?? !row.locked) : (row.canRevoke ?? !row.locked));
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认${next === 1 ? '发布' : '撤回'}选中的 ${rows.length} 份试卷吗？`, '批量操作', { type: 'warning' });
  } catch {
    return;
  }
  try {
    const responses = await Promise.all(rows.map((row) => updatePaperStatus(row.id, next)));
    rememberPaperOperationAudit(next === 1 ? 'Batch publish papers' : 'Batch revoke papers', responses.map((response) => response.data.operationLogId));
    ElMessage.success(`已${next === 1 ? '发布' : '撤回'} ${rows.length} 份试卷`);
    clearSelection();
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量操作失败');
  }
}

async function batchDelete() {
  const rows = selectedPapers.value.filter((row) => canDeletePaper(row));
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${rows.length} 份试卷吗？`, '批量删除', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    const responses = await Promise.all(rows.map((row) => deletePaper(row.id)));
    rememberPaperOperationAudit('Batch delete papers', responses.map((response) => response.data.operationLogId));
    ElMessage.success(`已删除 ${rows.length} 份试卷`);
    clearSelection();
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败');
  }
}

async function exportPapers() {
  exporting.value = true;
  try {
    const response = await listPapers({ ...query, page: 1, size: 10000 });
    const rows = response.data.list.map((paper) => ({
      id: paper.id,
      paperName: paper.paperName,
      subject: paper.subjectName || '',
      questionCount: paper.questionCount,
      totalScore: paper.totalScore,
      status: statusText(paper.status),
      creator: paper.creatorName || '',
      description: paper.description || ''
    }));
    exportToCsv(`试卷列表_${new Date().toISOString().slice(0, 10)}`, [
      { key: 'id', label: 'ID' },
      { key: 'paperName', label: '试卷名称' },
      { key: 'subject', label: '科目' },
      { key: 'questionCount', label: '题量' },
      { key: 'totalScore', label: '总分' },
      { key: 'status', label: '状态' },
      { key: 'creator', label: '创建人' },
      { key: 'description', label: '说明' }
    ], rows);
    ElMessage.success(`已导出 ${rows.length} 份试卷`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    exporting.value = false;
  }
}

function resetCurrentBuilder() {
  if (creationMode.value === 'manual') {
    resetPaperForm();
  } else {
    resetGenerateForm();
  }
}

function resetPaperForm() {
  editingPaperId.value = null;
  paperForm.subjectId = subjects.value[0]?.id || 0;
  paperForm.paperName = '';
  paperForm.description = '';
  paperForm.status = 0;
  selectedQuestions.value = [];
  manualFilter.keyword = '';
  manualFilter.questionType = null;
  manualFilter.difficulty = null;
  manualFilter.knowledgePointId = null;
  loadAvailableQuestions();
}

function resetGenerateForm() {
  generateForm.subjectId = subjects.value[0]?.id || 0;
  generateForm.paperName = '';
  generateForm.description = '';
  generateForm.status = 0;
  generateForm.rules = defaultRuleSet();
  loadGenerateAvailableQuestions();
}

function defaultRule(questionType: QuestionType = 'SINGLE_CHOICE'): GenerateRulePayload {
  return {
    knowledgePointId: null,
    questionType,
    difficulty: null,
    count: 0,
    score: 5
  };
}

function defaultRuleSet(): GenerateRulePayload[] {
  return questionTypes.map((type) => defaultRule(type.value));
}

function addRule() {
  generateForm.rules.push(defaultRule());
}

function removeRule(index: number) {
  generateForm.rules.splice(index, 1);
  if (generateForm.rules.length === 0) {
    generateForm.rules = defaultRuleSet();
  }
}

async function submitGeneratePaper() {
  if (!generateForm.subjectId || !generateForm.paperName.trim()) {
    ElMessage.warning('请选择科目并填写试卷名称');
    return;
  }
  const rules = activeGenerateRules.value.map((rule) => ({
    ...rule,
    count: Number(rule.count || 0),
    score: Number(rule.score || 0)
  }));
  if (rules.length === 0) {
    ElMessage.warning('请至少设置一种题型的抽题数量');
    return;
  }
  if (rules.some((rule) => !rule.questionType || !rule.count || !rule.score)) {
    ElMessage.warning('请完整填写组卷规则');
    return;
  }
  try {
    const response = await generatePaper({
      ...generateForm,
      paperName: generateForm.paperName.trim(),
      description: generateForm.description.trim(),
      rules
    });
    ElMessage.success('规则组卷成功');
    rememberPaperOperationAudit('Generate paper', [response.data.operationLogId]);
    preview.value = response.data;
    previewVisible.value = true;
    resetGenerateForm();
    await loadPapers();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '规则组卷失败');
  }
}

function pointName(id: number) {
  return knowledgePoints.value.find((point) => point.id === id)?.pointName || `知识点 #${id}`;
}

function ruleAvailableCount(rule: GenerateRulePayload) {
  return generateAvailableQuestions.value.filter((question) => {
    return question.questionType === rule.questionType
      && (!rule.difficulty || question.difficulty === rule.difficulty)
      && (!rule.knowledgePointId || question.knowledgePointId === rule.knowledgePointId);
  }).length;
}

function typeText(type?: string) {
  return questionTypes.find((item) => item.value === type)?.label || type || '未知题型';
}

function difficultyText(value?: string | null) {
  return difficulties.find((item) => item.value === value)?.label || value || '不限难度';
}

function difficultyTagType(value?: string) {
  if (value === 'EASY') return 'success';
  if (value === 'HARD') return 'danger';
  return 'warning';
}

function statusText(status: number) {
  return status === 1 ? '已发布' : '草稿';
}
</script>

<style scoped>
.paper-builder-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(320px, 420px);
  gap: 16px;
  align-items: stretch;
}

.paper-operation-audit {
  margin-bottom: 14px;
}

.paper-operation-audit-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.paper-card {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.04);
}

.builder-card,
.basket-card {
  padding: 18px;
}

.paper-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.paper-card-header div {
  display: grid;
  gap: 4px;
}

.paper-card-header strong {
  font-size: 16px;
  color: #111827;
}

.paper-card-header span {
  color: #64748b;
  font-size: 12px;
}

.paper-blueprint {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 14px;
}

.blueprint-chip {
  display: grid;
  gap: 4px;
  min-height: 54px;
  padding: 9px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.blueprint-chip strong {
  color: #334155;
  font-size: 13px;
}

.blueprint-chip span {
  color: #64748b;
  font-size: 12px;
}

.blueprint-chip.active {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.paper-builder-form {
  margin-bottom: 16px;
}

.paper-form-grid {
  display: grid;
  grid-template-columns: minmax(180px, 0.8fr) minmax(260px, 1.4fr) 140px;
  gap: 12px;
}

.manual-builder,
.auto-builder {
  display: grid;
  gap: 14px;
}

.question-filter-row {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 140px 140px minmax(180px, 0.8fr);
  gap: 10px;
}

.question-pool {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
}

.pool-header,
.rule-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #e5e7eb;
}

.pool-header span,
.rule-toolbar span {
  color: #64748b;
  font-size: 12px;
}

.question-pool-list {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow: auto;
  padding: 12px;
}

.question-pool-item {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  cursor: pointer;
  transition: border-color 0.18s, background 0.18s;
}

.question-pool-item:hover {
  border-color: #409eff;
  background: #f8fbff;
}

.question-pool-item.selected {
  border-color: #93c5fd;
  background: #eff6ff;
}

.question-pool-item p {
  margin: 0;
  line-height: 1.65;
  color: #1f2937;
  overflow-wrap: anywhere;
}

.question-pool-meta,
.question-pool-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.question-pool-meta span,
.question-pool-footer span {
  color: #64748b;
  font-size: 12px;
}

.question-pool-footer {
  justify-content: space-between;
}

.rule-list {
  display: grid;
  gap: 10px;
}

.rule-card {
  display: grid;
  grid-template-columns: 34px minmax(150px, 1fr) 130px 120px 104px 116px 104px 70px;
  gap: 10px;
  align-items: center;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.rule-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #eef2ff;
  color: #4f46e5;
  font-weight: 700;
}

.rule-state {
  display: grid;
  gap: 3px;
  color: #64748b;
  font-size: 12px;
}

.rule-state strong {
  color: #111827;
}

.rule-state.shortage span {
  color: #dc2626;
}

.basket-card {
  display: flex;
  flex-direction: column;
  min-height: 420px;
}

.basket-scroll {
  display: grid;
  gap: 10px;
  align-content: start;
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-right: 2px;
}

.basket-group {
  display: grid;
  gap: 8px;
}

.basket-group-header {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 8px;
  background: #eef2ff;
  color: #3730a3;
  font-size: 13px;
}

.basket-item,
.rule-summary-item {
  display: grid;
  gap: 10px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.basket-item-main {
  display: grid;
  gap: 5px;
  min-width: 0;
}

.basket-item-main strong,
.rule-summary-item strong {
  line-height: 1.55;
  overflow-wrap: anywhere;
}

.basket-item-main span,
.rule-summary-item span,
.rule-summary-item small {
  color: #64748b;
  font-size: 12px;
}

.basket-item-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.basket-order-actions {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.basket-footer {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding-top: 14px;
  margin-top: 14px;
  border-top: 1px solid #e5e7eb;
}

.paper-list-toolbar > .el-input {
  width: min(360px, 100%);
}

.paper-list-toolbar > .el-select {
  width: 160px;
}

.paper-name-cell {
  display: grid;
  gap: 4px;
}

.paper-name-cell span {
  color: #64748b;
  font-size: 12px;
}

.paper-lock-cell {
  display: grid;
  gap: 4px;
  align-items: center;
}

.paper-lock-cell span {
  color: #64748b;
  font-size: 12px;
  line-height: 1.2;
}

.preview-title {
  display: grid;
  gap: 6px;
  margin-bottom: 16px;
}

.preview-title h3 {
  margin: 0;
  font-size: 20px;
}

.preview-title span {
  color: #64748b;
}

.paper-preview {
  display: grid;
  gap: 12px;
}

.paper-preview-group {
  display: grid;
  gap: 10px;
}

.paper-preview-group-header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #eef2ff;
  color: #3730a3;
}

.paper-preview-question {
  display: grid;
  gap: 6px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.paper-preview-question strong {
  line-height: 1.65;
  overflow-wrap: anywhere;
}

.paper-preview-question small {
  color: #64748b;
}

@media (max-width: 1180px) {
  .paper-builder-grid,
  .paper-form-grid,
  .question-filter-row,
  .rule-card {
    grid-template-columns: 1fr;
  }

  .basket-footer {
    grid-template-columns: 1fr;
  }
}
</style>
