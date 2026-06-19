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

    <el-alert
      v-if="lastQuestionOperationAudit"
      class="question-operation-audit"
      type="success"
      :closable="true"
      show-icon
      @close="lastQuestionOperationAudit = null"
    >
      <template #title>
        <div class="question-operation-audit-content">
          <span>{{ lastQuestionOperationAudit.action }} audit recorded: {{ questionOperationAuditText(lastQuestionOperationAudit.questionReviewLogIds) }}</span>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestQuestionOperationAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestQuestionOperationAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

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
        <el-select v-model="query.status" placeholder="可用状态" clearable>
          <el-option label="可组卷" :value="1" />
          <el-option label="不可用" :value="0" />
        </el-select>
        <el-select v-model="query.reviewStatus" placeholder="审核状态" clearable>
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已驳回" value="REJECTED" />
        </el-select>
        <el-button type="primary" @click="loadQuestions">查询</el-button>
        <el-button type="warning" plain @click="showReviewQueue">待审队列</el-button>
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
          <el-input-number v-model="aiSettings.count" :min="1" :max="AI_BATCH_QUESTION_COUNT" controls-position="right" />
          <el-input
            v-model="aiSettings.requirements"
            clearable
            maxlength="200"
            placeholder="补充要求：考察重点、场景、易错点"
          />
        </div>
        <div class="ai-generator-actions">
          <el-button type="success" :icon="MagicStick" :loading="aiGenerating" @click="aiGenerateQuestion">生成草稿</el-button>
          <el-button type="primary" plain :icon="Upload" :loading="excelImporting" @click="pickExcelFile">Excel 导入</el-button>
          <el-button type="info" plain :icon="View" @click="templatePreviewVisible = true">查看模板</el-button>
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
            <el-input-number v-model="materialCounts[item.value]" :min="0" :max="AI_MATERIAL_TYPE_COUNT" controls-position="right" />
          </label>
        </div>
        <div class="ai-material-total" :class="{ 'is-danger': materialQuestionTotalExceeded }">
          {{ materialQuestionTotalValue }} / {{ AI_MATERIAL_TOTAL_COUNT }}
        </div>
        <el-button type="primary" :icon="Upload" :loading="aiMaterialGenerating" :disabled="materialQuestionTotalInvalid" @click="pickMaterialDocument">上传材料生成</el-button>
      </div>

      <input ref="excelInputRef" class="hidden-file-input" type="file" accept=".xlsx,.xls" @change="handleExcelSelected" />
      <input ref="materialDocInputRef" class="hidden-file-input" type="file" :accept="AI_DOCUMENT_ACCEPT" @change="handleMaterialDocumentSelected" />

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
        <el-form-item label="审核状态">
          <el-tag type="info">保存后进入草稿，提交审核通过后才能组卷</el-tag>
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
        <el-button size="small" type="success" plain @click="batchSubmitReview">批量提交审核</el-button>
        <el-button size="small" type="warning" plain @click="batchSetStatus(0)">批量下架</el-button>
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
        <el-table-column label="审核" width="150">
          <template #default="scope">
            <div class="review-cell">
              <el-tag :type="reviewStatusTag(scope.row.reviewStatus)">{{ reviewStatusText(scope.row.reviewStatus) }}</el-tag>
              <small>v{{ scope.row.versionNo || 1 }}</small>
              <small v-if="scope.row.reviewComment" :title="scope.row.reviewComment">{{ scope.row.reviewComment }}</small>
            </div>
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
        <el-table-column v-if="canManageQuestions" label="操作" width="310" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.canEdit !== false" link type="primary" @click="editQuestion(scope.row as QuestionInfo)">编辑</el-button>
            <el-button v-if="canSubmitReview(scope.row as QuestionInfo)" link type="success" @click="submitReview(scope.row as QuestionInfo)">提交审核</el-button>
            <el-button v-if="canReviewQuestion(scope.row as QuestionInfo)" link type="success" @click="approveReview(scope.row as QuestionInfo)">通过</el-button>
            <el-button v-if="canReviewQuestion(scope.row as QuestionInfo)" link type="warning" @click="rejectReview(scope.row as QuestionInfo)">驳回</el-button>
            <el-button v-if="scope.row.canTakeOffline" link type="warning" @click="toggleQuestionStatus(scope.row as QuestionInfo)">下架</el-button>
            <el-button link type="info" @click="openReviewLogs(scope.row as QuestionInfo)">日志</el-button>
            <el-button v-if="scope.row.canDelete !== false" link type="danger" @click="removeQuestion(Number(scope.row.id))">删除</el-button>
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
            <span>{{ draft.correctAnswer || correctAnswerText(draft) || '待教师确认' }}</span>
          </div>
          <div class="ai-draft-meta">
            <strong>解析</strong>
            <span>{{ draft.analysis || '暂无解析' }}</span>
          </div>
        </article>
      </div>
    </el-dialog>

    <el-drawer v-model="logDrawerVisible" title="题目审核日志" size="520px">
      <div v-if="activeLogQuestion" class="log-drawer-head">
        <strong>{{ activeLogQuestion.stem }}</strong>
        <el-tag>{{ reviewStatusText(activeLogQuestion.reviewStatus) }} · v{{ activeLogQuestion.versionNo || 1 }}</el-tag>
      </div>
      <el-timeline v-loading="logLoading">
        <el-timeline-item
          v-for="log in reviewLogs"
          :key="log.id"
          :timestamp="log.operatedAt"
          placement="top"
        >
          <div class="review-log-item">
            <div class="question-review-log-id-cell">
              <strong>{{ actionText(log.actionType) }}</strong>
              <span>#{{ log.id }}</span>
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy question review audit ID"
                aria-label="Copy question review audit ID"
                @click="copyQuestionReviewAuditId(log.id)"
              />
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy question review audit link"
                aria-label="Copy question review audit link"
                @click="copyQuestionReviewAuditLink(log.id)"
              />
            </div>
            <span>{{ log.operatorName || '系统' }}</span>
            <small>v{{ log.versionNo }} · {{ reviewStatusText(log.fromReviewStatus || undefined) }} -> {{ reviewStatusText(log.toReviewStatus || undefined) }}</small>
            <p v-if="log.comment">{{ log.comment }}</p>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-if="!logLoading && reviewLogs.length === 0" description="暂无日志" />
    </el-drawer>

    <el-dialog v-model="templatePreviewVisible" title="Excel 题目导入模板" width="880px">
      <div class="template-preview">
        <div class="template-description">
          <el-alert type="info" :closable="false">
            <template #title>
              <strong>使用说明</strong>
            </template>
            <ol>
              <li>点击"下载模板"按钮获取标准 Excel 模板文件</li>
              <li>在模板中按格式填写题目信息（参考示例行）</li>
              <li>题型支持：单选、多选、判断、填空、主观</li>
              <li>客观题选项格式：每行一个选项，使用 A. B. C. D. 格式</li>
              <li>答案格式：单选填 A，多选填 A,B,C，判断填 A 或 B</li>
              <li>填写完成后点击"Excel 导入"按钮上传文件</li>
            </ol>
          </el-alert>
        </div>

        <div class="template-example">
          <h4>模板格式示例：</h4>
          <el-table :data="templateExampleData" border>
            <el-table-column prop="type" label="题型" width="80" />
            <el-table-column prop="stem" label="题干" min-width="180" show-overflow-tooltip />
            <el-table-column prop="options" label="选项" min-width="140" show-overflow-tooltip />
            <el-table-column prop="answer" label="答案" width="80" />
            <el-table-column prop="analysis" label="解析" min-width="160" show-overflow-tooltip />
            <el-table-column prop="score" label="分值" width="70" />
          </el-table>
        </div>
      </div>

      <template #footer>
        <el-button @click="templatePreviewVisible = false">关闭</el-button>
        <el-button type="primary" :icon="Download" @click="downloadTemplate">下载模板</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Check, DocumentAdd, DocumentChecked, DocumentCopy, Download, MagicStick, Upload, View } from '@element-plus/icons-vue';
import { exportToCsv } from '../utils/exportCsv';
import { listKnowledgePoints, listSubjects, type KnowledgePointInfo, type SubjectInfo } from '../api/basic';
import {
  createQuestion,
  deleteQuestion,
  fetchQuestionSummary,
  listQuestions,
  listQuestionReviewLogs,
  approveQuestionReview,
  rejectQuestionReview,
  submitQuestionReview,
  updateQuestion,
  updateQuestionStatus,
  type Difficulty,
  type QuestionInfo,
  type QuestionOption,
  type QuestionPayload,
  type QuestionReviewLog,
  type QuestionReviewStatus,
  type QuestionSummary,
  type QuestionType
} from '../api/question';
import {
  generateQuestionDrafts,
  generateQuestionsFromMaterial,
  importQuestionDocument,
  importQuestionExcel,
  downloadQuestionTemplate,
  saveGeneratedQuestions,
  type AiGeneratedQuestion
} from '../api/ai';
import {
  copyQuestionReviewAuditIdToClipboard,
  copyQuestionReviewAuditLinkToClipboard
} from '../utils/clipboard';
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

const AI_BATCH_QUESTION_COUNT = 10;
const AI_MATERIAL_TYPE_COUNT = 30;
const AI_MATERIAL_TOTAL_COUNT = 30;
const AI_DOCUMENT_MAX_FILE_BYTES = 25 * 1024 * 1024;
const AI_SOURCE_DETAIL_MAX_LENGTH = 255;
const AI_DOCUMENT_SUPPORTED_EXTENSIONS = ['txt', 'text', 'md', 'doc', 'docx', 'ppt', 'pptx', 'pdf'];
const AI_DOCUMENT_ACCEPT = AI_DOCUMENT_SUPPORTED_EXTENSIONS.map((extension) => `.${extension}`).join(',');
const QUESTION_DOCUMENT_SOURCE_LABEL = 'Question document import';
const MATERIAL_GENERATION_SOURCE_LABEL = 'Course material generation';

const subjects = ref<SubjectInfo[]>([]);
const knowledgePoints = ref<KnowledgePointInfo[]>([]);
const questions = ref<QuestionInfo[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const totalQuestions = ref(0);
const emptySummary: QuestionSummary = {
  total: 0,
  published: 0,
  draft: 0,
  pendingReview: 0,
  approvedReview: 0,
  rejectedReview: 0,
  types: {},
  difficulties: {}
};
const summary = ref<QuestionSummary>({ ...emptySummary });
const editingQuestionId = ref<number | null>(null);

const tableRef = ref<{ clearSelection: () => void }>();
const excelInputRef = ref<HTMLInputElement | null>(null);
const materialDocInputRef = ref<HTMLInputElement | null>(null);
const selectedQuestions = ref<QuestionInfo[]>([]);
const exporting = ref(false);
const aiGenerating = ref(false);
const aiImporting = ref(false);
const aiMaterialGenerating = ref(false);
const excelImporting = ref(false);
const templatePreviewVisible = ref(false);
const aiSaving = ref(false);
const aiDialogVisible = ref(false);
const aiDialogTitle = ref('AI题目草稿');
const aiDrafts = ref<AiGeneratedQuestion[]>([]);
const logDrawerVisible = ref(false);
const logLoading = ref(false);
const activeLogQuestion = ref<QuestionInfo | null>(null);
const reviewLogs = ref<QuestionReviewLog[]>([]);
const lastQuestionOperationAudit = ref<{ action: string; questionReviewLogIds: Array<number | string> } | null>(null);
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

const templateExampleData = [
  {
    type: '单选',
    stem: '以下哪个是 Java 的基本数据类型？',
    options: 'A. String\nB. int\nC. Integer\nD. Object',
    answer: 'B',
    analysis: 'int 是 Java 的 8 种基本数据类型之一',
    score: '5'
  },
  {
    type: '多选',
    stem: '以下哪些是 Spring 框架的核心模块？',
    options: 'A. Spring Core\nB. Spring MVC\nC. Spring Boot\nD. Spring AOP',
    answer: 'A,B,D',
    analysis: 'Spring Boot 是基于 Spring 的快速开发框架',
    score: '5'
  },
  {
    type: '判断',
    stem: 'Java 中的 String 是可变的。',
    options: 'A. 正确\nB. 错误',
    answer: 'B',
    analysis: 'String 在 Java 中是不可变的',
    score: '3'
  }
];
const materialQuestionTotalValue = computed(() => materialQuestionTotal());
const materialQuestionTotalExceeded = computed(() => materialQuestionTotalValue.value > AI_MATERIAL_TOTAL_COUNT);
const materialQuestionTotalInvalid = computed(() => materialQuestionTotalValue.value <= 0 || materialQuestionTotalExceeded.value);

const query = reactive<{
  keyword: string;
  subjectId: number | null;
  knowledgePointId: number | null;
  questionType: QuestionType | null;
  difficulty: Difficulty | null;
  status: number | null;
  reviewStatus: QuestionReviewStatus | null;
}>({
  keyword: '',
  subjectId: null,
  knowledgePointId: null,
  questionType: null,
  difficulty: null,
  status: null,
  reviewStatus: null
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
  { label: '可组卷', value: summary.value.published || 0, remark: '已审核通过并可引用' },
  { label: '待审核', value: summary.value.pendingReview || 0, remark: '等待审核处理' },
  { label: '已驳回', value: summary.value.rejectedReview || 0, remark: '需修改后重新提交' }
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
    summary.value = { ...emptySummary, ...response.data };
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
  query.reviewStatus = null;
  currentPage.value = 1;
  loadQuestions();
}

function showReviewQueue() {
  query.status = 0;
  query.reviewStatus = 'PENDING';
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
      const response = await updateQuestion(editingQuestionId.value, payload);
      rememberQuestionOperationAudit('Update question', [response.data.questionReviewLogId]);
      ElMessage.success('题目已更新');
    } else {
      const response = await createQuestion(payload);
      rememberQuestionOperationAudit('Create question', [response.data.questionReviewLogId]);
      ElMessage.success('题目已新增');
    }
    resetQuestionForm();
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '题目保存失败');
  }
}

function rememberQuestionOperationAudit(action: string, ids: Array<number | string | null | undefined>) {
  const questionReviewLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (questionReviewLogIds.length === 0) return;
  lastQuestionOperationAudit.value = { action, questionReviewLogIds };
}

function questionOperationAuditText(ids: Array<number | string>) {
  if (ids.length === 1) return `#${ids[0]}`;
  return `${ids.length} logs, latest #${ids[0]}`;
}

async function copyLatestQuestionOperationAuditId() {
  const ids = lastQuestionOperationAudit.value?.questionReviewLogIds;
  if (!ids || ids.length === 0) return;
  try {
    await copyQuestionReviewAuditIdToClipboard(ids.join(','));
    ElMessage.success('Audit ID copied');
  } catch {
    ElMessage.error('Failed to copy audit ID');
  }
}

async function copyLatestQuestionOperationAuditLink() {
  const id = lastQuestionOperationAudit.value?.questionReviewLogIds[0];
  if (!id) return;
  try {
    await copyQuestionReviewAuditLinkToClipboard(id);
    ElMessage.success('Audit link copied');
  } catch {
    ElMessage.error('Failed to copy audit link');
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
    const response = await updateQuestionStatus(row.id, nextStatus);
    rememberQuestionOperationAudit(nextStatus === 1 ? 'Online question' : 'Offline question', [response.data.questionReviewLogId]);
    ElMessage.success(nextStatus === 1 ? '题目已设为可用' : '题目已下架');
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '状态更新失败');
  }
}

function canSubmitReview(row: QuestionInfo) {
  return row.canSubmitReview === true;
}

function canReviewQuestion(row: QuestionInfo) {
  return row.canReview === true;
}

async function submitReview(row: QuestionInfo) {
  try {
    const response = await submitQuestionReview(row.id);
    rememberQuestionOperationAudit('Submit question review', [response.data.questionReviewLogId]);
    ElMessage.success('题目已提交审核');
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交审核失败');
  }
}

async function approveReview(row: QuestionInfo) {
  try {
    const response = await approveQuestionReview(row.id);
    rememberQuestionOperationAudit('Approve question review', [response.data.questionReviewLogId]);
    ElMessage.success('题目已审核通过，可用于组卷');
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核通过失败');
  }
}

async function rejectReview(row: QuestionInfo) {
  try {
    const result = await ElMessageBox.prompt('请输入驳回原因，便于创建人修改后重新提交。', '驳回题目', {
      inputType: 'textarea',
      inputValidator: (value) => Boolean(value && value.trim()),
      inputErrorMessage: '请填写驳回原因',
      confirmButtonText: '驳回',
      cancelButtonText: '取消'
    });
    const response = await rejectQuestionReview(row.id, result.value.trim());
    rememberQuestionOperationAudit('Reject question review', [response.data.questionReviewLogId]);
    ElMessage.success('题目已驳回');
    await loadQuestions();
  } catch (error) {
    if (error === 'cancel' || error === 'close') return;
    ElMessage.error(error instanceof Error ? error.message : '驳回失败');
  }
}

async function openReviewLogs(row: QuestionInfo) {
  activeLogQuestion.value = row;
  logDrawerVisible.value = true;
  logLoading.value = true;
  try {
    const response = await listQuestionReviewLogs(row.id);
    reviewLogs.value = response.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审核日志加载失败');
    reviewLogs.value = [];
  } finally {
    logLoading.value = false;
  }
}

async function copyQuestionReviewAuditId(logId?: number | string | null) {
  try {
    const value = await copyQuestionReviewAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`Question review audit ID copied: ${value}`);
  } catch {
    ElMessage.error('Failed to copy question review audit ID');
  }
}

async function copyQuestionReviewAuditLink(logId?: number | string | null) {
  try {
    const link = await copyQuestionReviewAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('Question review audit link copied');
  } catch {
    ElMessage.error('Failed to copy question review audit link');
  }
}

async function removeQuestion(id: number) {
  await ElMessageBox.confirm('确认删除该题目吗？后续已被试卷引用时应改为撤回。', '删除确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  });
  try {
    const response = await deleteQuestion(id);
    rememberQuestionOperationAudit('Delete question', [response.data.questionReviewLogId]);
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

async function batchSubmitReview() {
  const rows = selectedQuestions.value.filter(canSubmitReview);
  if (rows.length === 0) {
    ElMessage.warning('请选择草稿或已驳回的未上架题目');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认提交 ${rows.length} 道题目进入审核吗？`, '批量提交审核', { type: 'warning' });
  } catch {
    return;
  }
  try {
    const responses = await Promise.all(rows.map((r) => submitQuestionReview(r.id)));
    rememberQuestionOperationAudit('Batch submit question review', responses.map((response) => response.data.questionReviewLogId));
    ElMessage.success(`已提交 ${rows.length} 道题目审核`);
    clearSelection();
    await loadQuestions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量提交审核失败');
  }
}

async function batchSetStatus(next: number) {
  const rows = next === 0 ? selectedQuestions.value.filter((row) => row.canTakeOffline) : selectedQuestions.value;
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认${next === 1 ? '设为可用' : '下架'}选中的 ${rows.length} 道题目吗？`, '批量操作', { type: 'warning' });
  } catch {
    return;
  }
  try {
    const responses = await Promise.all(rows.map((r) => updateQuestionStatus(r.id, next)));
    rememberQuestionOperationAudit(next === 1 ? 'Batch online questions' : 'Batch offline questions', responses.map((response) => response.data.questionReviewLogId));
    ElMessage.success(`已${next === 1 ? '设为可用' : '下架'} ${rows.length} 道题目`);
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
    const responses = await Promise.all(rows.map((r) => deleteQuestion(r.id)));
    rememberQuestionOperationAudit('Batch delete questions', responses.map((response) => response.data.questionReviewLogId));
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
      version: q.versionNo || 1,
      answer: isObjectiveType(q.questionType)
        ? (q.options || []).filter((o) => isCorrectOption(o.correct)).map((o) => o.optionLabel).join('')
        : (q.correctAnswer || ''),
      status: statusText(q.status),
      reviewStatus: reviewStatusText(q.reviewStatus),
      reviewComment: q.reviewComment || '',
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
      { key: 'version', label: '版本' },
      { key: 'answer', label: '答案' },
      { key: 'status', label: '状态' },
      { key: 'reviewStatus', label: '审核状态' },
      { key: 'reviewComment', label: '审核意见' },
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
  return status === 1 ? '可组卷' : '不可用';
}

function reviewStatusText(status?: QuestionReviewStatus) {
  const map: Record<QuestionReviewStatus, string> = {
    DRAFT: '草稿',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回'
  };
  return map[status || 'DRAFT'];
}

function reviewStatusTag(status?: QuestionReviewStatus) {
  if (status === 'APPROVED') return 'success';
  if (status === 'PENDING') return 'warning';
  if (status === 'REJECTED') return 'danger';
  return 'info';
}

function actionText(action: string) {
  const map: Record<string, string> = {
    CREATE: '创建题目',
    EDIT: '编辑题目',
    DELETE: '删除题目',
    SUBMIT_REVIEW: '提交审核',
    APPROVE: '审核通过',
    REJECT: '审核驳回',
    ONLINE: '设为可用',
    OFFLINE: '下架题目'
  };
  return map[action] || action;
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

function pickExcelFile() {
  if (!currentDocumentContext()) return;
  excelInputRef.value?.click();
}

function pickMaterialDocument() {
  if (!currentDocumentContext()) return;
  if (!validateMaterialQuestionCounts()) return;
  materialDocInputRef.value?.click();
}

async function handleExcelSelected(event: Event) {
  const file = selectedFile(event);
  if (!file) return;

  const filename = file.name.toLowerCase();
  if (!filename.endsWith('.xlsx') && !filename.endsWith('.xls')) {
    ElMessage.error('仅支持 .xlsx 或 .xls 格式的 Excel 文件');
    return;
  }

  const context = currentDocumentContext();
  if (!context) return;

  excelImporting.value = true;
  try {
    const response = await importQuestionExcel(file, context);
    showAiDrafts(response.data, `Excel 导入：${file.name}`);
    ElMessage.success(`已成功导入 ${aiDrafts.value.length} 道题目草稿`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Excel 导入失败');
  } finally {
    excelImporting.value = false;
    if (excelInputRef.value) {
      excelInputRef.value.value = '';
    }
  }
}

async function handleMaterialDocumentSelected(event: Event) {
  const file = selectedFile(event);
  if (!file) return;
  if (!validateDocumentUploadFile(file, MATERIAL_GENERATION_SOURCE_LABEL)) return;
  const context = currentDocumentContext();
  if (!context) return;
  if (!validateMaterialQuestionCounts()) return;
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

function downloadTemplate() {
  const url = downloadQuestionTemplate();
  const link = document.createElement('a');
  link.href = url;
  link.download = '题目导入模板.xlsx';
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  ElMessage.success('模板下载已开始');
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

function validateDocumentUploadFile(file: File, sourceLabel: string) {
  if (file.size > AI_DOCUMENT_MAX_FILE_BYTES) {
    ElMessage.warning('文档不能超过 25MB');
    return false;
  }
  const filename = file.name.trim();
  const maxFilenameLength = AI_SOURCE_DETAIL_MAX_LENGTH - sourceLabel.length - 2;
  if (filename.length > maxFilenameLength) {
    ElMessage.warning(`文件名不能超过 ${maxFilenameLength} 个字符`);
    return false;
  }
  if (!AI_DOCUMENT_SUPPORTED_EXTENSIONS.includes(documentExtension(filename))) {
    ElMessage.warning('仅支持 txt、Word、PPT、PDF 文档');
    return false;
  }
  return true;
}

function documentExtension(filename: string) {
  const dot = filename.lastIndexOf('.');
  return dot < 0 ? '' : filename.slice(dot + 1).toLowerCase();
}

function materialQuestionTotal() {
  return Object.values(materialCounts).reduce((sum, value) => sum + Number(value || 0), 0);
}

function validateMaterialQuestionCounts() {
  const total = materialQuestionTotalValue.value;
  if (total <= 0) {
    ElMessage.warning('请至少设置一种题型数量');
    return false;
  }
  if (total > AI_MATERIAL_TOTAL_COUNT) {
    ElMessage.warning(`单次最多生成 ${AI_MATERIAL_TOTAL_COUNT} 道题`);
    return false;
  }
  return true;
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
    rememberQuestionOperationAudit('Save AI generated questions', response.data.questionReviewLogIds || response.data.questions.map((question) => question.questionReviewLogId));
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
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr) minmax(64px, auto) auto;
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

.ai-material-total {
  min-width: 64px;
  text-align: center;
  color: #4b5563;
  font-size: 13px;
  font-weight: 700;
}

.ai-material-total.is-danger {
  color: #dc2626;
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

.review-cell {
  display: grid;
  gap: 4px;
}

.review-cell small {
  overflow: hidden;
  color: #6b7280;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-drawer-head {
  display: grid;
  gap: 8px;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef2f7;
}

.review-log-item {
  display: grid;
  gap: 4px;
  color: #374151;
}

.review-log-item span,
.review-log-item small {
  color: #6b7280;
}

.review-log-item p {
  margin: 4px 0 0;
  line-height: 1.6;
}

.question-review-log-id-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.question-review-log-id-cell span {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  color: #111827;
}

.question-review-log-id-cell .el-button {
  padding: 0;
}

.question-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.question-operation-audit {
  margin-bottom: 14px;
}

.template-preview {
  display: grid;
  gap: 20px;
}

.template-description .el-alert {
  line-height: 1.8;
}

.template-description ol {
  margin: 8px 0 0;
  padding-left: 20px;
}

.template-description li {
  margin: 4px 0;
}

.template-example h4 {
  margin: 0 0 12px;
  color: #374151;
  font-size: 14px;
  font-weight: 600;
}

.template-example .el-table {
  font-size: 13px;
}

.question-operation-audit-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
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
