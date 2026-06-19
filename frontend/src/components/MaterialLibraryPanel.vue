<template>
  <section class="material-library-panel">
    <el-card shadow="never" class="material-toolbar">
      <template #header>
        <div class="material-card-header">
          <span>课程资料库</span>
          <el-button type="primary" :icon="Upload" :loading="uploading" @click="pickMaterialFile">上传资料</el-button>
        </div>
      </template>

      <div class="material-toolbar-grid">
        <el-input v-model="query.keyword" clearable placeholder="资料标题、文件名、科目" @keyup.enter="loadMaterials" />
        <el-select v-model="query.subjectId" clearable placeholder="科目">
          <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
        </el-select>
        <el-input v-model="uploadTitle" clearable maxlength="200" show-word-limit placeholder="上传标题（可选）" />
        <el-button type="primary" plain :icon="Refresh" @click="loadMaterials">刷新</el-button>
      </div>

      <input ref="materialInputRef" class="hidden-file-input" type="file" :accept="MATERIAL_DOCUMENT_ACCEPT" @change="handleMaterialFileSelected" />
    </el-card>

    <el-alert
      v-if="lastMaterialOperationAudit"
      class="material-operation-audit"
      type="success"
      show-icon
      closable
      @close="lastMaterialOperationAudit = null"
    >
      <template #title>
        <div class="material-operation-audit-content">
          <span>{{ lastMaterialOperationAudit.action }} audit recorded: {{ materialOperationAuditText(lastMaterialOperationAudit.operationLogIds) }}</span>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestMaterialOperationAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestMaterialOperationAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

    <el-table v-loading="loading" :data="materials" border class="material-table">
      <el-table-column prop="title" label="资料" min-width="220">
        <template #default="scope">
          <div class="material-title-cell">
            <strong>{{ scope.row.title }}</strong>
            <span>{{ scope.row.fileName || '-' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="subjectName" label="科目" width="150" />
      <el-table-column label="分段" width="100">
        <template #default="scope">{{ scope.row.chunkCount || 0 }}</template>
      </el-table-column>
      <el-table-column label="大纲" width="100">
        <template #default="scope">{{ scope.row.outlineCount || 0 }}</template>
      </el-table-column>
      <el-table-column prop="uploaderName" label="上传人" width="130" />
      <el-table-column label="上传时间" width="180">
        <template #default="scope">{{ formatTime(scope.row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="scope">
          <el-button link type="primary" :icon="View" @click="openMaterial(scope.row.id)">详情</el-button>
          <el-button link type="success" :icon="MagicStick" @click="openGenerateDialog(scope.row as CourseMaterial)">生成</el-button>
          <el-button link type="danger" :icon="Delete" @click="removeMaterial(scope.row as CourseMaterial)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailVisible" :title="detail?.title || '资料详情'" size="min(880px, 96vw)">
      <div v-if="detail" class="material-detail">
        <div class="material-meta">
          <el-tag>{{ detail.subjectName }}</el-tag>
          <span>{{ detail.fileName || '-' }}</span>
          <span>{{ formatTime(detail.createdAt) }}</span>
          <el-button type="success" plain :icon="MagicStick" @click="openGenerateDialog(detail)">生成题目</el-button>
        </div>

        <section>
          <h3>知识点大纲</h3>
          <el-empty v-if="!detail.outline?.length" description="暂无大纲" :image-size="80" />
          <div v-else class="outline-list">
            <article v-for="item in detail.outline" :key="item.id || item.outlineOrder" class="outline-item">
              <strong>{{ item.title }}</strong>
              <p v-if="item.summary">{{ item.summary }}</p>
              <small v-if="item.keywords">{{ item.keywords }}</small>
            </article>
          </div>
        </section>

        <section>
          <h3>资料分段</h3>
          <el-empty v-if="!detail.chunks?.length" description="暂无分段" :image-size="80" />
          <div v-else class="chunk-list">
            <article v-for="chunk in detail.chunks" :key="chunk.id || chunk.chunkOrder" class="chunk-item">
              <div>
                <el-tag size="small">page {{ chunk.pageNo }}</el-tag>
                <el-tag size="small" type="info">paragraph {{ chunk.paragraphNo }}</el-tag>
              </div>
              <strong v-if="chunk.heading">{{ chunk.heading }}</strong>
              <p>{{ chunk.content }}</p>
              <small v-if="chunk.keywords">{{ chunk.keywords }}</small>
            </article>
          </div>
        </section>
      </div>
    </el-drawer>

    <el-dialog v-model="generateVisible" :title="`资料库生成：${activeMaterial?.title || ''}`" width="960px" class="material-generate-dialog">
      <div class="generation-form">
        <el-input v-model="generationForm.knowledgePointName" clearable maxlength="128" placeholder="知识点名称（可选）" />
        <el-select v-model="generationForm.difficulty" placeholder="难度">
          <el-option v-for="item in difficulties" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input-number v-model="generationForm.defaultScore" :min="0.5" :step="0.5" :precision="1" />
        <el-input v-model="generationForm.requirements" clearable maxlength="1000" placeholder="补充要求（可选）" />
      </div>

      <div class="generation-counts">
        <label v-for="item in questionTypes" :key="item.value" class="generation-count-item">
          <span>{{ item.label }}</span>
          <el-input-number v-model="generationCounts[item.value]" :min="0" :max="AI_MATERIAL_TYPE_COUNT" controls-position="right" />
        </label>
        <div class="generation-total" :class="{ 'is-danger': generationTotalInvalid }">
          {{ generationTotalValue }} / {{ AI_MATERIAL_TOTAL_COUNT }}
        </div>
      </div>

      <div class="generation-actions">
        <el-button plain :disabled="generationDrafts.length === 0" @click="selectAllGenerationDrafts">全选</el-button>
        <el-button plain :disabled="generationDrafts.length === 0" @click="clearDraftSelection">清空</el-button>
        <el-button type="success" :icon="MagicStick" :loading="generating" :disabled="generationTotalInvalid" @click="generateLibraryDrafts">生成草稿</el-button>
        <el-button type="primary" :loading="savingDrafts" :disabled="selectedDraftCount === 0" @click="saveLibraryDrafts">保存选中 {{ selectedDraftCount }}</el-button>
      </div>

      <el-empty v-if="generationDrafts.length === 0" description="暂无生成草稿" :image-size="90" />
      <div v-else class="generation-draft-list">
        <article v-for="(draft, index) in generationDrafts" :key="index" class="generation-draft-item">
          <div class="generation-draft-head">
            <el-checkbox :model-value="selectedDraftIndexes.has(index)" @change="(checked) => toggleDraftSelection(index, checked)">入库</el-checkbox>
            <el-tag>{{ typeText(draft.questionType) }}</el-tag>
            <el-tag type="info">{{ difficultyText(draft.difficulty) }}</el-tag>
            <span>{{ draft.defaultScore }} 分</span>
          </div>
          <strong>{{ draft.stem }}</strong>
          <ol v-if="draft.options?.length">
            <li v-for="option in draft.options" :key="option.optionLabel" :class="{ 'is-correct': isCorrectOption(option.correct) }">
              {{ option.optionLabel }}. {{ option.optionContent }}
            </li>
          </ol>
          <div class="generation-answer">答案：{{ correctAnswerText(draft) }}</div>
          <p v-if="draft.analysis">{{ draft.analysis }}</p>
          <small>{{ draft.sourceDetail || 'Material library' }} · page {{ draft.sourcePage || '-' }} paragraph {{ draft.sourceParagraph || '-' }}</small>
        </article>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Delete, DocumentCopy, MagicStick, Refresh, Upload, View } from '@element-plus/icons-vue';
import { listSubjects, type SubjectInfo } from '../api/basic';
import { saveGeneratedQuestions, type AiGeneratedQuestion } from '../api/ai';
import {
  deleteCourseMaterial,
  fetchCourseMaterial,
  generateQuestionsFromLibraryMaterial,
  listCourseMaterials,
  uploadCourseMaterial,
  type CourseMaterial,
  type CourseMaterialDetail
} from '../api/material';
import type { Difficulty, QuestionType } from '../api/question';
import {
  copyOperationLogIdToClipboard,
  copyOperationLogLinkToClipboard,
  copyQuestionReviewAuditIdToClipboard,
  copyQuestionReviewAuditLinkToClipboard
} from '../utils/clipboard';

const MATERIAL_DOCUMENT_MAX_FILE_BYTES = 25 * 1024 * 1024;
const MATERIAL_TITLE_MAX_LENGTH = 200;
const MATERIAL_FILENAME_MAX_LENGTH = 255;
const MATERIAL_DOCUMENT_SUPPORTED_EXTENSIONS = ['txt', 'text', 'md', 'doc', 'docx', 'ppt', 'pptx', 'pdf'];
const MATERIAL_DOCUMENT_ACCEPT = MATERIAL_DOCUMENT_SUPPORTED_EXTENSIONS.map((extension) => `.${extension}`).join(',');
const AI_MATERIAL_TYPE_COUNT = 30;
const AI_MATERIAL_TOTAL_COUNT = 30;

const questionTypes: Array<{ label: string; value: QuestionType }> = [
  { label: '单选', value: 'SINGLE_CHOICE' },
  { label: '多选', value: 'MULTIPLE_CHOICE' },
  { label: '判断', value: 'TRUE_FALSE' },
  { label: '填空', value: 'FILL_BLANK' },
  { label: '主观', value: 'SUBJECTIVE' }
];

const difficulties: Array<{ label: string; value: Difficulty }> = [
  { label: '简单', value: 'EASY' },
  { label: '中等', value: 'MEDIUM' },
  { label: '困难', value: 'HARD' }
];

const subjects = ref<SubjectInfo[]>([]);
const materials = ref<CourseMaterial[]>([]);
const detail = ref<CourseMaterialDetail | null>(null);
const activeMaterial = ref<CourseMaterial | CourseMaterialDetail | null>(null);
const generationDrafts = ref<AiGeneratedQuestion[]>([]);
const selectedDraftIndexes = ref<Set<number>>(new Set());
const lastMaterialOperationAudit = ref<{
  action: string;
  operationLogIds: Array<number | string>;
  auditType: 'operation' | 'questionReview';
} | null>(null);
const loading = ref(false);
const uploading = ref(false);
const generating = ref(false);
const savingDrafts = ref(false);
const detailVisible = ref(false);
const generateVisible = ref(false);
const materialInputRef = ref<HTMLInputElement | null>(null);
const uploadTitle = ref('');
const query = reactive<{ keyword: string; subjectId: number | null }>({
  keyword: '',
  subjectId: null
});
const generationForm = reactive<{
  knowledgePointName: string;
  difficulty: Difficulty;
  defaultScore: number;
  requirements: string;
}>({
  knowledgePointName: '',
  difficulty: 'MEDIUM',
  defaultScore: 5,
  requirements: ''
});
const generationCounts = reactive<Record<QuestionType, number>>({
  SINGLE_CHOICE: 2,
  MULTIPLE_CHOICE: 1,
  TRUE_FALSE: 1,
  FILL_BLANK: 1,
  SUBJECTIVE: 0
});
const generationTotalValue = computed(() => materialQuestionTotal());
const generationTotalInvalid = computed(() => generationTotalValue.value <= 0 || generationTotalValue.value > AI_MATERIAL_TOTAL_COUNT);
const selectedDraftCount = computed(() => selectedDraftIndexes.value.size);
const selectedGenerationDrafts = computed(() => generationDrafts.value.filter((_, index) => selectedDraftIndexes.value.has(index)));

onMounted(async () => {
  await Promise.all([loadSubjects(), loadMaterials()]);
});

async function loadSubjects() {
  const response = await listSubjects();
  subjects.value = response.data;
}

async function loadMaterials() {
  loading.value = true;
  try {
    const response = await listCourseMaterials({
      keyword: query.keyword.trim(),
      subjectId: query.subjectId
    });
    materials.value = response.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料列表加载失败');
  } finally {
    loading.value = false;
  }
}

function rememberMaterialOperationAudit(
  action: string,
  ids: Array<number | string | null | undefined>,
  auditType: 'operation' | 'questionReview' = 'operation'
) {
  const operationLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (operationLogIds.length === 0) return;
  lastMaterialOperationAudit.value = { action, operationLogIds, auditType };
}

function materialOperationAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestMaterialOperationAuditId() {
  const audit = lastMaterialOperationAudit.value;
  if (!audit?.operationLogIds.length) return;
  try {
    if (audit.auditType === 'questionReview') {
      await copyQuestionReviewAuditIdToClipboard(audit.operationLogIds.join(','));
    } else {
      await copyOperationLogIdToClipboard(audit.operationLogIds.join(','));
    }
    ElMessage.success('Audit ID copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit ID copy failed');
  }
}

async function copyLatestMaterialOperationAuditLink() {
  const audit = lastMaterialOperationAudit.value;
  const id = audit?.operationLogIds[0];
  if (!audit || !id) return;
  try {
    if (audit.auditType === 'questionReview') {
      await copyQuestionReviewAuditLinkToClipboard(id);
    } else {
      await copyOperationLogLinkToClipboard(id);
    }
    ElMessage.success('Audit link copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit link copy failed');
  }
}

function pickMaterialFile() {
  if (!query.subjectId) {
    ElMessage.warning('请先选择科目');
    return;
  }
  if (uploadTitle.value.trim().length > MATERIAL_TITLE_MAX_LENGTH) {
    ElMessage.warning(`资料标题不能超过 ${MATERIAL_TITLE_MAX_LENGTH} 个字符`);
    return;
  }
  materialInputRef.value?.click();
}

async function handleMaterialFileSelected(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0] || null;
  input.value = '';
  if (!file || !query.subjectId) return;
  if (!validateMaterialUploadFile(file)) return;

  uploading.value = true;
  try {
    const response = await uploadCourseMaterial(file, query.subjectId, uploadTitle.value.trim() || undefined);
    detail.value = response.data;
    rememberMaterialOperationAudit('Upload material', [response.data.operationLogId]);
    detailVisible.value = true;
    uploadTitle.value = '';
    await loadMaterials();
    ElMessage.success('资料已上传并生成大纲');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料上传失败');
  } finally {
    uploading.value = false;
  }
}

function validateMaterialUploadFile(file: File) {
  if (file.size > MATERIAL_DOCUMENT_MAX_FILE_BYTES) {
    ElMessage.warning('文档不能超过 25MB');
    return false;
  }
  const filename = file.name.trim();
  if (filename.length > MATERIAL_FILENAME_MAX_LENGTH) {
    ElMessage.warning(`文件名不能超过 ${MATERIAL_FILENAME_MAX_LENGTH} 个字符`);
    return false;
  }
  if (!MATERIAL_DOCUMENT_SUPPORTED_EXTENSIONS.includes(documentExtension(filename))) {
    ElMessage.warning('仅支持 txt、Word、PPT、PDF 文档');
    return false;
  }
  return true;
}

function documentExtension(filename: string) {
  const dot = filename.lastIndexOf('.');
  return dot < 0 ? '' : filename.slice(dot + 1).toLowerCase();
}

async function openMaterial(id: number) {
  detailVisible.value = true;
  detail.value = null;
  try {
    const response = await fetchCourseMaterial(id);
    detail.value = response.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料详情加载失败');
  }
}

async function removeMaterial(material: CourseMaterial) {
  try {
    await ElMessageBox.confirm(`确认删除资料「${material.title}」吗？`, '删除资料', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    const response = await deleteCourseMaterial(material.id);
    rememberMaterialOperationAudit('Delete material', [response.data.operationLogId]);
    if (detail.value?.id === material.id) {
      detail.value = null;
      detailVisible.value = false;
    }
    if (activeMaterial.value?.id === material.id) {
      activeMaterial.value = null;
      generationDrafts.value = [];
      selectedDraftIndexes.value = new Set();
      generateVisible.value = false;
    }
    await loadMaterials();
    ElMessage.success('资料已删除');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料删除失败');
  }
}

function openGenerateDialog(material: CourseMaterial | CourseMaterialDetail) {
  activeMaterial.value = material;
  generationDrafts.value = [];
  selectedDraftIndexes.value = new Set();
  generateVisible.value = true;
}

async function generateLibraryDrafts() {
  if (!activeMaterial.value || !validateGenerationCounts()) return;
  generating.value = true;
  try {
    const response = await generateQuestionsFromLibraryMaterial(activeMaterial.value.id, {
      knowledgePointName: generationForm.knowledgePointName.trim() || null,
      difficulty: generationForm.difficulty,
      defaultScore: Number(generationForm.defaultScore || 5),
      requirements: generationForm.requirements.trim(),
      typeCounts: { ...generationCounts }
    });
    generationDrafts.value = response.data.questions;
    rememberMaterialOperationAudit('Generate material drafts', [response.data.operationLogId]);
    selectAllGenerationDrafts();
    ElMessage.success(`已生成 ${generationDrafts.value.length} 道资料库草稿`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料库生成失败');
  } finally {
    generating.value = false;
  }
}

async function saveLibraryDrafts() {
  if (selectedGenerationDrafts.value.length === 0) {
    ElMessage.warning('请选择要保存的草稿');
    return;
  }
  savingDrafts.value = true;
  try {
    const response = await saveGeneratedQuestions(selectedGenerationDrafts.value.map((draft) => ({ ...draft, status: 0 })));
    rememberMaterialOperationAudit(
      'Save material drafts',
      response.data.questionReviewLogIds || response.data.questions.map((question) => question.questionReviewLogId),
      'questionReview'
    );
    ElMessage.success(`已保存 ${response.data.savedCount} 道资料库草稿`);
    generationDrafts.value = [];
    selectedDraftIndexes.value = new Set();
    generateVisible.value = false;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '资料库草稿保存失败');
  } finally {
    savingDrafts.value = false;
  }
}

function toggleDraftSelection(index: number, checked: string | number | boolean) {
  const next = new Set(selectedDraftIndexes.value);
  if (Boolean(checked)) {
    next.add(index);
  } else {
    next.delete(index);
  }
  selectedDraftIndexes.value = next;
}

function selectAllGenerationDrafts() {
  selectedDraftIndexes.value = new Set(generationDrafts.value.map((_, index) => index));
}

function clearDraftSelection() {
  selectedDraftIndexes.value = new Set();
}

function materialQuestionTotal() {
  return Object.values(generationCounts).reduce((sum, value) => sum + Number(value || 0), 0);
}

function validateGenerationCounts() {
  const total = generationTotalValue.value;
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

function correctAnswerText(draft: AiGeneratedQuestion) {
  if (draft.options?.length) {
    const labels = draft.options
      .filter((option) => isCorrectOption(option.correct))
      .map((option) => option.optionLabel)
      .join('');
    return labels || draft.correctAnswer || '-';
  }
  return draft.correctAnswer || '-';
}

function isCorrectOption(value: boolean | number) {
  return value === true || value === 1;
}

function typeText(type: QuestionType) {
  const map: Record<QuestionType, string> = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SUBJECTIVE: '主观'
  };
  return map[type] || type;
}

function difficultyText(difficulty: Difficulty) {
  const map: Record<Difficulty, string> = {
    EASY: '简单',
    MEDIUM: '中等',
    HARD: '困难'
  };
  return map[difficulty] || difficulty;
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}
</script>

<style scoped>
.material-library-panel {
  display: grid;
  gap: 16px;
}

.material-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.material-toolbar-grid {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(160px, 220px) minmax(180px, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.hidden-file-input {
  display: none;
}

.material-table {
  width: 100%;
}

.material-operation-audit {
  border: 1px solid #bbf7d0;
}

.material-operation-audit-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.material-title-cell {
  display: grid;
  gap: 4px;
}

.material-title-cell span,
.material-meta,
.outline-item small,
.chunk-item small {
  color: #6b7280;
}

.material-detail {
  display: grid;
  gap: 22px;
}

.material-detail h3 {
  margin: 0 0 12px;
  color: #111827;
  font-size: 16px;
}

.material-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.outline-list,
.chunk-list {
  display: grid;
  gap: 10px;
}

.outline-item,
.chunk-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.outline-item p,
.chunk-item p {
  margin: 0;
  color: #374151;
  line-height: 1.65;
}

.chunk-item > div {
  display: flex;
  gap: 6px;
}

.generation-form {
  display: grid;
  grid-template-columns: minmax(160px, 1fr) 140px 120px minmax(220px, 1.2fr);
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.generation-counts {
  display: grid;
  grid-template-columns: repeat(5, minmax(96px, 1fr)) minmax(72px, auto);
  gap: 10px;
  align-items: end;
  margin-bottom: 14px;
}

.generation-count-item {
  display: grid;
  gap: 5px;
  color: #4b5563;
  font-size: 12px;
}

.generation-count-item :deep(.el-input-number) {
  width: 100%;
}

.generation-total {
  min-width: 72px;
  padding-bottom: 8px;
  color: #4b5563;
  font-weight: 700;
  text-align: center;
}

.generation-total.is-danger {
  color: #dc2626;
}

.generation-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-bottom: 14px;
}

.generation-draft-list {
  display: grid;
  gap: 12px;
  max-height: 52vh;
  overflow: auto;
  padding-right: 4px;
}

.generation-draft-item {
  display: grid;
  gap: 9px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.generation-draft-head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  color: #6b7280;
  font-size: 13px;
}

.generation-draft-item strong {
  color: #111827;
  line-height: 1.55;
}

.generation-draft-item ol {
  margin: 0;
  padding-left: 20px;
  color: #374151;
  line-height: 1.6;
}

.generation-draft-item li.is-correct {
  color: #047857;
  font-weight: 700;
}

.generation-answer {
  color: #047857;
  font-size: 13px;
  font-weight: 700;
}

.generation-draft-item p {
  margin: 0;
  color: #4b5563;
  line-height: 1.65;
}

.generation-draft-item small {
  color: #6b7280;
}

@media (max-width: 900px) {
  .material-toolbar-grid,
  .generation-form,
  .generation-counts {
    grid-template-columns: 1fr;
  }

  .generation-actions {
    justify-content: stretch;
  }

  .generation-actions .el-button {
    flex: 1;
  }
}
</style>
