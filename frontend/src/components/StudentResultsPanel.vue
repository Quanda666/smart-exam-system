<template>
  <section class="student-results-page mp-page">
    <div class="mp-page-header">
      <div>
        <h3 class="mp-page-title">成绩查询</h3>
      </div>
      <div class="mp-page-actions">
        <el-button :icon="Refresh" :loading="loading" @click="loadGrades(true)">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="lastStudentScoreAppealAudit"
      class="student-score-appeal-audit"
      type="success"
      :closable="true"
      show-icon
      @close="lastStudentScoreAppealAudit = null"
    >
      <template #title>
        <div class="student-score-appeal-audit-content">
          <span>{{ lastStudentScoreAppealAudit.action }} audit recorded: {{ studentScoreAppealAuditText(lastStudentScoreAppealAudit.scoreAppealLogIds) }}</span>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestStudentScoreAppealAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestStudentScoreAppealAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

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
      <el-table v-loading="loading" :data="grades" border>
        <el-table-column prop="examName" label="考试名称" min-width="220" />
        <el-table-column prop="subjectName" label="科目" width="150" />
        <el-table-column label="分数" width="120">
          <template #default="scope">
            <strong>{{ scoreText(scope.row as GradeInfo) }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="Answer stats" min-width="180">
          <template #default="scope">{{ answerStatsText(scope.row as GradeInfo) }}</template>
        </el-table-column>
        <el-table-column label="成绩状态" min-width="190">
          <template #default="scope">
            <div class="score-release-cell">
              <el-tag :type="scoreVisibilityType(scope.row as GradeInfo)">{{ scoreVisibilityText(scope.row as GradeInfo) }}</el-tag>
              <span v-if="scoreVisibilityHint(scope.row as GradeInfo)">{{ scoreVisibilityHint(scope.row as GradeInfo) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="scope">
            <el-tag :type="scope.row.status === 5 ? 'success' : 'warning'">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.submitTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="scope">
            <el-tooltip :content="scoreVisibilityHint(scope.row as GradeInfo) || '查看答题详情'" placement="top" :disabled="canViewResult(scope.row as GradeInfo)">
              <span>
                <el-button
                  link
                  type="primary"
                  :icon="View"
                  :disabled="!canViewResult(scope.row as GradeInfo)"
                  @click="viewResult(scope.row.attemptId)"
                >
                  详情
                </el-button>
              </span>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && grades.length === 0" description="暂无成绩记录" />
    </div>

    <el-drawer v-model="drawerVisible" title="考试结果详情" direction="rtl" size="52%">
      <div v-if="examResult" class="result-drawer">
        <el-descriptions :title="examResult.gradeInfo.examName" :column="2" border>
          <el-descriptions-item label="总分">{{ scoreText(examResult.gradeInfo) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusText(examResult.gradeInfo.status) }}</el-descriptions-item>
          <el-descriptions-item label="科目">{{ examResult.gradeInfo.subjectName }}</el-descriptions-item>
          <el-descriptions-item label="交卷时间">{{ formatDateTime(examResult.gradeInfo.submitTime) }}</el-descriptions-item>
          <el-descriptions-item label="Answer stats">{{ answerStatsText(examResult.gradeInfo) }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <div class="appeal-summary">
          <strong>成绩申诉</strong>
          <div class="appeal-actions">
            <span v-if="appealWindowHint(examResult.gradeInfo)">{{ appealWindowHint(examResult.gradeInfo) }}</span>
            <el-tooltip :content="appealSubmitDisabledReason(examResult.gradeInfo, null) || '提交整卷申诉'" placement="top" :disabled="canSubmitAppealFor(examResult.gradeInfo, null)">
              <span>
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :icon="EditPen"
                  :disabled="!canSubmitAppealFor(examResult.gradeInfo, null)"
                  @click="openAppeal()"
                >
                  整卷申诉
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
        <el-table v-if="currentAppeals.length" :data="currentAppeals" size="small" border class="appeal-table">
          <el-table-column label="申诉对象" min-width="180">
            <template #default="scope">{{ scope.row.questionStem || '整张试卷' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="scope">
              <el-tag :type="appealStatusType(scope.row.status)">{{ appealStatusText(scope.row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="处理结果" width="110">
            <template #default="scope">{{ appealHandlingResultText(scope.row.handlingResult) }}</template>
          </el-table-column>
          <el-table-column prop="teacherReply" label="处理意见" min-width="180" />
          <el-table-column prop="recheckNote" label="复核说明" min-width="180">
            <template #default="scope">{{ scope.row.recheckNote || '-' }}</template>
          </el-table-column>
          <el-table-column label="Audit" width="130">
            <template #default="scope">
              <div v-if="scope.row.scoreAppealLogId" class="student-appeal-audit-id-cell">
                <span>#{{ scope.row.scoreAppealLogId }}</span>
                <el-button
                  link
                  type="primary"
                  :icon="DocumentCopy"
                  title="Copy score appeal audit ID"
                  aria-label="Copy score appeal audit ID"
                  @click="copyStudentAppealRowAuditId(scope.row.scoreAppealLogId)"
                />
                <el-button
                  link
                  type="primary"
                  :icon="DocumentCopy"
                  title="Copy score appeal audit link"
                  aria-label="Copy score appeal audit link"
                  @click="copyStudentAppealRowAuditLink(scope.row.scoreAppealLogId)"
                />
              </div>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="166">
            <template #default="scope">
              <el-button link type="primary" :icon="View" @click="openStudentAppealLogs(scope.row as ScoreAppeal)">Logs</el-button>
              <el-button
                v-if="canViewAppealEvidence(scope.row as ScoreAppeal)"
                link
                type="success"
                :icon="View"
                @click="openStudentAppealEvidence(scope.row as ScoreAppeal)"
              >
                Evidence
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <div v-for="(answer, index) in examResult.answers" :key="index" class="answer-block">
          <p>
            <strong>{{ index + 1 }}. {{ answer.stem }}</strong>
            <el-tag size="small">{{ typeText(answer.questionType) }}</el-tag>
          </p>
          <div v-if="answer.options?.length" class="result-option-list">
            <div
              v-for="option in answer.options"
              :key="option.optionLabel"
              :class="['result-option-item', { correct: isCorrectValue(option.correct ?? option.isCorrect) }]"
            >
              <strong>{{ option.optionLabel }}.</strong>
              <span>{{ option.optionContent }}</span>
            </div>
          </div>
          <p :class="answer.isCorrect ? 'text-success' : 'text-danger'">你的答案：{{ answer.studentAnswer || '未作答' }}</p>
          <p>正确答案：{{ answer.correctAnswer || '待批阅' }}</p>
          <p>得分：{{ answer.score }}</p>
          <p v-if="answer.analysis">解析：{{ answer.analysis }}</p>
          <div class="answer-actions">
            <el-tag v-if="appealFor(answer.questionId)" size="small" :type="appealStatusType(appealFor(answer.questionId)?.status)">
              {{ appealStatusText(appealFor(answer.questionId)?.status) }}
            </el-tag>
            <el-tooltip :content="appealSubmitDisabledReason(examResult.gradeInfo, answer.questionId) || '提交本题申诉'" placement="top" :disabled="canSubmitAppealFor(examResult.gradeInfo, answer.questionId)">
              <span>
                <el-button
                  size="small"
                  text
                  type="primary"
                  :icon="EditPen"
                  :disabled="!canSubmitAppealFor(examResult.gradeInfo, answer.questionId)"
                  @click="openAppeal(answer)"
                >
                  申诉本题
                </el-button>
              </span>
            </el-tooltip>
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="appealDialogVisible" title="提交成绩申诉" width="520px">
      <el-form label-position="top">
        <el-form-item label="申诉对象">
          <el-input :model-value="appealTargetLabel" disabled />
        </el-form-item>
        <el-form-item label="申诉原因" required>
          <el-input v-model="appealForm.reason" type="textarea" :rows="5" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="appealDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="appealSubmitting" @click="submitAppeal">提交</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="appealLogVisible" title="Score appeal logs" size="min(720px, 94vw)">
      <div v-if="activeLogAppeal" class="student-appeal-log-head">
        <strong>{{ activeLogAppeal.examName }}</strong>
        <span>{{ activeLogAppeal.questionStem || 'Whole paper appeal' }}</span>
      </div>
      <div class="student-appeal-log-toolbar">
        <el-button type="success" plain :loading="appealLogExporting" :disabled="!activeLogAppeal" @click="exportStudentAppealLogs">Export</el-button>
      </div>
      <el-table v-loading="appealLogLoading" :data="appealLogs" border>
        <el-table-column label="Log ID" width="116">
          <template #default="scope">
            <div class="student-appeal-audit-id-cell">
              <span>#{{ scope.row.id }}</span>
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy score appeal audit ID"
                aria-label="Copy score appeal audit ID"
                @click="copyStudentAppealRowAuditId(scope.row.id)"
              />
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy score appeal audit link"
                aria-label="Copy score appeal audit link"
                @click="copyStudentAppealRowAuditLink(scope.row.id)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Action" width="126">
          <template #default="scope">{{ appealLogActionText(scope.row.action) }}</template>
        </el-table-column>
        <el-table-column label="Status" width="128">
          <template #default="scope">{{ appealLogStatusText(scope.row.statusFrom) }} -> {{ appealLogStatusText(scope.row.statusTo) }}</template>
        </el-table-column>
        <el-table-column label="Result" width="132">
          <template #default="scope">{{ appealHandlingResultText(scope.row.handlingResult) }}</template>
        </el-table-column>
        <el-table-column prop="note" label="Note" min-width="180" show-overflow-tooltip />
        <el-table-column label="Time" width="170">
          <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!appealLogLoading && appealLogs.length === 0" description="No score appeal logs" />
    </el-drawer>

    <el-drawer v-model="appealEvidenceVisible" title="Recheck Evidence" size="min(900px, 96vw)">
      <div v-loading="appealEvidenceLoading">
        <template v-if="appealEvidence">
          <div class="student-appeal-evidence-head">
            <div>
              <strong>{{ appealEvidence.examName }}</strong>
              <span>{{ appealEvidence.questionStem || 'Whole paper appeal' }}</span>
            </div>
            <el-tag type="success">Evidence available</el-tag>
          </div>

          <div class="student-appeal-evidence-stats">
            <div>
              <span>Required</span>
              <strong>{{ appealEvidence.requiredRecheckAnswerCount || 0 }}</strong>
            </div>
            <div>
              <span>Reviewed</span>
              <strong>{{ appealEvidence.reviewedRecheckAnswerCount || 0 }}</strong>
            </div>
            <div>
              <span>Pending</span>
              <strong>{{ appealEvidence.pendingRecheckAnswerCount || 0 }}</strong>
            </div>
            <div>
              <span>Score Logs</span>
              <strong>{{ appealEvidence.reviewScoreLogCount || 0 }}</strong>
            </div>
          </div>

          <el-descriptions :column="1" border class="student-appeal-evidence-summary">
            <el-descriptions-item label="Handling Result">{{ appealHandlingResultText(appealEvidence.handlingResult) }}</el-descriptions-item>
            <el-descriptions-item label="Teacher Reply">{{ appealEvidence.teacherReply || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Recheck Note">{{ appealEvidence.recheckNote || '-' }}</el-descriptions-item>
            <el-descriptions-item label="Recheck Opened">{{ formatDateTime(appealEvidence.recheckOpenedAt) }}</el-descriptions-item>
            <el-descriptions-item label="Rechecked At">{{ formatDateTime(appealEvidence.recheckedAt) }}</el-descriptions-item>
          </el-descriptions>

          <el-table :data="appealEvidence.answers || []" border class="student-appeal-evidence-table">
            <el-table-column prop="questionId" label="Question" width="92" />
            <el-table-column label="Status" width="100">
              <template #default="scope">{{ appealEvidenceAnswerStatusText(scope.row as ScoreAppealEvidenceAnswer) }}</template>
            </el-table-column>
            <el-table-column label="Score" width="160">
              <template #default="scope">
                {{ scope.row.oldScore ?? '-' }} -> {{ scope.row.newScore ?? scope.row.currentScore ?? '-' }} / {{ scope.row.maxScore ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="Audit" width="100">
              <template #default="scope">
                <span v-if="scope.row.reviewScoreLogId">#{{ scope.row.reviewScoreLogId }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="reviewerName" label="Reviewer" width="120">
              <template #default="scope">{{ scope.row.reviewerName || scope.row.reviewerId || '-' }}</template>
            </el-table-column>
            <el-table-column label="Reviewed At" min-width="150">
              <template #default="scope">{{ formatDateTime(scope.row.reviewedAt) }}</template>
            </el-table-column>
            <el-table-column prop="stem" label="Stem" min-width="220" show-overflow-tooltip />
          </el-table>
        </template>
        <el-empty v-else-if="!appealEvidenceLoading" description="No recheck evidence" />
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { DocumentCopy, EditPen, Refresh, View } from '@element-plus/icons-vue';
import { useRoute } from 'vue-router';
import {
  getExamResult,
  getGrades,
  getMyScoreAppeals,
  getMyScoreAppealEvidence,
  getMyScoreAppealLogs,
  exportMyScoreAppealLogs,
  submitScoreAppeal,
  type ExamResult,
  type GradeInfo,
  type ScoreAppeal,
  type ScoreAppealEvidence,
  type ScoreAppealEvidenceAnswer,
  type ScoreAppealLog
} from '../api/student';
import {
  copyScoreAppealAuditIdToClipboard,
  copyScoreAppealAuditLinkToClipboard
} from '../utils/clipboard';
import { formatDateTime } from '../utils/dateFormat';

const route = useRoute();
const loading = ref(false);
const grades = ref<GradeInfo[]>([]);
const drawerVisible = ref(false);
const examResult = ref<ExamResult | null>(null);
const appeals = ref<ScoreAppeal[]>([]);
const appealDialogVisible = ref(false);
const appealSubmitting = ref(false);
const appealLogVisible = ref(false);
const appealLogLoading = ref(false);
const appealLogExporting = ref(false);
const appealLogs = ref<ScoreAppealLog[]>([]);
const activeLogAppeal = ref<ScoreAppeal | null>(null);
const appealEvidenceVisible = ref(false);
const appealEvidenceLoading = ref(false);
const appealEvidence = ref<ScoreAppealEvidence | null>(null);
const focusedRouteAppealId = ref<number | null>(null);
const focusedRouteAttemptId = ref<number | null>(null);
const appealForm = reactive<{ attemptId: number | null; questionId: number | null; stem: string; reason: string }>({
  attemptId: null,
  questionId: null,
  stem: '',
  reason: ''
});
const lastStudentScoreAppealAudit = ref<{ action: string; scoreAppealLogIds: Array<number | string> } | null>(null);

const summaryCards = computed(() => {
  const released = grades.value.filter((item) => canViewResult(item));
  const pending = grades.value.filter((item) => !canViewResult(item));
  const average = released.length
    ? (released.reduce((sum, item) => sum + Number(item.score || 0), 0) / released.length).toFixed(1)
    : '—';
  return [
    { label: '记录数', value: grades.value.length, remark: '已提交考试', className: '' },
    { label: '已发布', value: released.length, remark: '可查看成绩', className: 'mp-val-ok' },
    { label: '待发布', value: pending.length, remark: '批阅/发布中', className: 'mp-val-warn' },
    { label: '申诉中', value: appeals.value.filter((item) => item.status === 0).length, remark: '待教师处理', className: 'mp-val-warn' },
    { label: '平均分', value: average, remark: '已出分考试', className: '' }
  ];
});

const currentAppeals = computed(() => {
  const attemptId = examResult.value?.gradeInfo.attemptId;
  return attemptId ? appeals.value.filter((item) => item.attemptId === attemptId) : [];
});

const appealTargetLabel = computed(() => appealForm.stem || '整张试卷');

onMounted(async () => {
  await Promise.all([loadGrades(), loadAppeals()]);
  await applyAppealRouteFocus();
  await applyAttemptRouteFocus();
});

watch(
  () => route.query.appealId,
  () => {
    applyAppealRouteFocus();
  }
);

watch(
  () => route.query.attemptId,
  () => {
    applyAttemptRouteFocus();
  }
);

async function loadGrades(manual = false) {
  loading.value = true;
  try {
    grades.value = (await getGrades()).data;
    if (manual) {
      ElMessage.success('成绩已刷新');
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成绩列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadAppeals() {
  try {
    appeals.value = (await getMyScoreAppeals()).data;
  } catch {
    appeals.value = [];
  }
}

async function applyAppealRouteFocus() {
  if (route.path !== '/student/results') return;
  const appealId = normalizedRouteAppealId();
  if (!appealId || focusedRouteAppealId.value === appealId) return;
  focusedRouteAppealId.value = appealId;
  if (appeals.value.length === 0) {
    await loadAppeals();
  }
  const appeal = appeals.value.find((item) => Number(item.id) === appealId);
  if (!appeal) {
    ElMessage.warning('Score appeal not found or no longer accessible');
    return;
  }
  await viewResult(appeal.attemptId);
  const refreshedAppeal = appeals.value.find((item) => Number(item.id) === appealId) || appeal;
  if (canViewAppealEvidence(refreshedAppeal)) {
    await openStudentAppealEvidence(refreshedAppeal);
  } else {
    await openStudentAppealLogs(refreshedAppeal);
  }
}

async function applyAttemptRouteFocus() {
  if (route.path !== '/student/results' || normalizedRouteAppealId()) return;
  const attemptId = normalizedRouteAttemptId();
  if (!attemptId || focusedRouteAttemptId.value === attemptId) return;
  focusedRouteAttemptId.value = attemptId;
  if (grades.value.length === 0) {
    await loadGrades();
  }
  const grade = grades.value.find((item) => Number(item.attemptId) === attemptId);
  if (!grade) {
    ElMessage.warning('Result record not found or no longer accessible');
    return;
  }
  await viewResult(attemptId);
}

function normalizedRouteAppealId() {
  const raw = Array.isArray(route.query.appealId) ? route.query.appealId[0] : route.query.appealId;
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function normalizedRouteAttemptId() {
  const raw = Array.isArray(route.query.attemptId) ? route.query.attemptId[0] : route.query.attemptId;
  const parsed = Number(raw);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

async function openStudentAppealLogs(row: ScoreAppeal) {
  activeLogAppeal.value = row;
  appealLogs.value = [];
  appealLogVisible.value = true;
  appealLogLoading.value = true;
  try {
    appealLogs.value = (await getMyScoreAppealLogs(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Score appeal logs failed to load');
  } finally {
    appealLogLoading.value = false;
  }
}

async function exportStudentAppealLogs() {
  if (!activeLogAppeal.value) return;
  appealLogExporting.value = true;
  try {
    await exportMyScoreAppealLogs(activeLogAppeal.value.id, activeLogAppeal.value.examName);
    ElMessage.success('Score appeal log export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Export failed');
  } finally {
    appealLogExporting.value = false;
  }
}

async function openStudentAppealEvidence(row: ScoreAppeal) {
  appealEvidence.value = null;
  appealEvidenceVisible.value = true;
  appealEvidenceLoading.value = true;
  try {
    appealEvidence.value = (await getMyScoreAppealEvidence(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Recheck evidence failed to load');
  } finally {
    appealEvidenceLoading.value = false;
  }
}

async function viewResult(attemptId: number) {
  const grade = grades.value.find((item) => item.attemptId === attemptId);
  if (grade && !canViewResult(grade)) {
    ElMessage.warning(scoreVisibilityHint(grade) || '成绩暂不可查看');
    return;
  }
  try {
    examResult.value = (await getExamResult(attemptId)).data;
    await loadAppeals();
    drawerVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '查看结果失败');
  }
}

function openAppeal(answer?: ExamResult['answers'][number]) {
  if (!examResult.value) return;
  const questionId = answer?.questionId ?? null;
  if (!canSubmitAppealFor(examResult.value.gradeInfo, questionId)) {
    ElMessage.warning(appealSubmitDisabledReason(examResult.value.gradeInfo, questionId) || '当前不能提交成绩申诉');
    return;
  }
  appealForm.attemptId = examResult.value.gradeInfo.attemptId;
  appealForm.questionId = questionId;
  appealForm.stem = answer?.stem || '';
  appealForm.reason = '';
  appealDialogVisible.value = true;
}

async function submitAppeal() {
  if (!appealForm.attemptId) return;
  if (!appealForm.reason.trim()) {
    ElMessage.warning('请填写申诉原因');
    return;
  }
  appealSubmitting.value = true;
  try {
    const response = await submitScoreAppeal({
      attemptId: appealForm.attemptId,
      questionId: appealForm.questionId,
      reason: appealForm.reason.trim()
    });
    rememberStudentScoreAppealAudit('Submit appeal', response.data.scoreAppealLogIds || []);
    ElMessage.success(`Appeal submitted${studentScoreAppealLogSuffix(response.data.scoreAppealLogIds)}`);
    appealDialogVisible.value = false;
    await loadAppeals();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交失败');
  } finally {
    appealSubmitting.value = false;
  }
}

function studentScoreAppealLogSuffix(logIds?: number[]) {
  if (!logIds || logIds.length === 0) return '';
  const visibleIds = logIds.slice(0, 3).map((id) => `#${id}`).join(', ');
  return `; appeal audit logs ${visibleIds}${logIds.length > 3 ? ` +${logIds.length - 3}` : ''}`;
}

function rememberStudentScoreAppealAudit(action: string, ids: Array<number | string | null | undefined>) {
  const scoreAppealLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (scoreAppealLogIds.length === 0) return;
  lastStudentScoreAppealAudit.value = { action, scoreAppealLogIds };
}

function studentScoreAppealAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestStudentScoreAppealAuditId() {
  const ids = lastStudentScoreAppealAudit.value?.scoreAppealLogIds;
  if (!ids?.length) return;
  try {
    await copyScoreAppealAuditIdToClipboard(ids.join(','));
    ElMessage.success('Score appeal audit ID copied');
  } catch {
    ElMessage.error('Failed to copy score appeal audit ID');
  }
}

async function copyLatestStudentScoreAppealAuditLink() {
  const id = lastStudentScoreAppealAudit.value?.scoreAppealLogIds[0];
  if (!id) return;
  try {
    await copyScoreAppealAuditLinkToClipboard(id);
    ElMessage.success('Score appeal audit link copied');
  } catch {
    ElMessage.error('Failed to copy score appeal audit link');
  }
}

async function copyStudentAppealRowAuditId(id?: number | string | null) {
  if (!id) return;
  try {
    const value = await copyScoreAppealAuditIdToClipboard(id);
    ElMessage.success(`Score appeal audit ID copied: ${value}`);
  } catch {
    ElMessage.error('Failed to copy score appeal audit ID');
  }
}

async function copyStudentAppealRowAuditLink(id?: number | string | null) {
  if (!id) return;
  try {
    await copyScoreAppealAuditLinkToClipboard(id);
    ElMessage.success('Score appeal audit link copied');
  } catch {
    ElMessage.error('Failed to copy score appeal audit link');
  }
}

function appealFor(questionId?: number | null) {
  if (!questionId) return null;
  return currentAppeals.value.find((item) => item.questionId === questionId) || null;
}

function canViewAppealEvidence(row?: ScoreAppeal | null) {
  return row?.status === 2 && row.handlingResult === 'RECHECK_REQUIRED';
}

function appealEvidenceAnswerStatusText(answer: ScoreAppealEvidenceAnswer) {
  return Number(answer.reviewStatus || 0) === 1 ? 'Reviewed' : 'Pending';
}

function canViewResult(grade: GradeInfo) {
  return grade.scoreVisible === true && grade.scoreVisibility === 'RELEASED' && grade.status === 5;
}

function scoreText(grade: GradeInfo | number | string | null | undefined) {
  if (typeof grade === 'number' || typeof grade === 'string' || grade === null || grade === undefined) {
    return grade === null || grade === undefined ? '-' : grade;
  }
  return canViewResult(grade) ? grade.score : '-';
}

function answerStatsText(grade?: GradeInfo | null) {
  if (!grade) return '-';
  const questionTotal = Number(grade.questionCount);
  const answered = Number(grade.answeredCount);
  const unanswered = Number(grade.unansweredCount);
  if (!Number.isFinite(questionTotal) || !Number.isFinite(answered) || !Number.isFinite(unanswered)) {
    return '-';
  }
  return `${answered}/${questionTotal} answered, ${unanswered} unanswered`;
}

function scoreVisibilityText(grade: GradeInfo) {
  if (canViewResult(grade)) return '已发布';
  if (grade.scoreVisibility === 'REVOKED') return '已撤回';
  if (grade.scoreVisibility === 'PENDING_REVIEW') return '待批阅';
  if (grade.scoreVisibility === 'PENDING_RECHECK') return '复核中';
  if (grade.scoreVisibility === 'PENDING_FINALIZE') return '待判分';
  if (grade.scoreVisibility === 'PENDING_SCORE') return '成绩待确认';
  return '待发布';
}

function scoreVisibilityType(grade: GradeInfo) {
  if (canViewResult(grade)) return 'success';
  if (grade.scoreVisibility === 'REVOKED') return 'warning';
  if (grade.scoreVisibility === 'PENDING_REVIEW') return 'warning';
  if (grade.scoreVisibility === 'PENDING_RECHECK') return 'warning';
  return 'info';
}

function scoreVisibilityHint(grade: GradeInfo) {
  if (canViewResult(grade)) {
    return grade.scorePublishedAt ? `发布时间：${formatDateTime(grade.scorePublishedAt)}` : '';
  }
  if (grade.scoreVisibility === 'REVOKED') {
    return grade.scoreRevokeReason ? `撤回原因：${grade.scoreRevokeReason}` : '成绩已撤回，暂不可查看详情';
  }
  if (grade.scoreVisibility === 'PENDING_REVIEW') return '答卷仍在批阅中';
  if (grade.scoreVisibility === 'PENDING_RECHECK') return '成绩复核尚未完成';
  if (grade.scoreVisibility === 'PENDING_FINALIZE') return '答卷仍在判分处理中';
  if (grade.scoreVisibility === 'PENDING_SCORE') return '成绩数据正在确认，暂不可查看详情';
  return '教师发布成绩后可查看分数和答题详情';
}

function canSubmitAppeal(grade?: GradeInfo | null) {
  return Boolean(grade && canViewResult(grade) && grade.appealOpen === true);
}

function canSubmitAppealFor(grade?: GradeInfo | null, questionId?: number | null) {
  return canSubmitAppeal(grade) && !hasActiveAppealOverlap(questionId);
}

function hasActiveAppealOverlap(questionId?: number | null) {
  const targetQuestionId = questionId ?? null;
  return currentAppeals.value.some((item) => {
    if (item.status !== 0 && item.status !== 1) return false;
    if (targetQuestionId === null) return true;
    return item.questionId == null || item.questionId === targetQuestionId;
  });
}

function appealWindowHint(grade?: GradeInfo | null) {
  if (!grade || !canViewResult(grade)) return '';
  if (grade.appealDeadlineAt) return `申诉截止：${formatDateTime(grade.appealDeadlineAt)}`;
  if (grade.appealOpen) return '申诉长期开放';
  return '';
}

function appealDisabledReason(grade?: GradeInfo | null) {
  if (!grade || !canViewResult(grade)) return '成绩发布后才能申诉';
  if (grade.appealOpen) return '';
  if (grade.appealDeadlineAt) return `申诉已截止：${formatDateTime(grade.appealDeadlineAt)}`;
  return '当前不允许提交成绩申诉';
}

function appealSubmitDisabledReason(grade?: GradeInfo | null, questionId?: number | null) {
  const baseReason = appealDisabledReason(grade);
  if (baseReason) return baseReason;
  if (hasActiveAppealOverlap(questionId)) return '该答卷或题目已有申诉记录，处理完成前不能重复提交';
  return '';
}

function statusText(status?: number) {
  if (status === 4) return '待批阅';
  if (status === 5) return '已出分';
  if (status === 2) return '已交卷';
  return '未完成';
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

function isCorrectValue(value: boolean | number | undefined) {
  return value === true || value === 1;
}

function appealStatusText(status?: number) {
  if (status === 1) return '已回复';
  if (status === 2) return '已关闭';
  return '待处理';
}

function appealStatusType(status?: number) {
  if (status === 1) return 'success';
  if (status === 2) return 'info';
  return 'warning';
}

function appealHandlingResultText(value?: string | null) {
  const map: Record<string, string> = {
    MAINTAINED: '维持原分',
    RECHECK_REQUIRED: '需要复核',
    ADJUSTED_OFFLINE: '已线下调整'
  };
  return value ? map[value] || value : '-';
}
function appealLogActionText(value?: string | null) {
  const map: Record<string, string> = {
    SUBMIT: 'Submitted',
    REPLY: 'Replied',
    RECHECK_OPEN: 'Recheck opened',
    CLOSE_RECHECK: 'Recheck closed'
  };
  return value ? map[value] || value : '-';
}

function appealLogStatusText(status?: number | null) {
  return status === null || status === undefined ? '-' : appealStatusText(status);
}
</script>

<style scoped>
.result-drawer .answer-block {
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.result-drawer .answer-block p {
  margin: 8px 0;
  line-height: 1.7;
}

.result-option-list {
  display: grid;
  gap: 6px;
  margin: 10px 0;
}

.result-option-item {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
}

.result-option-item.correct {
  border-color: #86efac;
  background: #f0fdf4;
  color: #166534;
}

.text-success {
  color: #16a34a;
}

.text-danger {
  color: #dc2626;
}

.appeal-summary,
.answer-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.appeal-summary {
  justify-content: space-between;
  margin-bottom: 12px;
}

.appeal-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #6b7280;
  font-size: 12px;
}

.appeal-table {
  margin-bottom: 14px;
}

.score-release-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.score-release-cell span {
  min-width: 0;
  color: #6b7280;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.student-score-appeal-audit {
  margin-bottom: 14px;
}

.student-score-appeal-audit-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.student-appeal-audit-id-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.student-appeal-audit-id-cell span {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  color: #111827;
}

.student-appeal-audit-id-cell .el-button {
  padding: 0;
}

.student-appeal-log-head {
  display: grid;
  gap: 4px;
  margin-bottom: 12px;
  color: #475569;
}

.student-appeal-log-head strong {
  color: #1f2937;
}

.student-appeal-log-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.student-appeal-evidence-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.student-appeal-evidence-head > div {
  display: grid;
  gap: 4px;
}

.student-appeal-evidence-head strong {
  color: #111827;
  font-size: 16px;
}

.student-appeal-evidence-head span {
  color: #64748b;
  font-size: 13px;
}

.student-appeal-evidence-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.student-appeal-evidence-stats > div {
  min-height: 64px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.student-appeal-evidence-stats span {
  color: #64748b;
  font-size: 12px;
}

.student-appeal-evidence-stats strong {
  color: #111827;
  font-size: 16px;
}

.student-appeal-evidence-summary,
.student-appeal-evidence-table {
  margin-top: 14px;
}
</style>
