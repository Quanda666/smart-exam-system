<template>
  <section class="exam-approval-page mp-page">
    <div class="approval-header">
      <div>
        <h3>考试审批</h3>
        <p>集中处理教师提交的考试发布申请，批准后才会生成考生入口和发布通知。</p>
      </div>
      <div class="approval-header-actions">
        <el-button :icon="Refresh" @click="loadQueue">刷新</el-button>
        <el-button :icon="Bell" :loading="reminding" @click="sendReminders">发送超时提醒</el-button>
        <el-button :icon="Clock" @click="openReminderLogs">提醒记录</el-button>
      </div>
    </div>

    <el-alert
      v-if="lastApprovalDecisionAudit"
      class="approval-decision-audit"
      type="success"
      :closable="true"
      show-icon
      @close="lastApprovalDecisionAudit = null"
    >
      <template #title>
        <div class="approval-decision-audit-content">
          <span>{{ lastApprovalDecisionAudit.action }} audit recorded: {{ approvalDecisionAuditText(lastApprovalDecisionAudit.approvalLogIds) }}</span>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestApprovalDecisionAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestApprovalDecisionAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

    <div class="approval-metrics">
      <div>
        <span>待处理</span>
        <strong>{{ pendingTotal }}</strong>
      </div>
      <div>
        <span>风险项</span>
        <strong>{{ riskTotal }}</strong>
      </div>
      <div>
        <span>当前页申请</span>
        <strong>{{ approvals.length }}</strong>
      </div>
      <div>
        <span>筛选结果</span>
        <strong>{{ total }}</strong>
      </div>
    </div>

    <el-tabs v-model="approvalTab" class="approval-status-tabs" @tab-change="switchApprovalTab">
      <el-tab-pane label="待审批" name="PENDING" />
      <el-tab-pane label="审批历史" name="PROCESSED" />
    </el-tabs>

    <div class="mp-toolbar approval-filters">
      <el-input v-model="query.keyword" placeholder="考试/试卷/科目" clearable style="width: 210px" @keyup.enter="search" />
      <el-input v-model="query.creatorKeyword" placeholder="教师姓名/账号" clearable style="width: 180px" @keyup.enter="search" />
      <el-select
        v-if="approvalTab === 'PROCESSED'"
        v-model="query.status"
        clearable
        placeholder="状态"
        style="width: 140px"
        @change="search"
      >
        <el-option label="已发布" :value="1" />
        <el-option label="已驳回" :value="3" />
      </el-select>
      <el-select v-model="query.risk" clearable placeholder="风险项" style="width: 190px" @change="search">
        <el-option label="已过开考时间" value="PAST_START" />
        <el-option label="无参考范围" value="NO_TARGET" />
        <el-option label="试卷无题目" value="NO_QUESTIONS" />
        <el-option label="及格线超过总分" value="PASS_SCORE_OVER_TOTAL" />
      </el-select>
      <el-date-picker
        v-model="startRange"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
        start-placeholder="开考开始"
        end-placeholder="开考结束"
        style="width: 330px"
        @change="search"
      />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
    </div>

    <div class="mp-table-card">
      <el-table v-loading="loading" :data="approvals" row-key="id" :row-class-name="approvalRowClassName">
        <el-table-column label="考试" min-width="200">
          <template #default="scope">
            <div class="approval-exam-cell">
              <strong>{{ scope.row.examName }}</strong>
              <span>{{ scope.row.paperName }} / {{ scope.row.subjectName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="教师" min-width="100">
          <template #default="scope">{{ scope.row.creatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="开考时间" min-width="155">
          <template #default="scope">{{ formatDateTime(scope.row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="范围/题目" min-width="90">
          <template #default="scope">
            {{ scope.row.targetCount || 0 }} / {{ scope.row.questionCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="分数" min-width="90">
          <template #default="scope">
            {{ scoreText(scope.row.passScore) }} / {{ scoreText(scope.row.totalScore) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="85">
          <template #default="scope">
            <el-tag :type="statusType(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="风险" min-width="160">
          <template #default="scope">
            <div class="risk-tags">
              <el-tag v-for="risk in riskList(scope.row.riskFlags)" :key="risk" type="danger" effect="plain">
                {{ riskText(risk) }}
              </el-tag>
              <span v-if="riskList(scope.row.riskFlags).length === 0">-</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="最近意见" min-width="150" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.latestApprovalNote || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.status === 0" link type="success" :icon="Check" @click="approve(scope.row as ExamApprovalQueueItem)">批准</el-button>
            <el-button v-if="scope.row.status === 0" link type="warning" :icon="Close" @click="reject(scope.row as ExamApprovalQueueItem)">驳回</el-button>
            <el-button link type="primary" @click="openLogs(scope.row as ExamApprovalQueueItem)">记录</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && approvals.length === 0" description="暂无审批申请" />
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        class="mp-pager"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-drawer v-model="logVisible" title="审批记录" size="min(720px, 94vw)">
      <template v-if="selectedExam">
        <div class="approval-log-head">
          <strong>{{ selectedExam.examName }}</strong>
          <el-tag :type="statusType(selectedExam.status)">{{ statusText(selectedExam.status) }}</el-tag>
        </div>
        <div class="approval-log-toolbar">
          <el-button type="success" plain :loading="logExporting" @click="exportApprovalLogs">Export</el-button>
        </div>
        <el-table v-loading="logLoading" :data="logs" border>
          <el-table-column prop="id" label="Log ID" width="90" />
          <el-table-column label="动作" width="110">
            <template #default="scope">{{ actionText(scope.row.action) }}</template>
          </el-table-column>
          <el-table-column label="状态流转" width="150">
            <template #default="scope">{{ statusText(scope.row.statusFrom) }} -> {{ statusText(scope.row.statusTo) }}</template>
          </el-table-column>
          <el-table-column prop="actorName" label="处理人" width="130" />
          <el-table-column label="时间" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="发布触达" width="220">
            <template #default="scope">{{ publishStatsText(scope.row as ExamApprovalLog) }}</template>
          </el-table-column>
          <el-table-column label="备注/原因" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.note || '-' }}</template>
          </el-table-column>
          <el-table-column label="Audit" width="150" fixed="right">
            <template #default="scope">
              <el-button link type="primary" :icon="Search" @click="openApprovalAudit(scope.row.id)">Audit</el-button>
              <el-button link type="primary" :icon="DocumentCopy" @click="copyApprovalAuditLink(scope.row.id)">Copy</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <el-drawer v-model="reminderLogVisible" title="审批提醒记录" size="min(760px, 94vw)">
      <div class="reminder-log-toolbar">
        <el-button type="success" plain :loading="reminderLogExporting" @click="exportReminderLogs">Export</el-button>
      </div>
      <el-table
        v-loading="reminderLogLoading"
        :data="reminderLogs"
        border
        :row-class-name="reminderLogRowClassName"
      >
        <el-table-column label="Log ID" width="180">
          <template #default="scope">
            <div class="reminder-log-id-cell">
              <span>#{{ scope.row.id }}</span>
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy approval reminder log ID"
                aria-label="Copy approval reminder log ID"
                @click="copyReminderLogId(scope.row.id)"
              />
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy approval reminder notification audit link"
                aria-label="Copy approval reminder notification audit link"
                @click="copyReminderNotificationAuditLink(scope.row.id)"
              />
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy approval reminder audit link"
                aria-label="Copy approval reminder audit link"
                @click="copyReminderAuditLink(scope.row.id)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="130">
          <template #default="scope">
            <el-tag :type="reminderStatusType(scope.row.status)">{{ reminderStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggeredByName" label="触发人" width="130" />
        <el-table-column label="来源" width="100">
          <template #default="scope">{{ reminderSourceText(scope.row.triggerSource) }}</template>
        </el-table-column>
        <el-table-column label="超时/收件" width="130">
          <template #default="scope">{{ scope.row.overdueExamCount }} / {{ scope.row.recipientCount }}</template>
        </el-table-column>
        <el-table-column label="阈值/冷却" width="130">
          <template #default="scope">{{ scope.row.overdueHours }}h / {{ scope.row.cooldownHours }}h</template>
        </el-table-column>
        <el-table-column label="节点/耗时" min-width="170" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.nodeId || '-' }} / {{ durationText(scope.row.durationMs) }}
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="通知审计" width="110">
          <template #default="scope">
            <el-button
              v-if="scope.row.status === 'SENT'"
              link
              type="primary"
              @click="openReminderNotificationAudit(scope.row as ApprovalReminderLog)"
            >
              查看
            </el-button>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.message || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!reminderLogLoading && reminderLogs.length === 0" description="暂无提醒记录" />
      <el-pagination
        v-model:current-page="reminderLogPage"
        v-model:page-size="reminderLogSize"
        :page-sizes="[10, 20, 50]"
        :total="reminderLogTotal"
        layout="total, sizes, prev, pager, next"
        class="mp-pager"
        @current-change="loadReminderLogs"
        @size-change="handleReminderLogSizeChange"
      />
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Bell, Check, Clock, Close, DocumentCopy, Refresh, Search } from '@element-plus/icons-vue';
import {
  approveExam,
  exportApprovalReminderLogs,
  exportExamApprovalLogs,
  getExamApprovalLogs,
  listApprovalReminderLogs,
  listExamApprovalQueue,
  rejectExam,
  sendApprovalOverdueReminders,
  type ExamApprovalLog,
  type ExamInfo,
  type ExamApprovalQueueItem,
  type ApprovalReminderResult,
  type ApprovalReminderLog
} from '../api/exam';
import {
  copyApprovalReminderAuditLinkToClipboard,
  copyApprovalReminderLogIdToClipboard,
  copyApprovalReminderNotificationAuditLinkToClipboard,
  copyExamApprovalAuditIdToClipboard,
  copyExamApprovalAuditLinkToClipboard
} from '../utils/clipboard';
import { formatDateTime } from '../utils/dateFormat';

const route = useRoute();
const router = useRouter();
const approvals = ref<ExamApprovalQueueItem[]>([]);
const total = ref(0);
const page = ref(1);
const size = ref(10);
const loading = ref(false);
const reminding = ref(false);
const startRange = ref<[string, string] | null>(null);
const query = reactive<{
  keyword: string;
  creatorKeyword: string;
  status: number | null;
  risk: string;
}>({
  keyword: '',
  creatorKeyword: '',
  status: null,
  risk: ''
});
const approvalTab = ref<'PENDING' | 'PROCESSED'>('PENDING');

const logVisible = ref(false);
const logLoading = ref(false);
const logExporting = ref(false);
const selectedExam = ref<ExamApprovalQueueItem | null>(null);
const logs = ref<ExamApprovalLog[]>([]);
const reminderLogVisible = ref(false);
const reminderLogLoading = ref(false);
const reminderLogExporting = ref(false);
const reminderLogPage = ref(1);
const reminderLogSize = ref(10);
const reminderLogTotal = ref(0);
const reminderLogs = ref<ApprovalReminderLog[]>([]);
const lastApprovalDecisionAudit = ref<{ action: string; approvalLogIds: Array<number | string> } | null>(null);

const pendingTotal = computed(() => approvals.value.filter((item) => item.status === 0).length);
const riskTotal = computed(() => approvals.value.reduce((sum, item) => sum + riskList(item.riskFlags).length, 0));
const focusedApprovalExamId = computed(() => routeExamId());
const focusedReminderLogId = computed(() => routeReminderLogId());

onMounted(() => {
  loadQueue();
  applyReminderLogRouteFocus();
});

watch(
  () => route.query.examId,
  () => {
    page.value = 1;
    loadQueue();
  }
);

watch(
  () => route.query.reminderLogId,
  () => {
    applyReminderLogRouteFocus();
  }
);

async function loadQueue() {
  loading.value = true;
  try {
    const response = await listExamApprovalQueue({
      keyword: query.keyword.trim(),
      creatorKeyword: query.creatorKeyword.trim(),
      status: typeof query.status === 'number' ? query.status : null,
      statusGroup: approvalTab.value,
      risk: query.risk,
      examId: focusedApprovalExamId.value,
      startFrom: startRange.value?.[0],
      startTo: startRange.value?.[1],
      page: page.value,
      size: size.value
    });
    approvals.value = response.data.list;
    total.value = response.data.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审批队列加载失败');
  } finally {
    loading.value = false;
  }
}

function routeExamId() {
  const raw = Array.isArray(route.query.examId) ? route.query.examId[0] : route.query.examId;
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function routeReminderLogId() {
  const raw = Array.isArray(route.query.reminderLogId) ? route.query.reminderLogId[0] : route.query.reminderLogId;
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function approvalRowClassName({ row }: { row: ExamApprovalQueueItem }) {
  return row.id === focusedApprovalExamId.value ? 'approval-row-focused' : '';
}

function reminderLogRowClassName({ row }: { row: ApprovalReminderLog }) {
  return row.id === focusedReminderLogId.value ? 'reminder-log-row-focused' : '';
}

async function sendReminders() {
  reminding.value = true;
  try {
    const result = (await sendApprovalOverdueReminders()).data;
    if (!result.enabled) {
      ElMessage.warning('审批超时提醒已在系统配置中关闭');
    } else if (result.cooldownActive) {
      ElMessage.warning(`提醒冷却中，${result.cooldownHours} 小时内不会重复发送`);
    } else if (!result.sent) {
      ElMessage.info('暂无需要提醒的超时审批');
    } else {
      ElMessage.success(approvalReminderSuccessText(result));
    }
    if (reminderLogVisible.value) {
      await loadReminderLogs();
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送提醒失败');
  } finally {
    reminding.value = false;
  }
}

async function openReminderLogs() {
  reminderLogVisible.value = true;
  reminderLogPage.value = 1;
  await loadReminderLogs();
}

async function applyReminderLogRouteFocus() {
  if (!focusedReminderLogId.value) return;
  reminderLogVisible.value = true;
  reminderLogPage.value = 1;
  await loadReminderLogs();
}

async function loadReminderLogs() {
  reminderLogLoading.value = true;
  try {
    const response = await listApprovalReminderLogs(reminderLogPage.value, reminderLogSize.value, {
      logId: focusedReminderLogId.value
    });
    reminderLogs.value = response.data.list;
    reminderLogTotal.value = response.data.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提醒记录加载失败');
  } finally {
    reminderLogLoading.value = false;
  }
}

async function exportReminderLogs() {
  reminderLogExporting.value = true;
  try {
    await exportApprovalReminderLogs();
    ElMessage.success('Approval reminder log export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Export failed');
  } finally {
    reminderLogExporting.value = false;
  }
}

function handleReminderLogSizeChange(nextSize: number) {
  reminderLogSize.value = nextSize;
  reminderLogPage.value = 1;
  loadReminderLogs();
}

async function copyReminderLogId(logId?: number | null) {
  try {
    const value = await copyApprovalReminderLogIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`Approval reminder log ID copied: ${value}`);
  } catch {
    ElMessage.error('Failed to copy approval reminder log ID');
  }
}

async function copyReminderNotificationAuditLink(logId?: number | null) {
  try {
    const link = await copyApprovalReminderNotificationAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('Approval reminder notification audit link copied');
  } catch {
    ElMessage.error('Failed to copy approval reminder notification audit link');
  }
}

async function copyReminderAuditLink(logId?: number | null) {
  try {
    const link = await copyApprovalReminderAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('Approval reminder audit link copied');
  } catch {
    ElMessage.error('Failed to copy approval reminder audit link');
  }
}

function search() {
  page.value = 1;
  loadQueue();
}

function switchApprovalTab() {
  query.status = null;
  page.value = 1;
  loadQueue();
}

function handlePageChange(nextPage: number) {
  page.value = nextPage;
  loadQueue();
}

function handleSizeChange(nextSize: number) {
  size.value = nextSize;
  page.value = 1;
  loadQueue();
}

async function approve(row: ExamApprovalQueueItem) {
  let note = '';
  try {
    const result = await ElMessageBox.prompt(`确认批准「${row.examName}」并发布给学生吗？`, '批准发布', {
      type: 'warning',
      inputType: 'textarea',
      inputPlaceholder: '批准备注（可选）',
      confirmButtonText: '确认批准',
      cancelButtonText: '取消'
    });
    note = String(result.value || '').trim();
  } catch {
    return;
  }
  try {
    const response = await approveExam(row.id, note ? { note } : undefined);
    rememberApprovalDecisionAudit('Approve exam', [response.data.approvalLogId]);
    ElMessage.success(publishNotificationSummary(response.data));
    await loadQueue();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批准失败');
  }
}

async function reject(row: ExamApprovalQueueItem) {
  let note = '';
  try {
    const result = await ElMessageBox.prompt(`请输入「${row.examName}」的驳回原因。`, '驳回发布', {
      type: 'warning',
      inputType: 'textarea',
      inputPlaceholder: '请说明需要教师修改的内容',
      inputValidator: (value) => Boolean(String(value || '').trim()) || '请填写驳回原因',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消'
    });
    note = String(result.value || '').trim();
  } catch {
    return;
  }
  try {
    const response = await rejectExam(row.id, { note });
    rememberApprovalDecisionAudit('Reject exam', [response.data.approvalLogId]);
    ElMessage.success('考试发布申请已驳回');
    await loadQueue();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '驳回失败');
  }
}

async function openLogs(row: ExamApprovalQueueItem) {
  selectedExam.value = row;
  logVisible.value = true;
  logLoading.value = true;
  logs.value = [];
  try {
    logs.value = (await getExamApprovalLogs(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审批记录加载失败');
  } finally {
    logLoading.value = false;
  }
}

async function exportApprovalLogs() {
  if (!selectedExam.value) return;
  logExporting.value = true;
  try {
    await exportExamApprovalLogs(selectedExam.value.id, selectedExam.value.examName);
    ElMessage.success('Approval log export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Export failed');
  } finally {
    logExporting.value = false;
  }
}

function openApprovalAudit(logId?: number | null) {
  if (!logId) return;
  router.push(`/monitor/logs?examApprovalLogId=${encodeURIComponent(String(logId))}`);
}

async function copyApprovalAuditLink(logId?: number | null) {
  try {
    const link = await copyExamApprovalAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('Exam approval audit link copied');
  } catch {
    ElMessage.error('Failed to copy exam approval audit link');
  }
}

function rememberApprovalDecisionAudit(action: string, ids: Array<number | string | null | undefined>) {
  const approvalLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (approvalLogIds.length === 0) return;
  lastApprovalDecisionAudit.value = { action, approvalLogIds };
}

function approvalDecisionAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestApprovalDecisionAuditId() {
  const ids = lastApprovalDecisionAudit.value?.approvalLogIds;
  if (!ids?.length) return;
  try {
    await copyExamApprovalAuditIdToClipboard(ids.join(','));
    ElMessage.success('Exam approval audit ID copied');
  } catch {
    ElMessage.error('Failed to copy exam approval audit ID');
  }
}

async function copyLatestApprovalDecisionAuditLink() {
  const id = lastApprovalDecisionAudit.value?.approvalLogIds[0];
  if (!id) return;
  try {
    await copyExamApprovalAuditLinkToClipboard(id);
    ElMessage.success('Exam approval audit link copied');
  } catch {
    ElMessage.error('Failed to copy exam approval audit link');
  }
}

function riskList(value?: string | null) {
  return value ? value.split(',').map((item) => item.trim()).filter(Boolean) : [];
}

function riskText(value: string) {
  const map: Record<string, string> = {
    PAST_START: '已过开考',
    NO_TARGET: '无范围',
    NO_QUESTIONS: '无题目',
    PASS_SCORE_OVER_TOTAL: '及格线异常'
  };
  return map[value] || value;
}

function statusText(status?: number | null) {
  if (status === null || status === undefined) return '-';
  const map: Record<number, string> = {
    0: '待审批',
    1: '已发布',
    2: '已关闭',
    3: '已驳回'
  };
  return map[status] || String(status);
}

function statusType(status?: number | null) {
  if (status === 1) return 'success';
  if (status === 3) return 'danger';
  if (status === 2) return 'info';
  return 'warning';
}

function actionText(action: string) {
  const map: Record<string, string> = {
    SUBMIT: '提交审批',
    RESUBMIT: '重新提交',
    APPROVE: '批准发布',
    REJECT: '驳回',
    DIRECT_PUBLISH: '管理员发布'
  };
  return map[action] || action;
}

function publishStatsText(row: ExamApprovalLog) {
  if (row.action !== 'APPROVE' && row.action !== 'DIRECT_PUBLISH') return '-';
  return `${row.candidateCount ?? 0} 人候选 / ${row.notifiedStudentCount ?? 0} 人通知 / ${row.notifiedAttemptCount ?? 0} 份答卷`;
}

function approvalReminderSuccessText(result: ApprovalReminderResult) {
  const suffix = result.reminderLogId ? `，提醒记录 #${result.reminderLogId}` : '';
  return `已向 ${result.adminCount} 名管理员发送 ${result.overdueExamCount} 场超时审批提醒${suffix}`;
}

function openReminderNotificationAudit(row: ApprovalReminderLog) {
  router.push({
    path: '/monitor/logs',
    query: {
      tab: 'notification',
      relatedType: 'APPROVAL_REMINDER',
      relatedId: String(row.id)
    }
  });
}

function publishNotificationSummary(row: ExamInfo) {
  return `考试已发布：${row.publishCandidateCount ?? 0} 名候选考生，已通知 ${row.publishNotifiedStudentCount ?? 0} 名学生，生成 ${row.publishNotifiedAttemptCount ?? 0} 份答卷`;
}

function reminderStatusText(status: string) {
  const map: Record<string, string> = {
    SENT: '已发送',
    SKIPPED_DISABLED: '已关闭',
    SKIPPED_EMPTY: '无超时',
    SKIPPED_NO_RECIPIENT: '无收件人',
    SKIPPED_COOLDOWN: '冷却中',
    SKIPPED_SCHEDULE_DISABLED: '任务关闭',
    SKIPPED_SCHEDULE_INTERVAL: '等待间隔'
  };
  return map[status] || status;
}

function reminderStatusType(status: string) {
  if (status === 'SENT') return 'success';
  if (status === 'SKIPPED_COOLDOWN') return 'warning';
  if (status === 'SKIPPED_DISABLED' || status === 'SKIPPED_NO_RECIPIENT' || status === 'SKIPPED_SCHEDULE_DISABLED') return 'danger';
  return 'info';
}

function reminderSourceText(source?: string) {
  const map: Record<string, string> = {
    MANUAL: '手动',
    SCHEDULE: '自动'
  };
  return source ? map[source] || source : '-';
}

function durationText(value?: number | null) {
  if (value === null || value === undefined) return '-';
  if (value < 1000) return `${value}ms`;
  return `${(value / 1000).toFixed(1)}s`;
}

function scoreText(value?: number | null) {
  return value === null || value === undefined ? '-' : `${value}`;
}
</script>

<style scoped>
.exam-approval-page {
  display: grid;
  gap: 14px;
  overflow-x: auto;
}

.exam-approval-page :deep(.el-table) {
  max-width: 100%;
}

.approval-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.approval-header-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.approval-header h3 {
  margin: 0;
  color: #111827;
  font-size: 20px;
}

.approval-header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.approval-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.approval-status-tabs {
  margin-bottom: 4px;
}

.approval-status-tabs :deep(.el-tabs__header) {
  margin: 0 0 12px;
}

.approval-metrics > div {
  display: grid;
  gap: 6px;
  min-height: 76px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.approval-metrics span {
  color: #64748b;
  font-size: 13px;
}

.approval-metrics strong {
  color: #111827;
  font-size: 24px;
}

.approval-filters {
  flex-wrap: wrap;
}

.approval-decision-audit {
  margin-top: 2px;
}

.approval-decision-audit-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.approval-exam-cell {
  display: grid;
  gap: 4px;
}

.approval-exam-cell strong {
  color: #111827;
}

.approval-exam-cell span {
  color: #64748b;
  font-size: 12px;
}

.mp-table-card :deep(.approval-row-focused td) {
  background: #fffbeb !important;
}

.reminder-log-row-focused td,
:deep(.reminder-log-row-focused td) {
  background: #fffbeb !important;
}

.risk-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.approval-log-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.approval-log-head strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-log-toolbar,
.reminder-log-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.reminder-log-id-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.reminder-log-id-cell span {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  color: #111827;
}

.reminder-log-id-cell .el-button {
  padding: 0;
}

@media (max-width: 900px) {
  .approval-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .approval-header {
    display: grid;
  }

  .approval-header-actions {
    justify-content: flex-start;
  }
}
</style>
