<template>
  <section class="exam-monitor">
    <div class="monitor-layout">
      <aside class="exam-list-panel">
        <div class="panel-head">
          <div>
            <h3>实时监考</h3>
            <p>按考试查看在线状态、风险事件和处置记录</p>
          </div>
          <el-button :icon="Refresh" circle :loading="examLoading" @click="loadExams" />
        </div>

        <el-input
          v-model="keyword"
          placeholder="搜索考试"
          clearable
          :prefix-icon="Search"
          class="exam-search"
          @keyup.enter="loadExams"
          @clear="loadExams"
        />

        <div v-loading="examLoading" class="exam-list">
          <button
            v-for="exam in filteredExams"
            :key="exam.id"
            type="button"
            :class="['exam-option', { active: selectedExamId === exam.id }]"
            @click="selectExam(exam.id)"
          >
            <strong>{{ exam.examName }}</strong>
            <span>{{ exam.paperName || '未关联试卷' }}</span>
            <small>{{ phaseText(exam) }} · {{ exam.submittedCount || 0 }}/{{ exam.attemptCount || 0 }} 已交卷</small>
          </button>
          <el-empty v-if="!examLoading && filteredExams.length === 0" description="暂无可监考考试" />
        </div>
      </aside>

      <div class="monitor-main">
        <div class="monitor-toolbar">
          <div>
            <h2>{{ selectedExam?.examName || '请选择考试' }}</h2>
            <p v-if="selectedExam">
              {{ formatDateTime(selectedExam.startTime) }} 至 {{ formatDateTime(selectedExam.endTime) }}
            </p>
          </div>
          <div class="toolbar-actions">
            <el-switch v-model="autoRefresh" active-text="自动刷新" />
            <el-button
              type="success"
              plain
              :icon="Download"
              :loading="exporting"
              :disabled="!selectedExamId"
              @click="exportSessions"
            >
              导出
            </el-button>
            <el-button :icon="Refresh" :loading="sessionLoading" @click="loadSessions">刷新</el-button>
          </div>
        </div>

        <div v-if="thresholdText" class="threshold-line">
          {{ thresholdText }}
        </div>

        <el-alert
          v-if="lastMonitorOperationAudit"
          class="monitor-operation-audit"
          type="success"
          :closable="true"
          show-icon
          @close="lastMonitorOperationAudit = null"
        >
          <template #title>
            <div class="monitor-operation-audit-content">
              <span>{{ lastMonitorOperationAudit.action }} audit recorded: {{ monitorOperationAuditText(lastMonitorOperationAudit.operationLogIds) }}</span>
              <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestMonitorOperationAuditId">Copy audit ID</el-button>
              <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestMonitorOperationAuditLink">Copy audit link</el-button>
            </div>
          </template>
        </el-alert>

        <div class="session-filter-row">
          <el-select v-model="sessionFilter.status" class="session-status-filter" placeholder="Status">
            <el-option label="All status" value="ALL" />
            <el-option label="Online" value="ONLINE" />
            <el-option label="Offline" value="OFFLINE" />
            <el-option label="Submitted" value="SUBMITTED" />
          </el-select>
          <el-select v-model="sessionFilter.minRiskScore" class="session-risk-filter" placeholder="Risk">
            <el-option label="All risk" :value="-1" />
            <el-option label="Risk > 0" :value="1" />
            <el-option :label="`Risk >= ${thresholds.warning}`" :value="thresholds.warning" />
            <el-option :label="`Risk >= ${thresholds.high}`" :value="thresholds.high" />
          </el-select>
          <el-select v-model="sessionFilter.latestNotificationStatus" class="session-notification-filter" placeholder="Latest notification">
            <el-option label="All notifications" value="ALL" />
            <el-option label="Sent" value="SENT" />
            <el-option label="Unread" value="UNREAD" />
            <el-option label="Read" value="READ" />
            <el-option label="No notification" value="NONE" />
          </el-select>
          <el-select v-model="sessionFilter.rulesConfirmationStatus" class="session-rules-filter" placeholder="Rules">
            <el-option label="All rules" value="ALL" />
            <el-option label="Confirmed" value="CONFIRMED" />
            <el-option label="Missing" value="MISSING" />
          </el-select>
          <el-select v-model="sessionFilter.latestActionType" class="session-action-filter" placeholder="Latest action">
            <el-option label="All actions" value="ALL" />
            <el-option label="Rules reminder" value="RULES_REMINDER" />
            <el-option label="Student reminder" value="WARN" />
            <el-option label="Acknowledged" value="ACKNOWLEDGE" />
            <el-option label="Force submit" value="FORCE_SUBMIT" />
            <el-option label="Note" value="NOTE" />
          </el-select>
          <el-button plain :disabled="sessionLoading" @click="resetSessionFilters">Reset</el-button>
          <span class="filter-count">{{ filteredSessions.length }}/{{ sessions.length }}</span>
        </div>

        <div class="metric-grid">
          <div class="metric-card online">
            <span>在线</span>
            <strong>{{ metrics.online }}</strong>
          </div>
          <div class="metric-card offline">
            <span>离线</span>
            <strong>{{ metrics.offline }}</strong>
          </div>
          <div class="metric-card submitted">
            <span>已交卷</span>
            <strong>{{ metrics.submitted }}</strong>
          </div>
          <div class="metric-card draft">
            <span>Saved drafts</span>
            <strong>{{ metrics.savedDrafts }}</strong>
          </div>
          <div class="metric-card timeout">
            <span>Time critical</span>
            <strong>{{ metrics.timeCritical }}</strong>
          </div>
          <div class="metric-card risk">
            <span>风险事件</span>
            <strong>{{ metrics.events }}</strong>
          </div>
          <div class="metric-card danger">
            <span>高风险</span>
            <strong>{{ metrics.highRisk }}</strong>
          </div>
          <button
            type="button"
            :class="['metric-card notice metric-action', { active: isUnreadNoticeFilterActive }]"
            :disabled="metrics.unreadNotices === 0"
            :aria-pressed="isUnreadNoticeFilterActive"
            @click="applyUnreadNoticeFilter"
          >
            <span>Unread notices</span>
            <strong>{{ metrics.unreadNotices }}</strong>
          </button>
          <button
            type="button"
            :class="['metric-card rules metric-action', { active: isMissingRulesFilterActive }]"
            :disabled="metrics.missingRules === 0"
            :aria-pressed="isMissingRulesFilterActive"
            @click="applyMissingRulesFilter"
          >
            <span>Missing rules</span>
            <strong>{{ metrics.missingRules }}</strong>
          </button>
          <button
            type="button"
            :class="['metric-card rules-reminder metric-action', { active: isRulesReminderFilterActive }]"
            :disabled="metrics.rulesReminders === 0"
            :aria-pressed="isRulesReminderFilterActive"
            @click="applyRulesReminderFilter"
          >
            <span>Rules reminders</span>
            <strong>{{ metrics.rulesReminders }}</strong>
          </button>
          <button
            type="button"
            :class="['metric-card pending-reminder metric-action', { active: isPendingRulesReminderFilterActive }]"
            :disabled="metrics.pendingRulesReminders === 0"
            :aria-pressed="isPendingRulesReminderFilterActive"
            @click="applyPendingRulesReminderFilter"
          >
            <span>Pending reminders</span>
            <strong>{{ metrics.pendingRulesReminders }}</strong>
          </button>
          <button
            type="button"
            :class="['metric-card confirmed-reminder metric-action', { active: isConfirmedRulesReminderFilterActive }]"
            :disabled="metrics.confirmedRulesReminders === 0"
            :aria-pressed="isConfirmedRulesReminderFilterActive"
            @click="applyConfirmedRulesReminderFilter"
          >
            <span>Confirmed reminders</span>
            <strong>{{ metrics.confirmedRulesReminders }}</strong>
          </button>
        </div>

        <el-table v-loading="sessionLoading" :data="filteredSessions" border class="session-table" height="520">
          <el-table-column label="学生" min-width="180">
            <template #default="scope">
              <div class="student-cell">
                <strong>{{ scope.row.realName || '-' }}</strong>
                <span>{{ scope.row.studentNo || '-' }} · {{ scope.row.className || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="scope">
              <el-tag :type="sessionStatusType(scope.row.status)">
                {{ sessionStatusText(scope.row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="风险" width="120">
            <template #default="scope">
              <el-tag :type="riskType(scope.row.riskScore)" effect="plain">
                {{ scope.row.riskScore }} · {{ riskLevelText(scope.row.riskLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="eventCount" label="事件" width="80" />
          <el-table-column label="最后事件" min-width="140">
            <template #default="scope">{{ eventTypeText(scope.row.lastEventType) }}</template>
          </el-table-column>
          <el-table-column label="最后心跳" min-width="165">
            <template #default="scope">{{ formatDateTime(scope.row.lastHeartbeatAt) }}</template>
          </el-table-column>
          <el-table-column label="Runtime" min-width="230">
            <template #default="scope">
              <div class="runtime-cell">
                <strong :class="{ danger: isTimeCritical(scope.row as MonitorSession) }">
                  {{ remainingTimeText(scope.row as MonitorSession) }}
                </strong>
                <span>Deadline: {{ formatDateTime(scope.row.deadlineAt) }}</span>
                <span>{{ draftTelemetryText(scope.row as MonitorSession) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Rules" min-width="165">
            <template #default="scope">{{ formatDateTime(scope.row.rulesConfirmedAt) }}</template>
          </el-table-column>
          <el-table-column label="最近处置" min-width="190">
            <template #default="scope">
              <div class="action-cell">
                <strong>{{ actionTypeText(scope.row.latestActionType) }}</strong>
                <span v-if="scope.row.latestHandledAt">
                  {{ formatDateTime(scope.row.latestHandledAt) }} · {{ scope.row.latestHandlerName || '-' }}
                </span>
                <el-tag v-if="scope.row.latestActionType" size="small" effect="plain" :type="latestNotificationTagType(scope.row as MonitorSession)">
                  {{ latestNotificationText(scope.row as MonitorSession) }}
                </el-tag>
                <el-tag
                  v-if="rulesReminderResolutionText(scope.row as MonitorSession)"
                  size="small"
                  effect="plain"
                  :type="rulesReminderResolutionTagType(scope.row as MonitorSession)"
                >
                  {{ rulesReminderResolutionText(scope.row as MonitorSession) }}
                </el-tag>
                <el-button
                  v-if="scope.row.latestActionNotificationId"
                  link
                  type="primary"
                  :icon="DocumentCopy"
                  title="Copy notification audit ID"
                  aria-label="Copy notification audit ID"
                  @click.stop="copyNotificationAuditId(scope.row.latestActionNotificationId)"
                >
                  Copy audit ID
                </el-button>
                <el-button
                  v-if="scope.row.latestActionNotificationId"
                  link
                  type="primary"
                  :icon="DocumentCopy"
                  title="Copy notification audit link"
                  aria-label="Copy notification audit link"
                  @click.stop="copyNotificationAuditLink(scope.row.latestActionNotificationId)"
                >
                  Copy link
                </el-button>
                <span v-if="scope.row.latestActionNote">{{ scope.row.latestActionNote }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="交卷" min-width="150">
            <template #default="scope">{{ submitText(scope.row as MonitorSession) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="340" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openIncident(scope.row as MonitorSession)">详情</el-button>
              <el-button link type="primary" @click="openEvents(scope.row as MonitorSession)">事件</el-button>
              <el-button link type="success" @click="openAction(scope.row as MonitorSession, 'ACKNOWLEDGE')">
                关注
              </el-button>
              <el-button
                link
                type="warning"
                :disabled="!canSendRulesReminder(scope.row as MonitorSession)"
                :title="rulesReminderUnavailableReason(scope.row as MonitorSession) || 'Send rules reminder'"
                @click="openAction(scope.row as MonitorSession, 'RULES_REMINDER')"
              >
                Rules
              </el-button>
              <el-button
                link
                type="warning"
                :disabled="scope.row.attemptStatus !== 1"
                @click="forceSubmit(scope.row as MonitorSession)"
              >
                强制交卷
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty
          v-if="selectedExamId && !sessionLoading && sessions.length > 0 && filteredSessions.length === 0"
          description="No monitor sessions match the filters"
        />

        <el-empty v-if="selectedExamId && !sessionLoading && sessions.length === 0" description="暂无学生进入考试" />
      </div>
    </div>

    <el-drawer v-model="eventDrawerVisible" title="作答监考详情" size="min(860px, 96vw)">
      <div v-loading="incidentLoading" class="incident-drawer-body">
      <div v-if="activeSession" class="drawer-head">
        <div>
          <strong>{{ activeSession.realName || '-' }}</strong>
          <span>{{ activeSession.studentNo || '-' }} · {{ activeSession.className || '-' }}</span>
        </div>
        <div class="drawer-actions">
          <el-button
            type="success"
            plain
            :icon="Download"
            :loading="eventExporting"
            @click="exportActiveEvents"
          >
            导出事件
          </el-button>
          <el-button
            type="success"
            plain
            :icon="Download"
            :loading="actionExporting"
            @click="exportActiveActions"
          >
            导出处置
          </el-button>
          <el-button type="primary" plain @click="openAction(activeSession, 'NOTE')">记录处置</el-button>
        </div>
      </div>

      <el-alert
        v-if="incidentDetail"
        class="incident-summary"
        :type="incidentHealthAlertType(incidentDetail.health.level)"
        :title="incidentDetail.health.summary"
        show-icon
        :closable="false"
      />

      <el-tabs v-model="drawerTab">
        <el-tab-pane label="Health" name="health">
          <div v-if="incidentDetail" class="incident-overview">
            <div class="incident-health-line">
              <el-tag :type="incidentHealthTagType(incidentDetail.health.level)" effect="dark">
                {{ incidentDetail.health.level }}
              </el-tag>
              <strong>{{ incidentDetail.session.examName || selectedExam?.examName || '-' }}</strong>
            </div>

            <div class="incident-stat-grid">
              <div class="incident-card">
                <span>Runtime</span>
                <strong>{{ incidentRemainingText(incidentDetail) }}</strong>
                <small>Status: {{ attemptStatusText(incidentDetail.attempt.attemptStatus) }}</small>
                <small>Deadline: {{ formatDateTime(incidentDetail.attempt.deadlineAt) }}</small>
                <small>Heartbeat: {{ formatDateTime(incidentDetail.attempt.lastHeartbeatAt || incidentDetail.session.lastHeartbeatAt) }}</small>
              </div>
              <div class="incident-card">
                <span>Draft</span>
                <strong>{{ draftEvidenceText(incidentDetail) }}</strong>
                <small>{{ draftFilledText(incidentDetail) }}</small>
                <small>Saved: {{ formatDateTime(incidentDetail.draft.updatedAt || incidentDetail.attempt.lastDraftSavedAt) }}</small>
                <small>Client: {{ incidentDetail.draft.clientDraftId || '-' }}</small>
              </div>
              <div class="incident-card">
                <span>Submission</span>
                <strong>{{ submissionEvidenceText(incidentDetail) }}</strong>
                <small>Time: {{ formatDateTime(incidentDetail.attempt.submitTime) }}</small>
                <small>Reason: {{ incidentDetail.attempt.submitReason || '-' }}</small>
                <small>Hash: {{ payloadHashText(incidentDetail.attempt.submitPayloadHash) }}</small>
              </div>
              <div class="incident-card">
                <span>Answers</span>
                <strong>{{ incidentAnswerStatsText(incidentDetail) }}</strong>
                <small>Recorded: {{ incidentDetail.answerStats.recordedCount }}</small>
                <small>Reviewed: {{ incidentDetail.answerStats.reviewedCount }}</small>
                <small>Pending review: {{ incidentDetail.answerStats.pendingReviewCount }}</small>
              </div>
              <div class="incident-card">
                <span>Risk</span>
                <strong>{{ incidentDetail.session.riskScore || 0 }} · {{ riskLevelText(incidentDetail.session.riskLevel) }}</strong>
                <small>Events: {{ incidentDetail.session.eventCount || 0 }}</small>
                <small>Last event: {{ eventTypeText(incidentDetail.session.lastEventType) }}</small>
                <small>Last event time: {{ formatDateTime(incidentDetail.session.lastEventAt) }}</small>
              </div>
              <div class="incident-card">
                <span>Force Submit</span>
                <strong>{{ forceSubmitEvidenceText(incidentDetail) }}</strong>
                <small>Time: {{ formatDateTime(incidentDetail.forceSubmitEvidence.submitTime) }}</small>
                <small>Handler: {{ incidentDetail.forceSubmitEvidence.action?.handlerName || '-' }}</small>
                <small>Notice: {{ incidentDetail.forceSubmitEvidence.action?.notificationId || '-' }}</small>
              </div>
            </div>

            <div class="incident-findings">
              <el-tag
                v-for="finding in incidentDetail.health.findings"
                :key="finding.code"
                :type="incidentFindingTagType(finding)"
                effect="plain"
              >
                {{ finding.code }} · {{ finding.message }}
              </el-tag>
              <el-empty
                v-if="incidentDetail.health.findings.length === 0"
                description="No incident findings"
              />
            </div>
          </div>
          <el-empty v-else-if="!incidentLoading" description="暂无作答详情" />
        </el-tab-pane>

        <el-tab-pane label="事件" name="events">
          <div class="event-filter-row">
            <el-select v-model="eventFilter.eventType" class="event-type-filter" placeholder="Event type">
              <el-option label="All events" value="ALL" />
              <el-option
                v-for="option in monitorEventTypeOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <el-date-picker
              v-model="eventFilter.timeRange"
              class="event-time-filter"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              start-placeholder="Start"
              end-placeholder="End"
            />
            <el-select v-model="eventFilter.minRiskScore" class="event-risk-filter" placeholder="Risk">
              <el-option label="All risk" :value="-1" />
              <el-option label="Risk > 0" :value="1" />
              <el-option :label="`Risk >= ${thresholds.warning}`" :value="thresholds.warning" />
              <el-option :label="`Risk >= ${thresholds.high}`" :value="thresholds.high" />
            </el-select>
            <el-button type="primary" plain :icon="Search" :loading="eventLoading" @click="reloadActiveEvents">
              Filter
            </el-button>
            <el-button plain :disabled="eventLoading" @click="resetEventFilters">Reset</el-button>
          </div>
          <el-timeline v-loading="eventLoading">
            <el-timeline-item
              v-for="event in events"
              :key="event.id"
              :timestamp="formatDateTime(event.clientEventTime || event.eventTime)"
              placement="top"
            >
              <div class="event-card">
                <div class="event-title">
                  <strong>{{ eventTypeText(event.eventType) }}</strong>
                  <el-tag :type="riskType(Number(event.riskScore || 0))" effect="plain" size="small">
                    Risk {{ event.riskScore || 0 }}
                  </el-tag>
                </div>
                <pre>{{ prettyExtra(event.extraInfo) }}</pre>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!eventLoading && events.length === 0" description="暂无风险事件" />
        </el-tab-pane>

        <el-tab-pane label="处置" name="actions">
          <el-timeline v-loading="actionLoading">
            <el-timeline-item
              v-for="action in actions"
              :key="action.id"
              :timestamp="formatDateTime(action.handledAt)"
              placement="top"
            >
              <div class="event-card">
                <strong>{{ actionTypeText(action.actionType) }} · {{ action.handlerName || '-' }}</strong>
                <div class="action-notification-row">
                  <el-tag size="small" effect="plain" :type="actionNotificationTagType(action)">
                    {{ actionNotificationText(action) }}
                  </el-tag>
                  <el-button
                    v-if="action.notificationId"
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="Copy notification audit ID"
                    aria-label="Copy notification audit ID"
                    @click="copyNotificationAuditId(action.notificationId)"
                  >
                    Copy audit ID
                  </el-button>
                  <el-button
                    v-if="action.notificationId"
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="Copy notification audit link"
                    aria-label="Copy notification audit link"
                    @click="copyNotificationAuditLink(action.notificationId)"
                  >
                    Copy link
                  </el-button>
                </div>
                <p>{{ action.note || '-' }}</p>
              </div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!actionLoading && actions.length === 0" description="暂无处置记录" />
        </el-tab-pane>
      </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="actionDialogVisible" title="记录监考处置" width="520px">
      <el-form label-position="top">
        <el-form-item label="处置类型">
          <el-select v-model="actionForm.actionType" style="width: 100%">
            <el-option label="已关注" value="ACKNOWLEDGE" />
            <el-option label="提醒学生" value="WARN" :disabled="actionTarget?.attemptStatus !== 1" />
            <el-option
              label="Rules reminder"
              value="RULES_REMINDER"
              :disabled="!actionTarget || !canSendRulesReminder(actionTarget)"
            />
            <el-option label="备注" value="NOTE" />
          </el-select>
        </el-form-item>
        <el-alert
          v-if="actionTarget && !canSendRulesReminder(actionTarget)"
          class="action-state-alert"
          type="info"
          :title="rulesReminderUnavailableReason(actionTarget)"
          show-icon
          :closable="false"
        />
        <el-form-item label="处置说明">
          <el-input
            v-model="actionForm.note"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="记录提醒方式、观察结论或后续处理意见"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="actionDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="actionSaving" @click="saveAction">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { DocumentCopy, Download, Refresh, Search } from '@element-plus/icons-vue';
import { listTeacherExams, type ExamInfo } from '../api/exam';
import {
  createMonitorAction,
  exportAttemptMonitorEvents,
  exportExamMonitorSessions,
  exportMonitorActions,
  forceSubmitMonitorSession,
  getMonitorAttemptIncident,
  listAttemptMonitorEvents,
  listExamMonitorSessions,
  listMonitorActions,
  type MonitorAction,
  type MonitorEvent,
  type MonitorEventQuery,
  type MonitorIncidentDetail,
  type MonitorIncidentFinding,
  type MonitorSession
} from '../api/monitor';
import {
  copyNotificationAuditIdToClipboard,
  copyNotificationAuditLinkToClipboard,
  copyOperationLogIdToClipboard,
  copyOperationLogLinkToClipboard
} from '../utils/clipboard';
import { formatDateTime } from '../utils/dateFormat';

const exams = ref<ExamInfo[]>([]);
const sessions = ref<MonitorSession[]>([]);
const events = ref<MonitorEvent[]>([]);
const actions = ref<MonitorAction[]>([]);
const incidentDetail = ref<MonitorIncidentDetail | null>(null);
const selectedExamId = ref<number | null>(null);
const activeSession = ref<MonitorSession | null>(null);
const actionTarget = ref<MonitorSession | null>(null);
const drawerTab = ref<'health' | 'events' | 'actions'>('health');
const keyword = ref('');
const examLoading = ref(false);
const sessionLoading = ref(false);
const eventLoading = ref(false);
const actionLoading = ref(false);
const incidentLoading = ref(false);
const actionSaving = ref(false);
const exporting = ref(false);
const eventExporting = ref(false);
const actionExporting = ref(false);
const eventDrawerVisible = ref(false);
const actionDialogVisible = ref(false);
const autoRefresh = ref(true);
const lastMonitorOperationAudit = ref<{ action: string; operationLogIds: Array<number | string> } | null>(null);
let refreshTimer: ReturnType<typeof setInterval> | null = null;

const actionForm = reactive({
  actionType: 'ACKNOWLEDGE',
  note: ''
});

const sessionFilter = reactive({
  status: 'ALL',
  minRiskScore: -1,
  latestNotificationStatus: 'ALL',
  rulesConfirmationStatus: 'ALL',
  latestActionType: 'ALL'
});

const eventFilter = reactive({
  eventType: 'ALL',
  timeRange: [] as string[],
  minRiskScore: -1
});

const monitorEventTypeOptions = [
  { label: 'Visibility hidden', value: 'VISIBILITY_HIDDEN' },
  { label: 'Window blur', value: 'WINDOW_BLUR' },
  { label: 'Copy', value: 'COPY' },
  { label: 'Paste', value: 'PASTE' },
  { label: 'Fullscreen exit', value: 'FULLSCREEN_EXIT' },
  { label: 'Page unload attempt', value: 'PAGE_UNLOAD_ATTEMPT' },
  { label: 'History back attempt', value: 'HISTORY_BACK_ATTEMPT' },
  { label: 'Context menu', value: 'CONTEXT_MENU' },
  { label: 'Network offline', value: 'NETWORK_OFFLINE' },
  { label: 'Network online', value: 'NETWORK_ONLINE' },
  { label: 'Heartbeat failed', value: 'HEARTBEAT_FAILED' }
];

const filteredExams = computed(() => {
  const value = keyword.value.trim().toLowerCase();
  if (!value) return exams.value;
  return exams.value.filter((exam) =>
    [exam.examName, exam.paperName || '', exam.subjectName || ''].some((item) => item.toLowerCase().includes(value))
  );
});

const selectedExam = computed(() => exams.value.find((exam) => exam.id === selectedExamId.value) || null);

const filteredSessions = computed(() => {
  return sessions.value.filter((session) => {
    if (sessionFilter.status !== 'ALL' && session.status !== sessionFilter.status) {
      return false;
    }
    if (sessionFilter.minRiskScore >= 0 && Number(session.riskScore || 0) < sessionFilter.minRiskScore) {
      return false;
    }
    if (!latestNotificationStatusMatches(session, sessionFilter.latestNotificationStatus)) {
      return false;
    }
    if (!rulesConfirmationStatusMatches(session, sessionFilter.rulesConfirmationStatus)) {
      return false;
    }
    if (!latestActionTypeMatches(session, sessionFilter.latestActionType)) {
      return false;
    }
    return true;
  });
});

const thresholds = computed(() => {
  const first = sessions.value[0];
  return {
    warning: Number(first?.warningThreshold || 8),
    high: Number(first?.highThreshold || 20)
  };
});

const thresholdText = computed(() => {
  if (!selectedExamId.value) return '';
  return `风险提示阈值 ${thresholds.value.warning}，高风险阈值 ${thresholds.value.high}`;
});

const metrics = computed(() => {
  return filteredSessions.value.reduce(
    (acc, item) => {
      if (item.status === 'ONLINE') acc.online += 1;
      else if (item.status === 'SUBMITTED') acc.submitted += 1;
      else acc.offline += 1;
      acc.events += Number(item.eventCount || 0);
      if (Number(item.riskScore || 0) >= thresholds.value.high) acc.highRisk += 1;
      if (hasSavedDraft(item)) acc.savedDrafts += 1;
      if (isTimeCritical(item)) acc.timeCritical += 1;
      if (hasLatestNotification(item) && !isLatestNotificationRead(item)) acc.unreadNotices += 1;
      if (!item.rulesConfirmedAt) acc.missingRules += 1;
      if (item.latestActionType === 'RULES_REMINDER') acc.rulesReminders += 1;
      if (isPendingRulesReminder(item)) acc.pendingRulesReminders += 1;
      if (isConfirmedRulesReminder(item)) acc.confirmedRulesReminders += 1;
      return acc;
    },
    {
      online: 0,
      offline: 0,
      submitted: 0,
      events: 0,
      highRisk: 0,
      savedDrafts: 0,
      timeCritical: 0,
      unreadNotices: 0,
      missingRules: 0,
      rulesReminders: 0,
      pendingRulesReminders: 0,
      confirmedRulesReminders: 0
    }
  );
});

const isUnreadNoticeFilterActive = computed(() => sessionFilter.latestNotificationStatus === 'UNREAD');
const isMissingRulesFilterActive = computed(() => sessionFilter.rulesConfirmationStatus === 'MISSING');
const isRulesReminderFilterActive = computed(() => sessionFilter.latestActionType === 'RULES_REMINDER');
const isPendingRulesReminderFilterActive = computed(
  () => sessionFilter.latestActionType === 'RULES_REMINDER' && sessionFilter.rulesConfirmationStatus === 'MISSING'
);
const isConfirmedRulesReminderFilterActive = computed(
  () => sessionFilter.latestActionType === 'RULES_REMINDER' && sessionFilter.rulesConfirmationStatus === 'CONFIRMED'
);

function resetSessionFilters() {
  sessionFilter.status = 'ALL';
  sessionFilter.minRiskScore = -1;
  sessionFilter.latestNotificationStatus = 'ALL';
  sessionFilter.rulesConfirmationStatus = 'ALL';
  sessionFilter.latestActionType = 'ALL';
}

function applyUnreadNoticeFilter() {
  if (metrics.value.unreadNotices === 0) return;
  sessionFilter.latestNotificationStatus = 'UNREAD';
}

function applyMissingRulesFilter() {
  if (metrics.value.missingRules === 0) return;
  sessionFilter.rulesConfirmationStatus = 'MISSING';
}

function applyRulesReminderFilter() {
  if (metrics.value.rulesReminders === 0) return;
  sessionFilter.latestActionType = 'RULES_REMINDER';
}

function applyPendingRulesReminderFilter() {
  if (metrics.value.pendingRulesReminders === 0) return;
  sessionFilter.latestActionType = 'RULES_REMINDER';
  sessionFilter.rulesConfirmationStatus = 'MISSING';
}

function applyConfirmedRulesReminderFilter() {
  if (metrics.value.confirmedRulesReminders === 0) return;
  sessionFilter.latestActionType = 'RULES_REMINDER';
  sessionFilter.rulesConfirmationStatus = 'CONFIRMED';
}

async function copyNotificationAuditId(notificationId?: number | string | null) {
  try {
    const value = await copyNotificationAuditIdToClipboard(notificationId);
    if (!value) return;
    ElMessage.success(`Notification audit ID copied: ${value}`);
  } catch {
    ElMessage.error('Failed to copy notification audit ID');
  }
}

async function copyNotificationAuditLink(notificationId?: number | string | null) {
  try {
    const link = await copyNotificationAuditLinkToClipboard(notificationId);
    if (!link) return;
    ElMessage.success('Notification audit link copied');
  } catch {
    ElMessage.error('Failed to copy notification audit link');
  }
}

onMounted(async () => {
  await loadExams();
  startPolling();
});

onUnmounted(stopPolling);

watch(autoRefresh, (enabled) => {
  if (enabled) startPolling();
  else stopPolling();
});

async function loadExams() {
  examLoading.value = true;
  try {
    const response = await listTeacherExams({ keyword: keyword.value, page: 1, size: 100 });
    exams.value = response.data.list;
    if (!selectedExamId.value && exams.value.length > 0) {
      selectedExamId.value = exams.value[0].id;
    } else if (selectedExamId.value && !exams.value.some((exam) => exam.id === selectedExamId.value)) {
      selectedExamId.value = exams.value[0]?.id || null;
    }
    await loadSessions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '考试列表加载失败');
  } finally {
    examLoading.value = false;
  }
}

async function loadSessions() {
  if (!selectedExamId.value) {
    sessions.value = [];
    return;
  }
  sessionLoading.value = true;
  try {
    const response = await listExamMonitorSessions(selectedExamId.value);
    sessions.value = response.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '监考会话加载失败');
  } finally {
    sessionLoading.value = false;
  }
}

async function exportSessions() {
  if (!selectedExamId.value) {
    ElMessage.warning('请先选择考试');
    return;
  }
  exporting.value = true;
  try {
    await exportExamMonitorSessions(selectedExamId.value, selectedExam.value?.examName, buildSessionExportQuery());
    ElMessage.success('监考会话导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    exporting.value = false;
  }
}

function buildSessionExportQuery() {
  return {
    sessionStatus: sessionFilter.status === 'ALL' ? undefined : sessionFilter.status,
    minRiskScore: sessionFilter.minRiskScore >= 0 ? sessionFilter.minRiskScore : undefined,
    latestNotificationStatus:
      sessionFilter.latestNotificationStatus === 'ALL' ? undefined : sessionFilter.latestNotificationStatus,
    rulesConfirmationStatus:
      sessionFilter.rulesConfirmationStatus === 'ALL' ? undefined : sessionFilter.rulesConfirmationStatus,
    latestActionType: sessionFilter.latestActionType === 'ALL' ? undefined : sessionFilter.latestActionType
  };
}

async function selectExam(examId: number) {
  selectedExamId.value = examId;
  await loadSessions();
}

async function openIncident(session: MonitorSession, tab: 'health' | 'events' | 'actions' = 'health') {
  clearEventFilters();
  activeSession.value = session;
  drawerTab.value = tab;
  eventDrawerVisible.value = true;
  await loadIncident(session);
}

async function openEvents(session: MonitorSession) {
  await openIncident(session, 'events');
}

async function loadIncident(session: MonitorSession) {
  incidentLoading.value = true;
  eventLoading.value = true;
  actionLoading.value = true;
  try {
    const response = await getMonitorAttemptIncident(session.id);
    incidentDetail.value = response.data;
    activeSession.value = { ...session, ...response.data.session };
    events.value = response.data.events || [];
    actions.value = response.data.actions || [];
  } catch (error) {
    incidentDetail.value = null;
    events.value = [];
    actions.value = [];
    ElMessage.error(error instanceof Error ? error.message : '作答详情加载失败');
  } finally {
    incidentLoading.value = false;
    eventLoading.value = false;
    actionLoading.value = false;
  }
}

async function loadEvents(session: MonitorSession) {
  eventLoading.value = true;
  try {
    const response = await listAttemptMonitorEvents(session.attemptId, buildEventQuery());
    events.value = response.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '事件明细加载失败');
  } finally {
    eventLoading.value = false;
  }
}

function buildEventQuery(): MonitorEventQuery {
  const [startFrom, startTo] = eventFilter.timeRange || [];
  return {
    eventType: eventFilter.eventType === 'ALL' ? undefined : eventFilter.eventType,
    startFrom: startFrom || undefined,
    startTo: startTo || undefined,
    minRiskScore: eventFilter.minRiskScore >= 0 ? eventFilter.minRiskScore : undefined
  };
}

async function reloadActiveEvents() {
  if (!activeSession.value) {
    return;
  }
  await loadEvents(activeSession.value);
}

async function resetEventFilters() {
  clearEventFilters();
  await reloadActiveEvents();
}

function clearEventFilters() {
  eventFilter.eventType = 'ALL';
  eventFilter.timeRange = [];
  eventFilter.minRiskScore = -1;
}

async function exportActiveEvents() {
  if (!activeSession.value) {
    return;
  }
  eventExporting.value = true;
  try {
    await exportAttemptMonitorEvents(
      activeSession.value.attemptId,
      activeSession.value.examName || selectedExam.value?.examName,
      activeSession.value.realName || undefined,
      buildEventQuery()
    );
    ElMessage.success('监考事件导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    eventExporting.value = false;
  }
}

async function exportActiveActions() {
  if (!activeSession.value) {
    return;
  }
  actionExporting.value = true;
  try {
    await exportMonitorActions(
      activeSession.value.id,
      activeSession.value.examName || selectedExam.value?.examName,
      activeSession.value.realName || undefined,
      activeSession.value.attemptId
    );
    ElMessage.success('监考处置记录导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    actionExporting.value = false;
  }
}

async function loadActions(session: MonitorSession) {
  actionLoading.value = true;
  try {
    const response = await listMonitorActions(session.id);
    actions.value = response.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处置记录加载失败');
  } finally {
    actionLoading.value = false;
  }
}

function openAction(session: MonitorSession, actionType: string) {
  actionTarget.value = session;
  actionForm.actionType = actionType;
  actionForm.note = defaultActionNote(session, actionType);
  actionDialogVisible.value = true;
}

async function saveAction() {
  if (!actionTarget.value) return;
  if (actionForm.actionType === 'RULES_REMINDER' && !canSendRulesReminder(actionTarget.value)) {
    ElMessage.warning(rulesReminderUnavailableReason(actionTarget.value));
    return;
  }
  actionSaving.value = true;
  try {
    const response = await createMonitorAction(actionTarget.value.id, {
      actionType: actionForm.actionType,
      note: actionForm.note
    });
    rememberMonitorOperationAudit(actionTypeText(actionForm.actionType), [response.data.operationLogId]);
    ElMessage.success('处置记录已保存');
    actionDialogVisible.value = false;
    if (activeSession.value?.id === actionTarget.value.id) {
      await loadIncident(actionTarget.value);
    }
    await loadSessions();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '处置记录保存失败');
  } finally {
    actionSaving.value = false;
  }
}

async function forceSubmit(session: MonitorSession) {
  try {
    await ElMessageBox.confirm(
      `确认强制提交 ${session.realName || '该学生'} 的当前答卷吗？系统会使用服务器草稿生成最终答卷。`,
      '强制交卷',
      {
        type: 'warning',
        confirmButtonText: '强制交卷',
        cancelButtonText: '取消'
      }
    );
  } catch {
    return;
  }
  try {
    const response = await forceSubmitMonitorSession(session.id, {
      note: '监考教师执行强制交卷'
    });
    rememberMonitorOperationAudit('Force submit', [response.data.operationLogId]);
    ElMessage.success(response.data.actionAlreadyRecorded ? '已强制交卷，处置记录已存在' : '已强制交卷并记录处置');
    await loadSessions();
    if (activeSession.value?.id === session.id) {
      await loadIncident(session);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '强制交卷失败');
  }
}

function rememberMonitorOperationAudit(action: string, ids: Array<number | string | null | undefined>) {
  const operationLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (operationLogIds.length === 0) return;
  lastMonitorOperationAudit.value = { action, operationLogIds };
}

function monitorOperationAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestMonitorOperationAuditId() {
  const ids = lastMonitorOperationAudit.value?.operationLogIds;
  if (!ids?.length) return;
  try {
    await copyOperationLogIdToClipboard(ids.join(','));
    ElMessage.success('Audit ID copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit ID copy failed');
  }
}

async function copyLatestMonitorOperationAuditLink() {
  const id = lastMonitorOperationAudit.value?.operationLogIds[0];
  if (!id) return;
  try {
    await copyOperationLogLinkToClipboard(id);
    ElMessage.success('Audit link copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit link copy failed');
  }
}

function startPolling() {
  stopPolling();
  if (!autoRefresh.value) return;
  refreshTimer = setInterval(loadSessions, 10000);
}

function stopPolling() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

function defaultActionNote(session: MonitorSession, actionType: string) {
  const risk = `当前风险分 ${session.riskScore || 0}`;
  if (actionType === 'ACKNOWLEDGE') return `${risk}，已关注该考生状态。`;
  if (actionType === 'WARN') return `${risk}，已提醒学生遵守考试纪律。`;
  if (actionType === 'RULES_REMINDER') return `${risk}，已提醒学生完成考试规则确认。`;
  if (actionType === 'FORCE_SUBMIT') return `${risk}，因监考异常执行强制交卷。`;
  return `${risk}。`;
}

function canSendRulesReminder(session: MonitorSession) {
  return !session.rulesConfirmedAt && Number(session.attemptStatus ?? 0) <= 1;
}

function rulesReminderUnavailableReason(session: MonitorSession) {
  if (session.rulesConfirmedAt) return 'Rules already confirmed by the student.';
  if (Number(session.attemptStatus ?? 0) > 1) return 'Rules reminders require an active attempt.';
  return '';
}

function phaseText(row: ExamInfo) {
  const now = Date.now();
  const start = new Date(row.startTime).getTime();
  const end = new Date(row.endTime).getTime();
  if (Number.isNaN(start) || Number.isNaN(end)) return '已创建';
  if (now < start) return '未开始';
  if (now > end) return '已结束';
  return '进行中';
}

function sessionStatusText(value: string) {
  const map: Record<string, string> = {
    ONLINE: '在线',
    OFFLINE: '离线',
    SUBMITTED: '已交卷'
  };
  return map[value] || value || '-';
}

function sessionStatusType(value: string) {
  if (value === 'ONLINE') return 'success';
  if (value === 'SUBMITTED') return 'info';
  return 'warning';
}

function riskType(score: number) {
  if (score >= thresholds.value.high) return 'danger';
  if (score >= thresholds.value.warning) return 'warning';
  return 'info';
}

function riskLevelText(value?: string | null) {
  const map: Record<string, string> = {
    NORMAL: '正常',
    WARNING: '提示',
    HIGH: '高风险'
  };
  return value ? map[value] || value : '正常';
}

function incidentHealthAlertType(level?: string | null) {
  if (level === 'HIGH') return 'error';
  if (level === 'WARNING') return 'warning';
  if (level === 'INFO') return 'info';
  return 'success';
}

function incidentHealthTagType(level?: string | null) {
  if (level === 'HIGH') return 'danger';
  if (level === 'WARNING') return 'warning';
  if (level === 'INFO') return 'info';
  return 'success';
}

function incidentFindingTagType(finding: MonitorIncidentFinding) {
  if (finding.severity === 'HIGH') return 'danger';
  if (finding.severity === 'WARNING') return 'warning';
  if (finding.severity === 'INFO') return 'info';
  return 'success';
}

function attemptStatusText(value?: number | string | null) {
  const status = Number(value);
  const map: Record<number, string> = {
    0: 'Not started',
    1: 'In progress',
    2: 'Submitted',
    3: 'Reviewing',
    4: 'Pending review',
    5: 'Completed'
  };
  return Number.isFinite(status) ? map[status] || String(value) : '-';
}

function incidentRemainingText(detail: MonitorIncidentDetail) {
  return remainingTimeText({
    ...detail.session,
    attemptStatus: detail.attempt.attemptStatus,
    submitTime: detail.attempt.submitTime,
    remainingSeconds: detail.attempt.remainingSeconds,
    deadlineAt: detail.attempt.deadlineAt
  } as MonitorSession);
}

function draftEvidenceText(detail: MonitorIncidentDetail) {
  if (!detail.draft.exists) return 'No server draft';
  const revision = detail.draft.revision ?? detail.attempt.draftRevision ?? 0;
  const savedCount = detail.draft.savedCount ?? 0;
  return `${detail.draft.source || 'DB'} · r${revision} · saved ${savedCount}`;
}

function draftFilledText(detail: MonitorIncidentDetail) {
  const filled = Number(detail.draft.filledAnswerCount || 0);
  const total = Number(detail.draft.answerKeyCount || 0);
  const suffix = detail.draft.parseError ? ' · parse warning' : '';
  return `${filled}/${total} filled in draft${suffix}`;
}

function submissionEvidenceText(detail: MonitorIncidentDetail) {
  if (detail.attempt.submitTime) {
    return detail.attempt.submitType ? submitTypeText(detail.attempt.submitType) : 'Submitted';
  }
  return attemptStatusText(detail.attempt.attemptStatus);
}

function submitTypeText(value?: string | null) {
  const map: Record<string, string> = {
    MANUAL: 'Manual submit',
    AUTO: 'Auto submit',
    TIMEOUT: 'Timeout submit',
    FORCED: 'Force submit'
  };
  return value ? map[value] || value : '-';
}

function payloadHashText(value?: string | null) {
  if (!value) return '-';
  return value.length > 16 ? `${value.slice(0, 16)}...` : value;
}

function incidentAnswerStatsText(detail: MonitorIncidentDetail) {
  const total = Number(detail.answerStats.questionCount || 0);
  const answered = Number(detail.answerStats.answeredCount || 0);
  const unanswered = Number(detail.answerStats.unansweredCount || 0);
  return `${answered}/${total} answered, ${unanswered} unanswered`;
}

function forceSubmitEvidenceText(detail: MonitorIncidentDetail) {
  if (!detail.forceSubmitEvidence.exists) return 'No force-submit evidence';
  const action = detail.forceSubmitEvidence.action;
  if (action?.id) return `Action #${action.id}`;
  return detail.forceSubmitEvidence.submitForced ? 'Forced submit recorded' : 'Force action recorded';
}

function eventTypeText(value?: string | null) {
  const map: Record<string, string> = {
    VISIBILITY_HIDDEN: '页面隐藏',
    WINDOW_BLUR: '窗口失焦',
    COPY: '复制',
    PASTE: '粘贴',
    FULLSCREEN_EXIT: '退出全屏',
    PAGE_UNLOAD_ATTEMPT: '尝试离开页面',
    HISTORY_BACK_ATTEMPT: '尝试返回上一页',
    CONTEXT_MENU: '打开右键菜单',
    NETWORK_OFFLINE: '网络断开',
    NETWORK_ONLINE: '网络恢复',
    HEARTBEAT_FAILED: '心跳失败'
  };
  return value ? map[value] || value : '-';
}

function actionTypeText(value?: string | null) {
  const map: Record<string, string> = {
    WARN: '提醒学生',
    RULES_REMINDER: 'Rules reminder',
    ACKNOWLEDGE: '已关注',
    FORCE_SUBMIT: '强制交卷',
    NOTE: '备注'
  };
  return value ? map[value] || value : '未处置';
}

function actionNotificationText(action: MonitorAction) {
  if (action.notificationSent === true || action.notificationSent === 1 || action.notificationSent === '1') {
    const prefix = action.notificationId ? `Notification #${action.notificationId}` : 'Notification sent';
    return `${prefix} · ${isNotificationRead(action) ? 'Read' : 'Unread'}`;
  }
  if (isNotificationActionType(action.actionType)) {
    return 'Notification not sent';
  }
  return 'No notification';
}

function actionNotificationTagType(action: MonitorAction) {
  if (action.notificationSent === true || action.notificationSent === 1 || action.notificationSent === '1') {
    return isNotificationRead(action) ? 'success' : 'warning';
  }
  if (isNotificationActionType(action.actionType)) {
    return 'warning';
  }
  return 'info';
}

function isNotificationRead(action: MonitorAction) {
  return action.notificationRead === true || action.notificationRead === 1 || action.notificationRead === '1';
}

function latestNotificationText(session: MonitorSession) {
  if (session.latestActionNotificationSent === true || session.latestActionNotificationSent === 1 || session.latestActionNotificationSent === '1') {
    const prefix = session.latestActionNotificationId ? `Notification #${session.latestActionNotificationId}` : 'Notification sent';
    return `${prefix} · ${isLatestNotificationRead(session) ? 'Read' : 'Unread'}`;
  }
  if (isNotificationActionType(session.latestActionType)) {
    return 'Notification not sent';
  }
  return 'No notification';
}

function latestNotificationTagType(session: MonitorSession) {
  if (session.latestActionNotificationSent === true || session.latestActionNotificationSent === 1 || session.latestActionNotificationSent === '1') {
    return isLatestNotificationRead(session) ? 'success' : 'warning';
  }
  if (isNotificationActionType(session.latestActionType)) {
    return 'warning';
  }
  return 'info';
}

function isNotificationActionType(actionType?: string | null) {
  return actionType === 'WARN' || actionType === 'RULES_REMINDER' || actionType === 'FORCE_SUBMIT';
}

function isLatestNotificationRead(session: MonitorSession) {
  return session.latestActionNotificationRead === true || session.latestActionNotificationRead === 1 || session.latestActionNotificationRead === '1';
}

function hasLatestNotification(session: MonitorSession) {
  return (
    session.latestActionNotificationSent === true ||
    session.latestActionNotificationSent === 1 ||
    session.latestActionNotificationSent === '1'
  );
}

function latestNotificationStatusMatches(session: MonitorSession, status: string) {
  if (status === 'ALL') return true;
  const sent = hasLatestNotification(session);
  const read = isLatestNotificationRead(session);
  if (status === 'SENT') return sent;
  if (status === 'UNREAD') return sent && !read;
  if (status === 'READ') return sent && read;
  if (status === 'NONE') return !sent;
  return true;
}

function rulesConfirmationStatusMatches(session: MonitorSession, status: string) {
  if (status === 'ALL') return true;
  const confirmed = Boolean(session.rulesConfirmedAt);
  if (status === 'CONFIRMED') return confirmed;
  if (status === 'MISSING') return !confirmed;
  return true;
}

function latestActionTypeMatches(session: MonitorSession, actionType: string) {
  if (actionType === 'ALL') return true;
  return session.latestActionType === actionType;
}

function isPendingRulesReminder(session: MonitorSession) {
  return session.latestActionType === 'RULES_REMINDER' && !session.rulesConfirmedAt;
}

function isConfirmedRulesReminder(session: MonitorSession) {
  return session.latestActionType === 'RULES_REMINDER' && Boolean(session.rulesConfirmedAt);
}

function rulesReminderResolutionText(session: MonitorSession) {
  if (session.latestActionType !== 'RULES_REMINDER') return '';
  return session.rulesConfirmedAt ? 'Confirmed after reminder' : 'Pending confirmation';
}

function rulesReminderResolutionTagType(session: MonitorSession) {
  return session.rulesConfirmedAt ? 'success' : 'danger';
}

function hasSavedDraft(session: MonitorSession) {
  return Boolean(session.lastDraftSavedAt) || Number(session.draftRevision || 0) > 0;
}

function remainingSecondsValue(session: MonitorSession) {
  const value = Number(session.remainingSeconds);
  return Number.isFinite(value) ? value : null;
}

function isTimeCritical(session: MonitorSession) {
  const remaining = remainingSecondsValue(session);
  return Number(session.attemptStatus || 0) === 1 && remaining !== null && remaining <= 300;
}

function remainingTimeText(session: MonitorSession) {
  if (session.submitTime || Number(session.attemptStatus || 0) >= 2) return 'Submitted';
  const remaining = remainingSecondsValue(session);
  if (remaining === null) return 'No server deadline';
  if (remaining <= 0) return 'Deadline passed';
  const hours = Math.floor(remaining / 3600);
  const minutes = Math.floor((remaining % 3600) / 60);
  const seconds = Math.floor(remaining % 60);
  if (hours > 0) return `${hours}h ${minutes}m ${seconds}s left`;
  if (minutes > 0) return `${minutes}m ${seconds}s left`;
  return `${seconds}s left`;
}

function draftTelemetryText(session: MonitorSession) {
  const revision = Number(session.draftRevision || 0);
  if (!session.lastDraftSavedAt && revision <= 0) return 'Draft: not saved';
  return `Draft: ${formatDateTime(session.lastDraftSavedAt)} · r${revision}`;
}

function submitText(row: MonitorSession) {
  if (row.submitTime) {
    const submitBase = `${formatDateTime(row.submitTime)}${row.submitType ? ` · ${row.submitType}` : ''}`;
    const stats = submitAnswerStatsText(row);
    return stats ? `${submitBase} · ${stats}` : submitBase;
  }
  return row.attemptStatus === 1 ? '作答中' : '-';
}

function submitAnswerStatsText(row: MonitorSession) {
  const questionTotal = Number(row.questionCount);
  const answered = Number(row.answeredCount);
  const unanswered = Number(row.unansweredCount);
  if (![questionTotal, answered, unanswered].every(Number.isFinite)) return '';
  return `${answered}/${questionTotal} answered, ${unanswered} unanswered`;
}

function prettyExtra(value?: string | null) {
  if (!value) return '-';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
</script>

<style scoped>
.exam-monitor {
  min-width: 0;
}

.monitor-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}

.exam-list-panel,
.monitor-main {
  min-width: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.exam-list-panel {
  padding: 14px;
}

.panel-head,
.monitor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.panel-head h3,
.monitor-toolbar h2 {
  margin: 0;
  color: #111827;
}

.panel-head p,
.monitor-toolbar p,
.threshold-line {
  margin: 5px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.exam-search {
  margin: 14px 0;
}

.exam-list {
  display: grid;
  gap: 8px;
  max-height: 680px;
  overflow: auto;
}

.exam-option {
  width: 100%;
  min-height: 86px;
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  text-align: left;
  cursor: pointer;
}

.exam-option:hover,
.exam-option.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.exam-option strong,
.exam-option span,
.exam-option small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exam-option span,
.exam-option small {
  color: #64748b;
}

.monitor-main {
  padding: 16px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.threshold-line {
  margin-top: 10px;
}

.monitor-operation-audit {
  margin-top: 12px;
}

.monitor-operation-audit-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.session-filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.session-status-filter,
.session-risk-filter,
.session-notification-filter,
.session-rules-filter,
.session-action-filter {
  width: 170px;
}

.filter-count {
  color: #64748b;
  font-size: 13px;
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 12px;
  margin: 16px 0;
}

.metric-card {
  min-height: 82px;
  display: grid;
  align-content: center;
  width: 100%;
  text-align: left;
  gap: 6px;
  padding: 14px;
  border-radius: 8px;
  border: 1px solid #e5e7eb;
  background: #f8fafc;
}

button.metric-card {
  appearance: none;
  font: inherit;
  cursor: pointer;
}

button.metric-card:hover {
  border-color: #f59e0b;
}

button.metric-card.active {
  border-color: #d97706;
  box-shadow: inset 0 0 0 1px #d97706;
}

button.metric-card:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

button.metric-card:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.metric-card span {
  color: #64748b;
  font-size: 13px;
}

.metric-card strong {
  font-size: 28px;
  color: #111827;
}

.metric-card.online {
  border-color: #bbf7d0;
  background: #f0fdf4;
}

.metric-card.offline {
  border-color: #fed7aa;
  background: #fff7ed;
}

.metric-card.submitted {
  border-color: #dbeafe;
  background: #eff6ff;
}

.metric-card.draft {
  border-color: #bae6fd;
  background: #f0f9ff;
}

.metric-card.timeout {
  border-color: #fcd34d;
  background: #fffbeb;
}

.metric-card.risk,
.metric-card.danger {
  border-color: #fecaca;
  background: #fef2f2;
}

.metric-card.notice {
  border-color: #fde68a;
  background: #fffbeb;
}

.metric-card.rules {
  border-color: #bfdbfe;
  background: #eff6ff;
}

.metric-card.rules-reminder {
  border-color: #c4b5fd;
  background: #f5f3ff;
}

.metric-card.pending-reminder {
  border-color: #fbcfe8;
  background: #fdf2f8;
}

.metric-card.confirmed-reminder {
  border-color: #a7f3d0;
  background: #ecfdf5;
}

.student-cell,
.action-cell,
.runtime-cell,
.drawer-head > div {
  display: grid;
  gap: 4px;
}

.action-cell .el-button {
  justify-self: start;
  padding: 0;
}

.action-state-alert {
  margin-bottom: 16px;
}

.student-cell span,
.action-cell span,
.runtime-cell span,
.drawer-head span {
  color: #64748b;
  font-size: 13px;
}

.runtime-cell strong {
  color: #111827;
}

.runtime-cell strong.danger {
  color: #dc2626;
}

.drawer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.drawer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.incident-drawer-body {
  min-height: 360px;
}

.incident-summary {
  margin-bottom: 14px;
}

.incident-overview {
  display: grid;
  gap: 14px;
}

.incident-health-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.incident-stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.incident-card {
  min-width: 0;
  display: grid;
  gap: 6px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.incident-card span {
  color: #64748b;
  font-size: 12px;
  text-transform: uppercase;
}

.incident-card strong {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #111827;
}

.incident-card small {
  min-width: 0;
  overflow-wrap: anywhere;
  color: #64748b;
}

.incident-findings {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.event-filter-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.event-type-filter,
.event-risk-filter {
  width: 170px;
}

.event-time-filter {
  width: min(380px, 100%);
}

.event-card {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.event-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}

.action-notification-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.event-card p {
  margin: 0;
  color: #374151;
  line-height: 1.6;
}

.event-card pre {
  max-height: 180px;
  margin: 0;
  padding: 10px;
  overflow: auto;
  border-radius: 6px;
  background: #111827;
  color: #e5e7eb;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 1100px) {
  .monitor-layout {
    grid-template-columns: 1fr;
  }

  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .monitor-toolbar,
  .panel-head,
  .drawer-head {
    align-items: flex-start;
    flex-direction: column;
  }

  .drawer-actions {
    flex-wrap: wrap;
  }

  .incident-stat-grid {
    grid-template-columns: 1fr;
  }

  .event-type-filter,
  .event-risk-filter,
  .session-status-filter,
  .session-risk-filter,
  .session-notification-filter,
  .session-rules-filter,
  .session-action-filter,
  .event-time-filter {
    width: 100%;
  }

  .toolbar-actions {
    width: 100%;
    justify-content: space-between;
  }

  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
