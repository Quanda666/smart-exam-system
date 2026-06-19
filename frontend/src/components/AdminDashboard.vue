<template>
  <section class="dashboard admin-dashboard">
    <div class="dashboard-head">
      <div>
        <h2 class="mp-greeting">{{ greeting }}，管理员</h2>
        <p>系统用户、考试运行、题库和成绩趋势都在这里集中查看。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="emit('navigate', '/system/users')">新建用户</el-button>
    </div>

    <div class="mp-quick-actions">
      <div class="mp-quick-action" @click="emit('navigate', '/system/users')"><el-icon><Plus /></el-icon>新建用户</div>
      <div class="mp-quick-action" @click="emit('navigate', pendingTeacherReviewRoute)"><el-icon><Avatar /></el-icon>待审教师</div>
      <div class="mp-quick-action" @click="emit('navigate', rejectedTeacherReviewRoute)"><el-icon><CircleClose /></el-icon>驳回教师</div>
      <div class="mp-quick-action" @click="emit('navigate', '/basic/notices')"><el-icon><Bell /></el-icon>发布公告</div>
      <div class="mp-quick-action" @click="emit('navigate', '/exam-approvals')"><el-icon><Tickets /></el-icon>考试审批</div>
      <div class="mp-quick-action" @click="emit('navigate', '/question-bank')"><el-icon><Collection /></el-icon>题库管理</div>
      <div class="mp-quick-action" @click="emit('navigate', '/exam/analysis')"><el-icon><DataAnalysis /></el-icon>成绩分析</div>
    </div>

    <div class="mp-card admin-action-center">
      <div class="mp-card-title admin-action-title">
        <span>
          <el-icon><Warning /></el-icon>
          Action Center
        </span>
        <el-tag :type="data.actionCenter.total > 0 ? 'warning' : 'success'" effect="plain">
          {{ data.actionCenter.total }} open
        </el-tag>
      </div>
      <div class="admin-action-summary">
        <button
          v-for="item in actionSummaryRows"
          :key="item.key"
          type="button"
          class="admin-action-summary-item"
          @click="emit('navigate', item.target)"
        >
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </button>
      </div>
      <div class="admin-action-list">
        <button
          v-for="item in data.actionCenter.items"
          :key="actionItemKey(item)"
          type="button"
          class="admin-action-row"
          @click="openActionItem(item)"
        >
          <span :class="['admin-action-icon', `severity-${String(item.severity || 'INFO').toLowerCase()}`]">
            <el-icon><component :is="actionIcon(item.type)" /></el-icon>
          </span>
          <span class="admin-action-main">
            <strong>{{ item.title }}</strong>
            <small>{{ item.subject || '-' }}</small>
            <small>{{ item.detail || '-' }}</small>
          </span>
          <span class="admin-action-side">
            <el-tag :type="actionSeverityType(item.severity)" size="small">{{ item.count }}</el-tag>
          </span>
        </button>
        <el-empty v-if="data.actionCenter.items.length === 0" description="No open admin actions" :image-size="80" />
      </div>
    </div>

    <div class="approval-sla-panel">
      <div class="approval-sla-main">
        <div class="mp-card-title">
          <el-icon><Tickets /></el-icon>
          考试审批提醒
        </div>
        <p>
          当前有 <strong>{{ data.approvalSummary.pending }}</strong> 场考试等待审批，
          <strong>{{ data.approvalSummary.overdue }}</strong> 场超过 {{ data.approvalSummary.overdueHours }} 小时未处理，
          <strong>{{ data.approvalSummary.startPassed }}</strong> 场已过计划开考时间。
        </p>
        <div class="approval-sla-stats">
          <span>近 30 天平均审批 {{ data.approvalSummary.avgApprovalHours }} 小时</span>
          <span>驳回率 {{ data.approvalSummary.rejectionRate }}%</span>
          <span>处理 {{ data.approvalSummary.decisionCount30d }} 次</span>
        </div>
      </div>
      <div class="approval-sla-actions">
        <el-button type="primary" :icon="Tickets" @click="emit('navigate', '/exam-approvals')">进入审批队列</el-button>
        <el-button :loading="reminding" :icon="Bell" @click="sendReminders">发送提醒</el-button>
      </div>
    </div>

    <div class="ops-capacity-panel">
      <div class="ops-capacity-head">
        <div>
          <div class="mp-card-title">
            <el-icon><Warning /></el-icon>
            运维容量
          </div>
          <p>{{ data.opsCapacity.alertMessage }}</p>
        </div>
        <div class="ops-capacity-actions">
          <el-tag :type="opsLevelTagType(data.opsCapacity.alertLevel)" effect="dark">{{ data.opsCapacity.alertLevel }}</el-tag>
          <el-button plain @click="emit('navigate', '/system/config')">系统配置</el-button>
          <el-button plain @click="emit('navigate', '/exam-monitor')">实时监考</el-button>
        </div>
      </div>
      <div class="ops-capacity-grid">
        <div class="ops-card">
          <span>数据库</span>
          <strong>{{ databaseStatusText }}</strong>
          <small>{{ data.opsCapacity.database.latencyMs }} ms · {{ data.opsCapacity.database.message }}</small>
        </div>
        <div class="ops-card">
          <span>草稿缓存</span>
          <strong>{{ data.opsCapacity.draftCache.alertLevel }}</strong>
          <small>dirty {{ data.opsCapacity.draftCache.dirtyCount }} / db {{ data.opsCapacity.draftCache.dbDrafts }} / errors {{ data.opsCapacity.draftCache.errors }}</small>
          <div class="ops-card-actions">
            <el-button link type="primary" @click="openOpsDrilldown('DIRTY_DRAFTS')">Dirty</el-button>
            <el-button link type="primary" @click="openOpsDrilldown('STALE_DB_DRAFTS')">Stale DB</el-button>
          </div>
        </div>
        <div class="ops-card">
          <span>考试运行</span>
          <strong>{{ data.opsCapacity.examRuntime.activeAttempts }} active</strong>
          <div class="ops-card-actions">
            <el-button link type="primary" @click="openOpsDrilldown('TIMEOUT_PRESSURE')">Timeout</el-button>
            <el-button link type="warning" @click="openOpsDrilldown('DEADLINE_PASSED_ACTIVE')">Past deadline</el-button>
          </div>
          <small>{{ data.opsCapacity.examRuntime.runningExams }} running · {{ data.opsCapacity.examRuntime.timeoutPressure }} time critical</small>
        </div>
        <div class="ops-card">
          <span>监考压力</span>
          <strong>{{ data.opsCapacity.monitorRuntime.activeSessions }} sessions</strong>
          <div class="ops-card-actions">
            <el-button link type="warning" @click="openOpsDrilldown('OFFLINE_MONITOR')">Offline</el-button>
            <el-button link type="danger" @click="openOpsDrilldown('HIGH_RISK_MONITOR')">High risk</el-button>
          </div>
          <small>{{ data.opsCapacity.monitorRuntime.offlineActiveSessions }} offline · {{ data.opsCapacity.monitorRuntime.highRiskSessions }} high risk · {{ data.opsCapacity.monitorRuntime.eventsLast10m }} events/10m</small>
        </div>
        <div class="ops-card">
          <span>提交吞吐</span>
          <strong>{{ data.opsCapacity.submitRuntime.submittedToday }} today</strong>
          <div class="ops-card-actions">
            <el-button link type="primary" @click="openOpsDrilldown('FORCED_SUBMITS_TODAY')">Forced today</el-button>
          </div>
          <small>{{ data.opsCapacity.submitRuntime.timeoutSubmittedToday }} timeout · {{ data.opsCapacity.submitRuntime.forceSubmittedToday }} forced</small>
        </div>
      </div>
      <div class="ops-alert-list">
        <el-tag
          v-for="alert in data.opsCapacity.alerts"
          :key="alert.code"
          :type="opsLevelTagType(alert.level)"
          effect="plain"
        >
          {{ alert.code }} · {{ alert.message }}
        </el-tag>
        <el-tag v-if="data.opsCapacity.alerts.length === 0" type="success" effect="plain">No ops alerts</el-tag>
      </div>
    </div>

    <el-drawer v-model="opsDrilldownVisible" :title="opsDrilldownTitle" size="min(980px, 96vw)" class="ops-drilldown-drawer">
      <div class="ops-drilldown-toolbar">
        <span>{{ opsDrilldownTotal }} records</span>
        <el-button type="success" plain :loading="opsDrilldownExporting" @click="exportCurrentOpsDrilldown">Export</el-button>
      </div>
      <el-table v-loading="opsDrilldownLoading" :data="opsDrilldownRows" border height="560">
        <el-table-column prop="attemptId" label="Attempt" width="92" />
        <el-table-column prop="examName" label="Exam" min-width="180" show-overflow-tooltip />
        <el-table-column label="Student" min-width="150" show-overflow-tooltip>
          <template #default="scope">
            {{ scope.row.studentName || '-' }}<span v-if="scope.row.studentNo"> / {{ scope.row.studentNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="Status" width="95">
          <template #default="scope">{{ attemptStatusText(scope.row.attemptStatus) }}</template>
        </el-table-column>
        <el-table-column label="Deadline" width="170">
          <template #default="scope">{{ formatDateTime(scope.row.serverDeadline) }}</template>
        </el-table-column>
        <el-table-column label="TTL" width="100">
          <template #default="scope">{{ secondsText(scope.row.secondsToDeadline) }}</template>
        </el-table-column>
        <el-table-column label="Risk" width="90">
          <template #default="scope">{{ numberText(scope.row.riskScore) }}</template>
        </el-table-column>
        <el-table-column label="Events" width="90">
          <template #default="scope">{{ numberText(scope.row.eventCount) }}</template>
        </el-table-column>
        <el-table-column label="Heartbeat" width="170">
          <template #default="scope">{{ formatDateTime(scope.row.lastHeartbeatAt) }}</template>
        </el-table-column>
        <el-table-column label="Draft" width="160">
          <template #default="scope">
            rev {{ valueText(scope.row.draftRevision) }} / saves {{ valueText(scope.row.draftSavedCount) }}
          </template>
        </el-table-column>
        <el-table-column label="Updated" width="170">
          <template #default="scope">{{ formatDateTime(scope.row.draftUpdatedAt || scope.row.submitTime || scope.row.lastEventAt) }}</template>
        </el-table-column>
        <el-table-column prop="note" label="Note" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.note || scope.row.lastEventType || scope.row.monitorStatus || '-' }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!opsDrilldownLoading && opsDrilldownRows.length === 0" description="No drilldown records" :image-size="90" />
      <el-pagination
        v-model:current-page="opsDrilldownPage"
        v-model:page-size="opsDrilldownPageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="opsDrilldownTotal"
        layout="total, sizes, prev, pager, next, jumper"
        class="ops-drilldown-pager"
        @current-change="loadOpsDrilldown"
        @size-change="handleOpsDrilldownSizeChange"
      />
    </el-drawer>

    <div class="mp-stat-grid">
      <div
        v-for="card in statCards"
        :key="card.label"
        :class="['mp-stat-card', { 'mp-stat-card-action': card.route }]"
        :role="card.route ? 'button' : undefined"
        :tabindex="card.route ? 0 : undefined"
        @click="openStatCard(card.route)"
        @keyup.enter="openStatCard(card.route)"
      >
        <div class="mp-stat-header">
          <el-icon><component :is="card.icon" /></el-icon>
          {{ card.group }}
        </div>
        <div class="mp-stat-row">
          <div :class="['mp-stat-icon', card.iconClass]">
            <el-icon><component :is="card.icon" /></el-icon>
          </div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">{{ card.label }}</div>
            <div class="mp-stat-value" :style="{ color: card.color }">{{ card.value }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><Warning /></el-icon>
          待审批考试
        </div>
        <div class="approval-list">
          <button
            v-for="exam in data.pendingApprovalExams"
            :key="exam.id"
            type="button"
            class="approval-row"
            @click="openPendingApprovalExam(exam)"
          >
            <span class="approval-main">
              <strong>{{ exam.name }}</strong>
              <small>{{ exam.creatorName || '-' }} / {{ formatDateTime(exam.time) }}</small>
            </span>
            <span class="approval-side">
              <el-tag v-if="exam.pendingHours >= data.approvalSummary.overdueHours" type="danger" size="small">{{ exam.pendingHours }}h</el-tag>
              <el-tag v-else type="warning" size="small">{{ exam.pendingHours }}h</el-tag>
              <span class="approval-risks">
                <em v-for="risk in riskList(exam.riskFlags)" :key="risk">{{ riskText(risk) }}</em>
              </span>
            </span>
          </button>
          <el-empty v-if="!data.pendingApprovalExams?.length" description="暂无待审批考试" :image-size="80" />
        </div>
      </div>

      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><TrendCharts /></el-icon>
          近7天考试通过趋势
        </div>
        <div v-if="data.examTrend?.length" ref="examTrendChart" class="chart-box"></div>
        <el-empty v-else description="暂无提交记录" :image-size="80" />
      </div>

      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><Calendar /></el-icon>
          近期考试运行
        </div>
        <div class="exam-list">
          <button v-for="exam in data.recentExams" :key="exam.id || exam.name" type="button" class="exam-row" @click="openRecentExam(exam)">
            <span class="exam-dot" :class="phaseClass(exam.phase)"></span>
            <span class="exam-main">
              <strong>{{ exam.name }}</strong>
              <small>{{ formatDateTime(exam.time) }} 至 {{ formatDateTime(exam.endTime) }}</small>
            </span>
            <span class="exam-side">
              <el-tag :type="phaseTagType(exam.phase)" size="small">{{ phaseText(exam.phase) }}</el-tag>
              <small>{{ exam.submittedCount || 0 }}/{{ exam.attemptCount || 0 }} 已交</small>
            </span>
          </button>
          <el-empty v-if="!data.recentExams?.length" description="暂无考试" :image-size="80" />
        </div>
      </div>
    </div>

    <div class="dashboard-grid secondary">
      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><Histogram /></el-icon>
          题库学科分布
        </div>
        <div v-if="data.teacherSubjects?.length" ref="teacherSubjectChart" class="chart-box compact"></div>
        <el-empty v-else description="暂无题库数据" :image-size="80" />
      </div>

      <div class="mp-card">
        <div class="mp-card-title">
          <el-icon><PieChart /></el-icon>
          学生年级分布
        </div>
        <div v-if="data.studentGrades?.length" ref="studentGradeChart" class="chart-box compact"></div>
        <el-empty v-else description="暂无学生数据" :image-size="80" />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  Avatar,
  Bell,
  Calendar,
  CircleClose,
  Collection,
  DataAnalysis,
  Document,
  Histogram,
  PieChart,
  Plus,
  Tickets,
  TrendCharts,
  User,
  UserFilled,
  Warning
} from '@element-plus/icons-vue';
import { getJson } from '../api/request';
import {
  exportOpsDrilldown,
  listOpsDrilldown,
  type OpsDrilldownItem,
  type OpsDrilldownType
} from '../api/admin';
import { sendApprovalOverdueReminders } from '../api/exam';
import { useChartAutoResize } from '../composables/useChartAutoResize';
import { formatDateTime } from '../utils/dateFormat';
import * as echarts from 'echarts';

const emit = defineEmits<{ navigate: [path: string] }>();
const { register } = useChartAutoResize();

interface RecentExam {
  id?: number;
  name: string;
  time: string;
  endTime?: string;
  phase: number;
  attemptCount?: number;
  submittedCount?: number;
}

interface ApprovalSummary {
  pending: number;
  overdue: number;
  overdueHours: number;
  startPassed: number;
  avgApprovalHours: number;
  rejectionRate: number;
  decisionCount30d: number;
}

interface PendingApprovalExam {
  id: number;
  name: string;
  time: string;
  createdAt: string;
  creatorName?: string;
  pendingHours: number;
  riskFlags?: string | null;
}

interface OpsAlert {
  level: 'OK' | 'INFO' | 'WARN' | 'HIGH' | 'DISABLED' | string;
  code: string;
  message: string;
}

interface OpsCapacity {
  generatedAt?: string;
  alertLevel: 'OK' | 'INFO' | 'WARN' | 'HIGH' | 'DISABLED' | string;
  alertMessage: string;
  database: {
    connected: boolean;
    latencyMs: number;
    slow?: boolean;
    message: string;
  };
  draftCache: {
    enabled: boolean;
    available: boolean;
    writeBackEnabled: boolean;
    dirtyCount: number;
    dbDrafts: number;
    staleDbDrafts?: number;
    errors: number;
    flushSkipped?: number;
    lastFlushAtEpochMillis?: number;
    lastFlushChecked?: number;
    lastFlushFlushed?: number;
    alertLevel: string;
    alertMessage: string;
  };
  examRuntime: {
    runningExams: number;
    activeAttempts: number;
    submittedLast10m: number;
    timeoutPressure: number;
    deadlinePassedActiveAttempts: number;
    activeAttemptWarningThreshold: number;
    activeAttemptHighThreshold: number;
  };
  monitorRuntime: {
    sessions: number;
    activeSessions: number;
    offlineActiveSessions: number;
    highRiskSessions: number;
    eventsLast10m: number;
    eventsLastHour: number;
    actionsLastHour: number;
    highRiskThreshold: number;
    eventStormWarningThreshold: number;
  };
  submitRuntime: {
    submittedToday: number;
    manualSubmittedToday: number;
    timeoutSubmittedToday: number;
    forceSubmittedToday: number;
    replayedSubmitResponses: number;
  };
  alerts: OpsAlert[];
}

interface AdminActionItem {
  type: string;
  title: string;
  subject?: string;
  detail?: string;
  count: number | string;
  severity?: 'HIGH' | 'WARN' | 'INFO' | string;
  target: string;
  examId?: number | null;
  appealId?: number | null;
  drilldownType?: OpsDrilldownType | string | null;
}

interface AdminActionCenter {
  generatedAt?: string;
  total: number;
  lifecycleActionRequiredExams: number;
  lifecycleRiskExams: number;
  pendingTeacherReviews: number;
  rejectedTeacherReviews: number;
  pendingApprovals: number;
  approvalOverdue: number;
  approvalStartPassed: number;
  pendingReviews: number;
  runningExams: number;
  opsAlerts: number;
  dirtyDrafts: number;
  staleDbDrafts: number;
  timeoutPressure: number;
  deadlinePassedActive: number;
  offlineMonitor: number;
  highRiskMonitor: number;
  forcedSubmitsToday: number;
  items: AdminActionItem[];
}

interface OverviewData {
  totalStudents: number;
  totalTeachers: number;
  todayExams: number;
  runningExams: number;
  pendingReviews: number;
  pendingApprovals: number;
  pendingTeacherReviews: number;
  rejectedTeacherReviews: number;
  totalPapers: number;
  totalQuestions: number;
  approvalSummary: ApprovalSummary;
  teacherSubjects: Array<{ name: string; value: number }>;
  studentGrades: Array<{ name: string; value: number }>;
  examTrend: Array<{ date: string; total: number; passed: number }>;
  recentExams: RecentExam[];
  pendingApprovalExams: PendingApprovalExam[];
  opsCapacity: OpsCapacity;
  actionCenter: AdminActionCenter;
}

const emptyActionCenter = (): AdminActionCenter => ({
  total: 0,
  lifecycleActionRequiredExams: 0,
  lifecycleRiskExams: 0,
  pendingTeacherReviews: 0,
  rejectedTeacherReviews: 0,
  pendingApprovals: 0,
  approvalOverdue: 0,
  approvalStartPassed: 0,
  pendingReviews: 0,
  runningExams: 0,
  opsAlerts: 0,
  dirtyDrafts: 0,
  staleDbDrafts: 0,
  timeoutPressure: 0,
  deadlinePassedActive: 0,
  offlineMonitor: 0,
  highRiskMonitor: 0,
  forcedSubmitsToday: 0,
  items: []
});

const data = ref<OverviewData>({
  totalStudents: 0,
  totalTeachers: 0,
  todayExams: 0,
  runningExams: 0,
  pendingReviews: 0,
  pendingApprovals: 0,
  pendingTeacherReviews: 0,
  rejectedTeacherReviews: 0,
  totalPapers: 0,
  totalQuestions: 0,
  approvalSummary: {
    pending: 0,
    overdue: 0,
    overdueHours: 24,
    startPassed: 0,
    avgApprovalHours: 0,
    rejectionRate: 0,
    decisionCount30d: 0
  },
  teacherSubjects: [],
  studentGrades: [],
  examTrend: [],
  recentExams: [],
  pendingApprovalExams: [],
  opsCapacity: {
    alertLevel: 'OK',
    alertMessage: 'Operations capacity is healthy',
    database: {
      connected: true,
      latencyMs: 0,
      message: '-'
    },
    draftCache: {
      enabled: false,
      available: false,
      writeBackEnabled: false,
      dirtyCount: 0,
      dbDrafts: 0,
      errors: 0,
      alertLevel: 'DISABLED',
      alertMessage: '-'
    },
    examRuntime: {
      runningExams: 0,
      activeAttempts: 0,
      submittedLast10m: 0,
      timeoutPressure: 0,
      deadlinePassedActiveAttempts: 0,
      activeAttemptWarningThreshold: 300,
      activeAttemptHighThreshold: 600
    },
    monitorRuntime: {
      sessions: 0,
      activeSessions: 0,
      offlineActiveSessions: 0,
      highRiskSessions: 0,
      eventsLast10m: 0,
      eventsLastHour: 0,
      actionsLastHour: 0,
      highRiskThreshold: 20,
      eventStormWarningThreshold: 1000
    },
    submitRuntime: {
      submittedToday: 0,
      manualSubmittedToday: 0,
      timeoutSubmittedToday: 0,
      forceSubmittedToday: 0,
      replayedSubmitResponses: 0
    },
    alerts: []
  },
  actionCenter: emptyActionCenter()
});

const greeting = computed(() => {
  const hour = new Date().getHours();
  if (hour < 6) return '夜深了';
  if (hour < 11) return '早上好';
  if (hour < 13) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
});

const databaseStatusText = computed(() => {
  if (!data.value.opsCapacity.database.connected) return 'DOWN';
  return data.value.opsCapacity.database.slow ? 'SLOW' : 'UP';
});
const opsDrilldownTitle = computed(() => `Ops Drilldown · ${opsDrilldownTypeText(opsDrilldownType.value)}`);

const statCards = computed(() => [
  { group: '用户', label: '学生总数', value: data.value.totalStudents, icon: User, iconClass: 'mp-icon-blue', color: '#2563eb' },
  { group: '用户', label: '教师总数', value: data.value.totalTeachers, icon: Avatar, iconClass: 'mp-icon-green', color: '#16a34a' },
  { group: '处理', label: '待审教师', value: data.value.pendingTeacherReviews, icon: Avatar, iconClass: 'mp-icon-orange', color: '#ea580c', route: pendingTeacherReviewRoute },
  { group: '处理', label: '驳回教师', value: data.value.rejectedTeacherReviews, icon: CircleClose, iconClass: 'mp-icon-red', color: '#dc2626', route: rejectedTeacherReviewRoute },
  { group: '考试', label: '今日考试', value: data.value.todayExams, icon: Calendar, iconClass: 'mp-icon-yellow', color: '#d97706' },
  { group: '考试', label: '进行中', value: data.value.runningExams, icon: TrendCharts, iconClass: 'mp-icon-blue', color: '#4f46e5' },
  { group: '处理', label: '待审批', value: data.value.pendingApprovals, icon: Tickets, iconClass: 'mp-icon-purple', color: '#7c3aed' },
  { group: '处理', label: '待批阅', value: data.value.pendingReviews, icon: Warning, iconClass: 'mp-icon-orange', color: '#ea580c' },
  { group: '资源', label: '试卷/题目', value: `${data.value.totalPapers}/${data.value.totalQuestions}`, icon: Tickets, iconClass: 'mp-icon-purple', color: '#0f172a' }
]);

const actionSummaryRows = computed(() => [
  { key: 'lifecycle', label: 'Lifecycle', value: data.value.actionCenter.lifecycleActionRequiredExams, target: '/exam-tasks' },
  { key: 'teachers', label: 'Teacher review', value: data.value.actionCenter.pendingTeacherReviews, target: pendingTeacherReviewRoute },
  { key: 'approvals', label: 'Exam approval', value: data.value.actionCenter.pendingApprovals, target: '/exam-approvals' },
  { key: 'approval-risk', label: 'Approval risk', value: data.value.actionCenter.approvalOverdue + data.value.actionCenter.approvalStartPassed, target: '/exam-approvals' },
  { key: 'review', label: 'Review backlog', value: data.value.actionCenter.pendingReviews, target: '/reviews' },
  { key: 'monitor', label: 'Live monitor', value: data.value.actionCenter.runningExams, target: '/exam-monitor' },
  { key: 'ops', label: 'Ops alerts', value: data.value.actionCenter.opsAlerts, target: '/system/config' }
]);

const teacherSubjectChart = ref<HTMLElement>();
const studentGradeChart = ref<HTMLElement>();
const examTrendChart = ref<HTMLElement>();
const reminding = ref(false);
const opsDrilldownVisible = ref(false);
const opsDrilldownLoading = ref(false);
const opsDrilldownExporting = ref(false);
const opsDrilldownType = ref<OpsDrilldownType>('TIMEOUT_PRESSURE');
const opsDrilldownRows = ref<OpsDrilldownItem[]>([]);
const opsDrilldownPage = ref(1);
const opsDrilldownPageSize = ref(10);
const opsDrilldownTotal = ref(0);
const pendingTeacherReviewRoute = '/system/users?role=TEACHER&status=0&teacherStatus=0';
const rejectedTeacherReviewRoute = '/system/users?role=TEACHER&status=0&teacherStatus=2';

onMounted(async () => {
  try {
    const overview = (await getJson<OverviewData>('/api/overview/admin')).data;
    data.value = {
      ...data.value,
      ...overview,
      approvalSummary: {
        ...data.value.approvalSummary,
        ...(overview.approvalSummary || {})
      },
      opsCapacity: mergeOpsCapacity(overview.opsCapacity),
      actionCenter: {
        ...emptyActionCenter(),
        ...(overview.actionCenter || {}),
        items: overview.actionCenter?.items || []
      }
    };
    await new Promise((resolve) => setTimeout(resolve, 100));
    renderCharts();
  } catch {
    ElMessage.error('概况数据加载失败');
  }
});

function mergeOpsCapacity(ops?: Partial<OpsCapacity>): OpsCapacity {
  const fallback = data.value.opsCapacity;
  return {
    ...fallback,
    ...(ops || {}),
    database: {
      ...fallback.database,
      ...(ops?.database || {})
    },
    draftCache: {
      ...fallback.draftCache,
      ...(ops?.draftCache || {})
    },
    examRuntime: {
      ...fallback.examRuntime,
      ...(ops?.examRuntime || {})
    },
    monitorRuntime: {
      ...fallback.monitorRuntime,
      ...(ops?.monitorRuntime || {})
    },
    submitRuntime: {
      ...fallback.submitRuntime,
      ...(ops?.submitRuntime || {})
    },
    alerts: ops?.alerts || []
  };
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
      ElMessage.success(`已向 ${result.adminCount} 名管理员发送 ${result.overdueExamCount} 场超时审批提醒`);
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送提醒失败');
  } finally {
    reminding.value = false;
  }
}

function openStatCard(route?: string) {
  if (route) {
    emit('navigate', route);
  }
}

function actionIcon(type?: string) {
  const normalized = String(type || '').toUpperCase();
  if (normalized.includes('TEACHER')) return Avatar;
  if (normalized.includes('LIFECYCLE')) return Warning;
  if (normalized.includes('APPROVAL')) return Tickets;
  if (normalized.includes('REVIEW')) return Document;
  if (normalized.includes('RUNNING') || normalized.includes('TIMEOUT')) return TrendCharts;
  if (normalized.includes('DRAFT') || normalized.includes('MONITOR') || normalized.includes('OPS')) return Warning;
  if (normalized.includes('FORCED')) return Bell;
  return Warning;
}

function actionSeverityType(severity?: string) {
  const normalized = String(severity || '').toUpperCase();
  if (normalized === 'HIGH') return 'danger';
  if (normalized === 'WARN') return 'warning';
  return 'info';
}

function actionItemKey(item: AdminActionItem) {
  return `${item.type}-${item.examId || item.drilldownType || 'none'}-${item.appealId || item.title}`;
}

function openActionItem(item: AdminActionItem) {
  const drilldownType = toOpsDrilldownType(item.drilldownType);
  if (drilldownType) {
    openOpsDrilldown(drilldownType);
    return;
  }
  emit('navigate', item.target || '/system/users');
}

function toOpsDrilldownType(value?: string | null): OpsDrilldownType | null {
  const normalized = String(value || '').toUpperCase();
  const allowed: Record<OpsDrilldownType, true> = {
    TIMEOUT_PRESSURE: true,
    DEADLINE_PASSED_ACTIVE: true,
    OFFLINE_MONITOR: true,
    HIGH_RISK_MONITOR: true,
    STALE_DB_DRAFTS: true,
    DIRTY_DRAFTS: true,
    FORCED_SUBMITS_TODAY: true
  };
  return normalized in allowed ? normalized as OpsDrilldownType : null;
}

function opsLevelTagType(level?: string | null) {
  if (level === 'HIGH') return 'danger';
  if (level === 'WARN') return 'warning';
  if (level === 'INFO') return 'info';
  if (level === 'DISABLED') return 'info';
  return 'success';
}

async function openOpsDrilldown(type: OpsDrilldownType) {
  opsDrilldownType.value = type;
  opsDrilldownPage.value = 1;
  opsDrilldownVisible.value = true;
  await loadOpsDrilldown();
}

async function loadOpsDrilldown() {
  opsDrilldownLoading.value = true;
  try {
    const response = await listOpsDrilldown(
      opsDrilldownType.value,
      opsDrilldownPage.value,
      opsDrilldownPageSize.value
    );
    opsDrilldownRows.value = response.data.list;
    opsDrilldownTotal.value = response.data.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Ops drilldown load failed');
  } finally {
    opsDrilldownLoading.value = false;
  }
}

function handleOpsDrilldownSizeChange(size: number) {
  opsDrilldownPageSize.value = size;
  opsDrilldownPage.value = 1;
  loadOpsDrilldown();
}

async function exportCurrentOpsDrilldown() {
  opsDrilldownExporting.value = true;
  try {
    await exportOpsDrilldown(opsDrilldownType.value);
    ElMessage.success('Ops drilldown export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Ops drilldown export failed');
  } finally {
    opsDrilldownExporting.value = false;
  }
}

function opsDrilldownTypeText(type: OpsDrilldownType) {
  const map: Record<OpsDrilldownType, string> = {
    TIMEOUT_PRESSURE: 'Timeout pressure',
    DEADLINE_PASSED_ACTIVE: 'Past-deadline active attempts',
    OFFLINE_MONITOR: 'Offline monitor sessions',
    HIGH_RISK_MONITOR: 'High-risk monitor sessions',
    STALE_DB_DRAFTS: 'Stale database drafts',
    DIRTY_DRAFTS: 'Dirty Redis drafts',
    FORCED_SUBMITS_TODAY: 'Forced submissions today'
  };
  return map[type] || type;
}

function attemptStatusText(status?: number | null) {
  const map: Record<number, string> = {
    0: 'Not started',
    1: 'Active',
    2: 'Submitted',
    4: 'Review',
    5: 'Completed'
  };
  return status === null || status === undefined ? '-' : map[status] || String(status);
}

function secondsText(value?: number | string | null) {
  if (value === null || value === undefined || value === '') return '-';
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) return '-';
  if (parsed < 0) return `${parsed}s`;
  const minutes = Math.floor(parsed / 60);
  const seconds = Math.floor(parsed % 60);
  return minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
}

function numberText(value?: number | string | null) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? String(parsed) : '-';
}

function valueText(value?: number | string | null) {
  return value === null || value === undefined || value === '' ? '-' : String(value);
}

function renderCharts() {
  if (examTrendChart.value && data.value.examTrend.length) {
    const chart = echarts.init(examTrendChart.value);
    register(chart, examTrendChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { data: ['提交人次', '通过人次'], top: 0 },
      xAxis: { type: 'category', data: data.value.examTrend.map((item) => item.date) },
      yAxis: { type: 'value' },
      series: [
        { name: '提交人次', type: 'line', smooth: true, data: data.value.examTrend.map((item) => item.total), itemStyle: { color: '#4f46e5' } },
        { name: '通过人次', type: 'line', smooth: true, data: data.value.examTrend.map((item) => item.passed), itemStyle: { color: '#16a34a' } }
      ],
      grid: { top: 42, right: 20, bottom: 28, left: 42 }
    });
  }

  if (teacherSubjectChart.value && data.value.teacherSubjects.length) {
    const chart = echarts.init(teacherSubjectChart.value);
    register(chart, teacherSubjectChart.value);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      xAxis: { type: 'category', data: data.value.teacherSubjects.map((item) => item.name), axisLabel: { rotate: 25 } },
      yAxis: { type: 'value' },
      series: [{ type: 'bar', data: data.value.teacherSubjects.map((item) => item.value), itemStyle: { color: '#4f46e5', borderRadius: [6, 6, 0, 0] } }],
      grid: { top: 18, right: 18, bottom: 58, left: 42 }
    });
  }

  if (studentGradeChart.value && data.value.studentGrades.length) {
    const chart = echarts.init(studentGradeChart.value);
    register(chart, studentGradeChart.value);
    chart.setOption({
      tooltip: { trigger: 'item' },
      color: ['#4f46e5', '#16a34a', '#d97706', '#dc2626', '#7c3aed', '#0891b2'],
      series: [{
        type: 'pie',
        radius: ['44%', '72%'],
        data: data.value.studentGrades.map((item) => ({ name: item.name || '未分班', value: item.value })),
        label: { formatter: '{b}\n{c}人', fontSize: 12, color: '#475569' }
      }]
    });
  }
}

function phaseText(phase?: number) {
  if (phase === 1) return '进行中';
  if (phase === 2) return '已结束';
  return '待开始';
}

function phaseTagType(phase?: number) {
  if (phase === 1) return 'success';
  if (phase === 2) return 'info';
  return 'warning';
}

function phaseClass(phase?: number) {
  if (phase === 1) return 'running';
  if (phase === 2) return 'ended';
  return 'waiting';
}

function openRecentExam(exam: RecentExam) {
  const examId = Number(exam.id || 0);
  if (Number.isFinite(examId) && examId > 0) {
    emit('navigate', `/exam-tasks?examId=${examId}`);
    return;
  }
  emit('navigate', '/exam-tasks');
}

function openPendingApprovalExam(exam: PendingApprovalExam) {
  const examId = Number(exam.id || 0);
  if (Number.isFinite(examId) && examId > 0) {
    emit('navigate', `/exam-approvals?examId=${examId}`);
    return;
  }
  emit('navigate', '/exam-approvals');
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
</script>

<style scoped>
.dashboard-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 14px;
}

.dashboard-head p {
  margin: 6px 0 0;
  color: #64748b;
}

.admin-action-center {
  margin-bottom: 16px;
}

.admin-action-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.admin-action-title > span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.admin-action-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(132px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.admin-action-summary-item {
  min-height: 58px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  color: #111827;
  text-align: left;
  cursor: pointer;
}

.admin-action-summary-item:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.admin-action-summary-item span {
  color: #64748b;
  font-size: 12px;
}

.admin-action-summary-item strong {
  color: #111827;
  font-size: 18px;
}

.admin-action-list {
  display: grid;
  gap: 10px;
}

.admin-action-row {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.admin-action-row:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.admin-action-icon {
  width: 38px;
  height: 38px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #eff6ff;
  color: #2563eb;
}

.admin-action-icon.severity-high {
  background: #fef2f2;
  color: #dc2626;
}

.admin-action-icon.severity-warn {
  background: #fff7ed;
  color: #ea580c;
}

.admin-action-main {
  min-width: 0;
  display: grid;
  gap: 3px;
}

.admin-action-main strong,
.admin-action-main small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-action-main strong {
  color: #111827;
}

.admin-action-main small {
  color: #64748b;
}

.admin-action-side {
  display: grid;
  justify-items: end;
}

.mp-stat-card-action {
  cursor: pointer;
}

.mp-stat-card-action:hover {
  border-color: #fed7aa;
  box-shadow: 0 10px 24px rgba(234, 88, 12, 0.12);
}

.approval-sla-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 14px 0;
  padding: 16px;
  border: 1px solid #ddd6fe;
  border-radius: 8px;
  background: #faf8ff;
}

.approval-sla-main {
  display: grid;
  gap: 8px;
}

.approval-sla-main p {
  margin: 0;
  color: #475569;
  line-height: 1.6;
}

.approval-sla-main strong {
  color: #7c3aed;
}

.approval-sla-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.approval-sla-stats span {
  padding: 4px 8px;
  border-radius: 6px;
  background: #ffffff;
  color: #475569;
  font-size: 12px;
}

.approval-sla-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
  flex: 0 0 auto;
}

.ops-capacity-panel {
  display: grid;
  gap: 12px;
  margin: 14px 0;
  padding: 16px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #f8fbff;
}

.ops-capacity-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.ops-capacity-head p {
  margin: 6px 0 0;
  color: #475569;
}

.ops-capacity-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.ops-capacity-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.ops-card {
  min-height: 92px;
  min-width: 0;
  display: grid;
  align-content: center;
  gap: 6px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.ops-card span {
  color: #64748b;
  font-size: 12px;
}

.ops-card strong {
  color: #111827;
  font-size: 20px;
}

.ops-card small {
  overflow: hidden;
  color: #64748b;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ops-card-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.ops-drilldown-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.ops-drilldown-toolbar span {
  color: #64748b;
  font-size: 13px;
}

.ops-drilldown-pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.ops-alert-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 0.9fr);
  gap: 16px;
}

.dashboard-grid.secondary {
  margin-top: 16px;
}

.chart-box {
  width: 100%;
  height: 300px;
}

.chart-box.compact {
  height: 260px;
}

.exam-list {
  display: grid;
  gap: 10px;
}

.approval-list {
  display: grid;
  gap: 10px;
}

.approval-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.approval-row:hover {
  border-color: #ddd6fe;
  background: #fbfaff;
}

.approval-main,
.approval-side {
  display: grid;
  gap: 5px;
}

.approval-main {
  min-width: 0;
}

.approval-main strong {
  overflow: hidden;
  color: #111827;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-main small {
  color: #64748b;
}

.approval-side {
  justify-items: end;
}

.approval-risks {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}

.approval-risks em {
  color: #b91c1c;
  font-size: 12px;
  font-style: normal;
}

.exam-row {
  display: grid;
  grid-template-columns: 12px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
  text-align: left;
  cursor: pointer;
}

.exam-row:hover {
  border-color: #bfdbfe;
  background: #f8fbff;
}

.exam-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #f59e0b;
}

.exam-dot.running {
  background: #16a34a;
}

.exam-dot.ended {
  background: #94a3b8;
}

.exam-main,
.exam-side {
  display: grid;
  gap: 4px;
}

.exam-main {
  min-width: 0;
}

.exam-main strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.exam-main small,
.exam-side small {
  color: #64748b;
}

.exam-side {
  justify-items: end;
}

@media (max-width: 900px) {
  .dashboard-head,
  .approval-sla-panel,
  .ops-capacity-head,
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-head,
  .approval-sla-panel,
  .ops-capacity-head {
    display: grid;
  }

  .ops-capacity-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 560px) {
  .ops-capacity-grid {
    grid-template-columns: 1fr;
  }
}
</style>
