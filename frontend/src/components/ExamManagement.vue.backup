<template>
  <section class="mp-page">
    <div class="mp-toolbar">
      <el-input v-model="query.keyword" placeholder="按考试或试卷名称搜索" clearable style="width: 240px" @keyup.enter="loadExams" />
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 150px" @change="handleFilterChange">
        <el-option label="待审批" :value="0" />
        <el-option label="已发布" :value="1" />
        <el-option label="已关闭" :value="2" />
        <el-option label="已驳回" :value="3" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="loadExams">查询</el-button>
      <el-button type="info" plain :icon="Warning" @click="openLifecycleHealth">Lifecycle Health</el-button>
      <el-button type="warning" plain :icon="Check" @click="openScoreReleaseSafety">Score Safety</el-button>
      <span class="mp-toolbar-spacer"></span>
      <el-button type="success" :icon="Plus" @click="openCreate">{{ createButtonText }}</el-button>
    </div>

    <el-alert
      v-if="lastExamOperationAudit"
      class="exam-operation-audit"
      type="success"
      :closable="true"
      show-icon
      @close="lastExamOperationAudit = null"
    >
      <template #title>
        <div class="exam-operation-audit-content">
          <span>{{ lastExamOperationAudit.action }} audit recorded: {{ examOperationAuditText(lastExamOperationAudit.operationLogIds) }}</span>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestExamOperationAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestExamOperationAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

    <el-alert
      v-if="lastScoreReleaseAudit"
      class="score-release-audit"
      type="success"
      :closable="true"
      show-icon
      @close="lastScoreReleaseAudit = null"
    >
      <template #title>
        <div class="score-release-audit-content">
          <span>{{ lastScoreReleaseAudit.action }} audit recorded: {{ scoreReleaseAuditText(lastScoreReleaseAudit.scoreReleaseLogIds) }}</span>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestScoreReleaseAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestScoreReleaseAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

    <div class="mp-table-card">
      <div v-if="selectedExams.length > 0" class="mp-batch-bar">
        已选择 <span class="mp-batch-count">{{ selectedExams.length }}</span> 场
        <span class="mp-batch-bar-spacer"></span>
        <el-button size="small" type="danger" plain @click="batchDelete">批量删除</el-button>
        <el-button size="small" text @click="clearSelection">取消选择</el-button>
      </div>

      <el-table ref="tableRef" v-loading="loading" :data="exams" :row-class-name="examRowClassName" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" :selectable="canDeleteExam" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="examName" label="考试名称" min-width="180" />
        <el-table-column prop="subjectName" label="科目" width="120" />
        <el-table-column prop="paperName" label="试卷" min-width="150" />
        <el-table-column label="开始时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.endTime) }}</template>
        </el-table-column>
        <el-table-column label="时长" width="90">
          <template #default="scope">{{ scope.row.durationMinutes }} 分钟</template>
        </el-table-column>
        <el-table-column label="及格线" width="90">
          <template #default="scope">{{ scoreText(scope.row.passScore) }}</template>
        </el-table-column>
        <el-table-column label="次数" width="80">
          <template #default="scope">{{ scope.row.maxAttempts || 1 }} 次</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="phaseType(scope.row as ExamInfo)">{{ phaseText(scope.row as ExamInfo) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Health" width="150">
          <template #default="scope">
            <el-tooltip :content="(scope.row as ExamInfo).lifecycleNextAction || lifecycleStateText((scope.row as ExamInfo).lifecycleState)" placement="top">
              <el-tag :type="lifecycleSeverityType((scope.row as ExamInfo).lifecycleSeverity)">
                {{ lifecycleStateText((scope.row as ExamInfo).lifecycleState) }}
              </el-tag>
            </el-tooltip>
          </template>
        </el-table-column>
        <el-table-column label="快照" width="120">
          <template #default="scope">
            <div class="exam-snapshot-cell">
              <span>{{ scope.row.candidateSnapshotCount || 0 }} 人</span>
              <span>{{ scope.row.questionSnapshotCount || 0 }} 题</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="成绩发布" width="110">
          <template #default="scope">
            <el-tooltip
              v-if="scoreReleaseDetail(scope.row as ExamInfo)"
              :content="scoreReleaseDetail(scope.row as ExamInfo)"
              placement="top"
            >
              <el-tag :type="scoreReleaseType(scope.row as ExamInfo)">{{ scoreReleaseText(scope.row as ExamInfo) }}</el-tag>
            </el-tooltip>
            <el-tag v-else :type="scoreReleaseType(scope.row as ExamInfo)">{{ scoreReleaseText(scope.row as ExamInfo) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="520" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :disabled="!canEditExam(scope.row as ExamInfo)" @click="openEdit(scope.row as ExamInfo)">编辑</el-button>
            <el-button v-if="canApproveExam(scope.row as ExamInfo)" link type="success" @click="approve(scope.row as ExamInfo)">批准</el-button>
            <el-button v-if="canRejectExam(scope.row as ExamInfo)" link type="warning" @click="reject(scope.row as ExamInfo)">驳回</el-button>
            <el-button link type="info" @click="openApprovalLogs(scope.row as ExamInfo)">审批记录</el-button>
            <el-button link type="info" @click="openScoreReleaseLogs(scope.row as ExamInfo)">成绩记录</el-button>
            <el-button link type="primary" @click="openLifecycle(scope.row as ExamInfo)">Timeline</el-button>
            <el-tooltip :content="exportScoresDisabledReason(scope.row as ExamInfo)" placement="top" :disabled="canExportScores(scope.row as ExamInfo)">
              <span>
                <el-button
                  link
                  type="success"
                  :disabled="!canExportScores(scope.row as ExamInfo)"
                  @click="exportScores(scope.row as ExamInfo)"
                >
                  成绩单
                </el-button>
              </span>
            </el-tooltip>
            <el-button link type="primary" @click="openSnapshot(scope.row as ExamInfo)">快照</el-button>
            <el-button
              link
              type="info"
              :loading="scoreReleaseReadinessLoading === (scope.row as ExamInfo).id"
              @click="openScoreReleaseReadiness(scope.row as ExamInfo)"
            >
              Readiness
            </el-button>
            <el-button
              v-if="(scope.row as ExamInfo).scoreReleaseStatus === 1"
              link
              type="warning"
              @click="revokeScores(scope.row as ExamInfo)"
            >
              撤回成绩
            </el-button>
            <el-tooltip v-else :content="publishScoresDisabledReason(scope.row as ExamInfo)" placement="top" :disabled="canPublishScores(scope.row as ExamInfo)">
              <span>
                <el-button
                  link
                  type="success"
                  :loading="scoreReleaseReadinessLoading === (scope.row as ExamInfo).id"
                  :disabled="!canPublishScores(scope.row as ExamInfo)"
                  @click="publishScores(scope.row as ExamInfo)"
                >
                  发布成绩
                </el-button>
              </span>
            </el-tooltip>
            <el-button
              v-if="canResolveScoreReleaseBlockers(scope.row as ExamInfo)"
              link
              type="warning"
              @click="openScoreReleaseResolution(scope.row as ExamInfo)"
            >
              Resolve
            </el-button>
            <el-button
              link
              type="warning"
              :disabled="!canCloseExam(scope.row as ExamInfo)"
              @click="close(scope.row as ExamInfo)"
            >
              提前结束
            </el-button>
            <el-button link type="danger" :disabled="!canDeleteExam(scope.row as ExamInfo)" @click="remove(scope.row as ExamInfo)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && exams.length === 0" description="还没有考试任务，点击右上角「发布考试」创建" />
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="totalExams"
        layout="total, sizes, prev, pager, next, jumper"
        class="mp-pager"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>

    <el-drawer v-model="scoreReleaseReadinessVisible" title="Score Release Readiness" size="min(840px, 94vw)">
      <template v-if="scoreReleaseReadiness">
        <div class="readiness-head">
          <div>
            <strong>{{ scoreReleaseReadiness.examName || scoreReleaseReadinessExam?.examName || '-' }}</strong>
            <span>Exam #{{ scoreReleaseReadiness.examId || scoreReleaseReadinessExam?.id }}</span>
          </div>
          <el-tag :type="isScoreReleaseReady(scoreReleaseReadiness) ? 'success' : 'warning'">
            {{ isScoreReleaseReady(scoreReleaseReadiness) ? 'Ready' : 'Blocked' }}
          </el-tag>
        </div>

        <div class="readiness-stats">
          <div v-for="item in scoreReleaseReadinessMetricRows" :key="item.key" class="readiness-stat">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <el-alert
          v-if="isScoreReleaseReady(scoreReleaseReadiness)"
          type="success"
          :closable="false"
          show-icon
          title="Scores can be published after teacher confirmation."
          class="readiness-alert"
        />
        <el-alert
          v-else
          type="warning"
          :closable="false"
          show-icon
          title="Resolve all blockers before publishing scores."
          class="readiness-alert"
        />

        <el-table :data="scoreReleaseReadinessDetailRows" border>
          <el-table-column prop="code" label="Blocker" width="180" />
          <el-table-column label="Count" width="90">
            <template #default="scope">{{ readinessCountText(scope.row.count) }}</template>
          </el-table-column>
          <el-table-column label="Detail" min-width="260">
            <template #default="scope">
              <div class="readiness-blocker-detail">
                <strong>{{ scope.row.message || scoreReleaseBlockerText(scope.row.code) }}</strong>
                <span>{{ scope.row.action || scoreReleaseBlockerActionText(scope.row.code) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="Action" width="180" fixed="right">
            <template #default="scope">
              <el-button
                v-if="canResolveScoreReleaseBlocker(scope.row.code)"
                link
                type="primary"
                @click="openScoreReleaseBlockerResolution(scope.row)"
              >
                Resolve
              </el-button>
              <el-button
                v-else-if="canFinalizeScoreReleaseBlocker(scope.row.code)"
                link
                type="danger"
                :loading="activeAttemptsFinalizing === Number(scoreReleaseReadinessExam?.id || scoreReleaseReadiness?.examId || 0)"
                @click="finalizeActiveAttemptsFromReadiness"
              >
                Finalize
              </el-button>
              <el-button
                v-else-if="canRecalculateScoreReleaseBlocker(scope.row.code)"
                link
                type="warning"
                :loading="missingScoreRecalculating === Number(scoreReleaseReadinessExam?.id || scoreReleaseReadiness?.examId || 0)"
                @click="recalculateMissingScoresFromReadiness"
              >
                Recalculate
              </el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="scoreReleaseReadinessDetailRows.length === 0" description="No blockers" :image-size="90" />
      </template>
      <el-empty v-else-if="!scoreReleaseReadinessLoading" description="No readiness data" :image-size="90" />
    </el-drawer>

    <el-drawer
      v-model="lifecycleHealthVisible"
      title="Lifecycle Health"
      size="min(1180px, 98vw)"
      class="score-safety-drawer"
    >
      <div class="score-safety-toolbar">
        <el-input
          v-model="lifecycleHealthQuery.keyword"
          placeholder="Search exam or paper"
          clearable
          :prefix-icon="Search"
          @keyup.enter="reloadLifecycleHealth"
          @clear="reloadLifecycleHealth"
        />
        <el-select v-model="lifecycleHealthQuery.state" @change="handleLifecycleHealthStateChange">
          <el-option label="All" value="ALL" />
          <el-option label="Action Required" value="ACTION_REQUIRED" />
          <el-option label="Approval" value="APPROVAL" />
          <el-option label="Waiting" value="WAITING" />
          <el-option label="Running" value="RUNNING" />
          <el-option label="Review" value="REVIEW" />
          <el-option label="Score Ready" value="SCORE_READY" />
          <el-option label="Released" value="RELEASED" />
          <el-option label="Risk" value="RISK" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="reloadLifecycleHealth">Search</el-button>
        <span class="mp-toolbar-spacer"></span>
        <el-button type="success" plain :loading="lifecycleHealthExporting" @click="exportLifecycleHealthRows">
          Export
        </el-button>
        <el-button plain :loading="lifecycleHandoffLoading" @click="openLifecycleHandoff">
          Handoff
        </el-button>
        <el-button
          plain
          :disabled="lifecycleHealthReviewRows.length === 0"
          @click="openLifecycleHealthReviewQueue"
        >
          Review Queue
        </el-button>
        <el-button
          plain
          :disabled="lifecycleHealthRecheckRows.length === 0"
          @click="openLifecycleHealthRecheckQueue"
        >
          Recheck Queue
        </el-button>
        <el-button
          plain
          :disabled="lifecycleHealthAppealRows.length === 0"
          @click="openLifecycleHealthAppealQueue"
        >
          Appeals
        </el-button>
        <el-button
          type="warning"
          plain
          :disabled="lifecycleHealthPublishableRows.length === 0"
          :loading="lifecycleHealthPublishing"
          @click="publishReadyLifecycleRows"
        >
          Publish Ready
        </el-button>
      </div>

      <div class="score-safety-stats">
        <div v-for="item in lifecycleHealthStatRows" :key="item.key" class="score-safety-stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>

      <div v-if="lifecycleHealthBlockerRows.length > 0" class="score-safety-blockers">
        <span>Top blockers</span>
        <el-tooltip
          v-for="item in lifecycleHealthBlockerRows"
          :key="item.code"
          :content="lifecycleBlockerText(item.code)"
          placement="top"
        >
          <el-tag type="warning" effect="plain">{{ item.code }} / {{ item.count }}</el-tag>
        </el-tooltip>
      </div>

      <el-table v-loading="lifecycleHealthLoading" :data="lifecycleHealthRows" border>
        <el-table-column label="Exam" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <div class="score-safety-exam-cell">
              <strong>{{ scope.row.examName || '-' }}</strong>
              <span>#{{ scope.row.id || scope.row.examId }} / {{ scope.row.paperName || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="subjectName" label="Subject" width="130" show-overflow-tooltip />
        <el-table-column label="State" width="145">
          <template #default="scope">
            <el-tag :type="lifecycleSeverityType(scope.row.lifecycleSeverity)">
              {{ lifecycleStateText(scope.row.lifecycleState) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Attempts" width="155">
          <template #default="scope">
            <div class="score-safety-counts">
              <strong>{{ readinessNumberText(scope.row.completedAttemptCount) }} / {{ readinessNumberText(scope.row.attemptCount) }}</strong>
              <span>active {{ readinessNumberText(scope.row.activeAttemptCount) }} / review {{ readinessNumberText(scope.row.pendingReviewAttemptCount) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Monitor" width="150">
          <template #default="scope">
            <div class="score-safety-counts">
              <strong>{{ readinessNumberText(scope.row.monitorSessionCount) }} sessions</strong>
              <span>offline {{ readinessNumberText(scope.row.offlineMonitorCount) }} / risk {{ readinessNumberText(scope.row.highRiskMonitorCount) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Blockers" min-width="230">
          <template #default="scope">
            <div v-if="lifecycleBlockers(scope.row).length > 0" class="score-safety-blocker-list">
              <el-tooltip
                v-for="blocker in lifecycleBlockers(scope.row).slice(0, 3)"
                :key="blocker"
                :content="lifecycleBlockerText(blocker)"
                placement="top"
              >
                <el-tag type="warning" effect="plain">{{ blocker }}</el-tag>
              </el-tooltip>
              <span v-if="lifecycleBlockers(scope.row).length > 3" class="score-safety-more">
                +{{ lifecycleBlockers(scope.row).length - 3 }}
              </span>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="lifecycleNextAction" label="Next Action" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.lifecycleNextAction || '-' }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="430" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openLifecycleHealthNextAction(scope.row as ExamLifecycleHealthExam)">
              Next
            </el-button>
            <el-button
              v-if="canOpenLifecycleReview(scope.row as ExamLifecycleHealthExam)"
              link
              type="primary"
              @click="openLifecycleReviewQueue(scope.row as ExamLifecycleHealthExam)"
            >
              Review
            </el-button>
            <el-button
              v-if="canOpenLifecycleRecheck(scope.row as ExamLifecycleHealthExam)"
              link
              type="warning"
              @click="openLifecycleRecheckQueue(scope.row as ExamLifecycleHealthExam)"
            >
              Recheck
            </el-button>
            <el-button
              v-if="canOpenLifecycleAppeals(scope.row as ExamLifecycleHealthExam)"
              link
              type="warning"
              @click="openLifecycleAppealQueue(scope.row as ExamLifecycleHealthExam)"
            >
              Appeals
            </el-button>
            <el-button
              v-if="canRepairLifecycleSnapshot(scope.row as ExamLifecycleHealthExam)"
              link
              type="warning"
              :loading="snapshotRepairing"
              @click="repairLifecycleSnapshot(scope.row as ExamLifecycleHealthExam)"
            >
              Repair
            </el-button>
            <el-button
              v-if="canRecalculateMissingScores(scope.row as ExamInfo)"
              link
              type="warning"
              :loading="missingScoreRecalculating === Number(scope.row.id || scope.row.examId || 0)"
              @click="recalculateMissingScoresFromLifecycle(scope.row as ExamLifecycleHealthExam)"
            >
              Recalculate
            </el-button>
            <el-button
              v-if="canPublishScores(scope.row as ExamInfo)"
              link
              type="success"
              :loading="scoreReleaseReadinessLoading === (scope.row.id || scope.row.examId)"
              @click="publishScoresFromLifecycleHealth(scope.row as ExamLifecycleHealthExam)"
            >
              Publish
            </el-button>
            <el-button
              v-if="canFinalizeLifecycleRow(scope.row as ExamLifecycleHealthExam)"
              link
              type="danger"
              :loading="activeAttemptsFinalizing === Number(scope.row.id || scope.row.examId || 0)"
              @click="finalizeActiveAttemptsFromLifecycle(scope.row as ExamLifecycleHealthExam)"
            >
              Finalize
            </el-button>
            <el-button link type="info" @click="openLifecycle(scope.row as ExamInfo)">Timeline</el-button>
            <el-button link type="warning" @click="openScoreReleaseReadiness(scope.row as ExamInfo)">Readiness</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!lifecycleHealthLoading && lifecycleHealthRows.length === 0"
        description="No lifecycle health records"
        :image-size="90"
      />
      <el-pagination
        v-model:current-page="lifecycleHealthQuery.page"
        v-model:page-size="lifecycleHealthQuery.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="lifecycleHealthTotal"
        layout="total, sizes, prev, pager, next, jumper"
        class="mp-pager"
        @current-change="handleLifecycleHealthPageChange"
        @size-change="handleLifecycleHealthSizeChange"
      />
    </el-drawer>

    <el-drawer
      v-model="lifecycleHandoffVisible"
      title="Lifecycle Handoff"
      size="min(920px, 96vw)"
      class="score-safety-drawer"
    >
      <template v-if="lifecycleHandoff">
        <div class="approval-log-head">
          <strong>{{ lifecycleHandoffTitle }}</strong>
          <el-tag type="warning">Filtered {{ readinessNumberText(lifecycleHandoff.filteredTotal) }}</el-tag>
        </div>
        <div class="score-release-log-toolbar">
          <el-button type="primary" plain :icon="DocumentCopy" :loading="lifecycleHandoffCopying" @click="copyLifecycleHandoff">
            Copy
          </el-button>
          <el-button type="success" plain :loading="lifecycleHandoffNotifyTarget === 'SELF'" @click="notifyLifecycleHandoff('SELF')">
            Notify Me
          </el-button>
          <el-button type="warning" plain :loading="lifecycleHandoffNotifyTarget === 'OPERATORS'" @click="notifyLifecycleHandoff('OPERATORS')">
            Notify Operators
          </el-button>
          <el-button plain @click="loadLifecycleHandoff">Refresh</el-button>
        </div>

        <div class="score-safety-stats">
          <div v-for="item in lifecycleHandoffMetricRows" :key="item.key" class="score-safety-stat">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <div v-if="lifecycleHandoff.topBlockers.length > 0" class="score-safety-blockers">
          <span>Top blockers</span>
          <el-tag v-for="item in lifecycleHandoff.topBlockers" :key="item.code" type="warning" effect="plain">
            {{ item.code }} / {{ item.count }}
          </el-tag>
        </div>

        <el-input
          type="textarea"
          :model-value="lifecycleHandoffText"
          :rows="10"
          readonly
          resize="vertical"
        />

        <el-table :data="lifecycleHandoff.actionRows" border class="mp-table-gap">
          <el-table-column label="Exam" min-width="220" show-overflow-tooltip>
            <template #default="scope">
              <div class="score-safety-exam-cell">
                <strong>{{ scope.row.examName || '-' }}</strong>
                <span>#{{ scope.row.examId }} / {{ scope.row.paperName || '-' }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="State" width="145">
            <template #default="scope">
              <el-tag :type="lifecycleSeverityType(scope.row.lifecycleSeverity)">
                {{ lifecycleStateText(scope.row.lifecycleState) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="lifecycleNextAction" label="Next Action" min-width="220" show-overflow-tooltip />
          <el-table-column prop="lifecycleBlockerCodes" label="Blockers" min-width="190" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.lifecycleBlockerCodes || '-' }}</template>
          </el-table-column>
          <el-table-column label="Open" width="90" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="openLifecycleHandoffTarget(scope.row as ExamLifecycleHandoffAction)">
                Open
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else-if="!lifecycleHandoffLoading" description="No lifecycle handoff data" :image-size="90" />
    </el-drawer>

    <el-drawer
      v-model="scoreReleaseSafetyVisible"
      title="Score Release Safety"
      size="min(1120px, 98vw)"
      class="score-safety-drawer"
    >
      <div class="score-safety-toolbar">
        <el-input
          v-model="scoreReleaseSafetyQuery.keyword"
          placeholder="Search exam or paper"
          clearable
          :prefix-icon="Search"
          @keyup.enter="reloadScoreReleaseSafety"
          @clear="reloadScoreReleaseSafety"
        />
        <el-select v-model="scoreReleaseSafetyQuery.state" @change="handleScoreReleaseSafetyStateChange">
          <el-option label="All" value="ALL" />
          <el-option label="Action Required" value="ACTION_REQUIRED" />
          <el-option label="Ready" value="READY" />
          <el-option label="Blocked" value="BLOCKED" />
          <el-option label="Released" value="RELEASED" />
          <el-option label="Revoked" value="REVOKED" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="reloadScoreReleaseSafety">Search</el-button>
        <span class="mp-toolbar-spacer"></span>
        <el-button type="success" plain :loading="scoreReleaseSafetyExporting" @click="exportScoreReleaseSafetyRows">
          Export
        </el-button>
      </div>

      <div class="score-safety-stats">
        <div v-for="item in scoreReleaseSafetyStatRows" :key="item.key" class="score-safety-stat">
          <span>{{ item.label }}</span>
          <strong>{{ item.value }}</strong>
        </div>
      </div>

      <div v-if="scoreReleaseSafetyBlockerRows.length > 0" class="score-safety-blockers">
        <span>Top blockers</span>
        <el-tooltip
          v-for="item in scoreReleaseSafetyBlockerRows"
          :key="item.code"
          :content="scoreReleaseBlockerText(item.code)"
          placement="top"
        >
          <el-tag type="warning" effect="plain">{{ item.code }} · {{ item.count }}</el-tag>
        </el-tooltip>
      </div>

      <el-table v-loading="scoreReleaseSafetyLoading" :data="scoreReleaseSafetyRows" border>
        <el-table-column label="Exam" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <div class="score-safety-exam-cell">
              <strong>{{ scope.row.examName || '-' }}</strong>
              <span>#{{ scope.row.id || scope.row.examId }} · {{ scope.row.paperName || '-' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="subjectName" label="Subject" width="130" show-overflow-tooltip />
        <el-table-column label="State" width="120">
          <template #default="scope">
            <el-tag :type="scoreReleaseSafetyStateType(scope.row.scoreSafetyState)">
              {{ scoreReleaseSafetyStateText(scope.row.scoreSafetyState) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Release" width="110">
          <template #default="scope">
            <el-tag :type="scoreReleaseType(scope.row as ExamInfo)">{{ scoreReleaseText(scope.row as ExamInfo) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Attempts" width="150">
          <template #default="scope">
            <div class="score-safety-counts">
              <strong>{{ readinessNumberText(scope.row.completedAttemptCount) }} / {{ readinessNumberText(scope.row.attemptCount) }}</strong>
              <span>scored {{ readinessNumberText(scope.row.scoredCompletedAttemptCount) }} · missing {{ readinessNumberText(scope.row.unscoredCompletedAttemptCount) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Blockers" min-width="230">
          <template #default="scope">
            <div v-if="scoreReleaseBlockers(scope.row as ExamInfo).length > 0" class="score-safety-blocker-list">
              <el-tooltip
                v-for="blocker in scoreReleaseBlockers(scope.row as ExamInfo).slice(0, 3)"
                :key="blocker"
                :content="scoreReleaseBlockerText(blocker)"
                placement="top"
              >
                <el-tag type="warning" effect="plain">{{ blocker }}</el-tag>
              </el-tooltip>
              <span v-if="scoreReleaseBlockers(scope.row as ExamInfo).length > 3" class="score-safety-more">
                +{{ scoreReleaseBlockers(scope.row as ExamInfo).length - 3 }}
              </span>
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="nextAction" label="Next Action" min-width="200" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.nextAction || '-' }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="370" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="openScoreReleaseReadiness(scope.row as ScoreReleaseSafetyExam)">
              Readiness
            </el-button>
            <el-button
              link
              type="success"
              :disabled="!canPublishScores(scope.row as ExamInfo)"
              :loading="scoreReleaseReadinessLoading === (scope.row.id || scope.row.examId)"
              @click="publishScoresFromSafety(scope.row as ScoreReleaseSafetyExam)"
            >
              Publish
            </el-button>
            <el-button
              v-if="canResolveScoreReleaseBlockers(scope.row as ExamInfo)"
              link
              type="warning"
              @click="openScoreReleaseResolution(scope.row as ExamInfo)"
            >
              Resolve
            </el-button>
            <el-button
              v-if="canFinalizeActiveAttempts(scope.row as ExamInfo)"
              link
              type="danger"
              :loading="activeAttemptsFinalizing === Number(scope.row.id || scope.row.examId || 0)"
              @click="finalizeActiveAttemptsFromSafety(scope.row as ScoreReleaseSafetyExam)"
            >
              Finalize
            </el-button>
            <el-button
              v-if="canRecalculateMissingScores(scope.row as ExamInfo)"
              link
              type="warning"
              :loading="missingScoreRecalculating === Number(scope.row.id || scope.row.examId || 0)"
              @click="recalculateMissingScoresFromSafety(scope.row as ScoreReleaseSafetyExam)"
            >
              Recalculate
            </el-button>
            <el-button link type="info" @click="openLifecycle(scope.row as ExamInfo)">Timeline</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-if="!scoreReleaseSafetyLoading && scoreReleaseSafetyRows.length === 0"
        description="No score release safety records"
        :image-size="90"
      />
      <el-pagination
        v-model:current-page="scoreReleaseSafetyQuery.page"
        v-model:page-size="scoreReleaseSafetyQuery.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="scoreReleaseSafetyTotal"
        layout="total, sizes, prev, pager, next, jumper"
        class="mp-pager"
        @current-change="handleScoreReleaseSafetyPageChange"
        @size-change="handleScoreReleaseSafetySizeChange"
      />
    </el-drawer>

    <el-drawer v-model="snapshotVisible" title="考试快照" size="min(980px, 96vw)" class="exam-snapshot-drawer">
      <template v-if="snapshot">
        <div class="snapshot-summary">
          <div>
            <span>考生</span>
            <strong>{{ snapshot.candidateCount }}</strong>
          </div>
          <div>
            <span>题目</span>
            <strong>{{ snapshot.questionCount }}</strong>
          </div>
          <div>
            <span>总分</span>
            <strong>{{ snapshot.totalScore }}</strong>
          </div>
          <div>
            <span>试卷</span>
            <strong>{{ snapshot.exam.paperName || '-' }}</strong>
          </div>
        </div>

        <div class="snapshot-toolbar">
          <span>{{ snapshotActiveCandidates.length }} active attempt(s)</span>
          <el-button
            type="success"
            plain
            :loading="snapshotExporting"
            @click="exportSnapshotEvidence"
          >
            Export Evidence
          </el-button>
          <el-button plain @click="openSnapshotExportAudit">
            Audit Logs
          </el-button>
          <el-button
            type="warning"
            plain
            :loading="snapshotRepairing"
            @click="repairCurrentSnapshot"
          >
            Repair Snapshot
          </el-button>
          <el-button
            type="danger"
            plain
            :disabled="snapshotActiveCandidates.length === 0"
            :loading="snapshotForceSubmitting"
            @click="forceSubmitActiveSnapshotCandidates"
          >
            Force Submit Active
          </el-button>
        </div>

        <el-tabs model-value="candidates">
          <el-tab-pane label="考生快照" name="candidates">
            <el-table v-loading="snapshotLoading" :data="snapshot.candidates" border height="420">
              <el-table-column prop="realName" label="姓名" min-width="120" />
              <el-table-column prop="studentNo" label="学号" min-width="120" />
              <el-table-column prop="className" label="班级" min-width="160" />
              <el-table-column label="来源" width="130">
                <template #default="scope">{{ sourceText(scope.row.sourceType) }}</template>
              </el-table-column>
              <el-table-column label="答卷状态" width="120">
                <template #default="scope">{{ attemptStatusText(scope.row.latestAttemptStatus) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="scope">
                  <el-button
                    link
                    type="danger"
                    :disabled="!scope.row.activeAttemptId"
                    @click="forceSubmitCandidate(scope.row.activeAttemptId)"
                  >
                    强制交卷
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="试卷快照" name="questions">
            <el-table v-loading="snapshotLoading" :data="snapshot.questions" border height="420">
              <el-table-column prop="sortOrder" label="#" width="70" />
              <el-table-column label="题型" width="110">
                <template #default="scope">{{ questionTypeText(scope.row.questionType) }}</template>
              </el-table-column>
              <el-table-column prop="score" label="分值" width="90" />
              <el-table-column prop="stem" label="题干" min-width="280" show-overflow-tooltip />
              <el-table-column prop="correctAnswer" label="参考答案" min-width="180" show-overflow-tooltip />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </template>
      <el-empty v-else-if="!snapshotLoading" description="暂无快照数据" />
    </el-drawer>

    <el-drawer v-model="approvalLogVisible" title="审批记录" size="min(720px, 94vw)">
      <template v-if="approvalExam">
        <div class="approval-log-head">
          <strong>{{ approvalExam.examName }}</strong>
          <el-tag :type="phaseType(approvalExam)">{{ phaseText(approvalExam) }}</el-tag>
        </div>
        <div class="approval-log-toolbar">
          <el-button type="success" plain :loading="approvalLogExporting" @click="exportApprovalLogRows">Export</el-button>
        </div>
        <el-table v-loading="approvalLogLoading" :data="approvalLogs" border>
          <el-table-column prop="id" label="Log ID" width="90" />
          <el-table-column label="动作" width="110">
            <template #default="scope">{{ approvalActionText(scope.row.action) }}</template>
          </el-table-column>
          <el-table-column label="状态流转" width="150">
            <template #default="scope">
              {{ approvalStatusText(scope.row.statusFrom) }} -> {{ approvalStatusText(scope.row.statusTo) }}
            </template>
          </el-table-column>
          <el-table-column prop="actorName" label="处理人" width="130" />
          <el-table-column label="时间" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
          <el-table-column label="发布触达" width="220">
            <template #default="scope">{{ approvalPublishStatsText(scope.row as ExamApprovalLog) }}</template>
          </el-table-column>
          <el-table-column prop="note" label="备注/原因" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.note || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="props.role === 'ADMIN'" label="Audit" width="150" fixed="right">
            <template #default="scope">
              <el-button link type="primary" :icon="Search" @click="openApprovalAudit(scope.row.id)">Audit</el-button>
              <el-button link type="primary" :icon="DocumentCopy" @click="copyApprovalAuditLink(scope.row.id)">Copy</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!approvalLogLoading && approvalLogs.length === 0" description="暂无审批记录" />
      </template>
    </el-drawer>

    <el-drawer v-model="scoreReleaseLogVisible" title="成绩发布记录" size="min(820px, 94vw)">
      <template v-if="scoreReleaseExam">
        <div class="approval-log-head">
          <strong>{{ scoreReleaseExam.examName }}</strong>
          <el-tag :type="scoreReleaseType(scoreReleaseExam)">{{ scoreReleaseText(scoreReleaseExam) }}</el-tag>
        </div>
        <div class="score-release-log-toolbar">
          <el-button type="success" plain :loading="scoreReleaseLogExporting" @click="exportScoreReleaseLogRows">Export</el-button>
        </div>
        <el-table v-loading="scoreReleaseLogLoading" :data="scoreReleaseLogs" border>
          <el-table-column prop="id" label="日志ID" width="92" />
          <el-table-column label="动作" width="110">
            <template #default="scope">{{ scoreReleaseActionText(scope.row.action) }}</template>
          </el-table-column>
          <el-table-column label="状态流转" width="150">
            <template #default="scope">
              {{ scoreReleaseStatusText(scope.row.statusFrom) }} -> {{ scoreReleaseStatusText(scope.row.statusTo) }}
            </template>
          </el-table-column>
          <el-table-column prop="actorName" label="处理人" width="130" />
          <el-table-column label="影响答卷" width="110">
            <template #default="scope">{{ scope.row.visibleAttemptCount || 0 }}</template>
          </el-table-column>
          <el-table-column label="通知" width="130">
            <template #default="scope">{{ scope.row.notifiedStudentCount || 0 }} 人 / {{ scope.row.notifiedAttemptCount || 0 }} 份</template>
          </el-table-column>
          <el-table-column label="时间" width="170">
            <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
          </el-table-column>
          <el-table-column prop="note" label="说明/原因" min-width="220" show-overflow-tooltip>
            <template #default="scope">{{ scope.row.note || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="props.role === 'ADMIN'" label="审计" width="150" fixed="right">
            <template #default="scope">
              <el-button link type="primary" :icon="Search" @click="openScoreReleaseAudit(scope.row.id)">审计</el-button>
              <el-button link type="primary" :icon="DocumentCopy" @click="copyScoreReleaseAuditLink(scope.row.id)">复制</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!scoreReleaseLogLoading && scoreReleaseLogs.length === 0" description="暂无成绩发布记录" />
      </template>
    </el-drawer>

    <el-drawer v-model="lifecycleVisible" title="Exam Lifecycle" size="min(900px, 96vw)" class="exam-lifecycle-drawer">
      <template v-if="lifecycleExam">
        <div class="lifecycle-head">
          <div>
            <strong>{{ lifecycleExam.examName }}</strong>
            <span>Exam #{{ lifecycleExam.id }} · {{ lifecycle?.exam.paperName || lifecycleExam.paperName || '-' }}</span>
          </div>
          <el-tag :type="phaseType(lifecycleExam)">{{ phaseText(lifecycleExam) }}</el-tag>
        </div>

        <div v-if="lifecycle" class="lifecycle-stats">
          <div v-for="item in lifecycleMetricRows" :key="item.key" class="lifecycle-stat">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>

        <el-alert
          v-if="lifecycleTruncatedCount > 0"
          type="warning"
          :closable="false"
          show-icon
          :title="`Timeline is truncated: ${lifecycleTruncatedCount} older events are hidden.`"
          class="lifecycle-alert"
        />

        <el-timeline v-loading="lifecycleLoading" class="lifecycle-timeline">
          <el-timeline-item
            v-for="(event, index) in lifecycleTimeline"
            :key="lifecycleEventKey(event, index)"
            :timestamp="formatDateTime(event.createdAt)"
            :type="lifecycleEventType(event)"
          >
            <div class="lifecycle-event">
              <div class="lifecycle-event-title">
                <strong>{{ lifecycleEventLabel(event) }}</strong>
                <el-tag size="small" effect="plain">{{ event.source }}</el-tag>
              </div>
              <div class="lifecycle-event-meta">
                <span v-if="event.actorName">Actor: {{ event.actorName }}</span>
                <span v-if="lifecycleStatusFlow(event)">Status: {{ lifecycleStatusFlow(event) }}</span>
                <span v-if="event.relatedType && event.relatedId">Related: {{ event.relatedType }}#{{ event.relatedId }}</span>
              </div>
              <p v-if="event.note">{{ event.note }}</p>
              <div v-if="lifecycleDetailEntries(event).length > 0" class="lifecycle-event-details">
                <el-tag
                  v-for="detail in lifecycleDetailEntries(event)"
                  :key="detail.label"
                  size="small"
                  effect="plain"
                >
                  {{ detail.label }}: {{ detail.value }}
                </el-tag>
              </div>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="!lifecycleLoading && lifecycleTimeline.length === 0" description="No lifecycle events" />
      </template>
    </el-drawer>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="min(1040px, 94vw)" top="5vh" class="exam-publish-dialog">
      <template v-if="!editingId">
        <el-steps :active="publishStep" finish-status="success" align-center class="publish-steps">
          <el-step title="选择试卷" :icon="Tickets" />
          <el-step title="考试安排" :icon="Timer" />
          <el-step title="参考范围" :icon="User" />
          <el-step title="发布确认" :icon="Check" />
        </el-steps>

        <div v-show="publishStep === 0" class="publish-panel paper-step">
          <div class="paper-picker">
            <el-input v-model="paperKeyword" placeholder="搜索试卷" clearable :prefix-icon="Search" />
            <el-scrollbar height="370px" class="paper-scroll">
              <button
                v-for="paper in filteredPapers"
                :key="paper.id"
                type="button"
                :class="['paper-option', { active: paper.id === form.paperId }]"
                @click="selectPaper(paper)"
              >
                <strong>{{ paper.paperName }}</strong>
                <span>{{ paper.subjectName }} · {{ paper.questionCount }} 题 · {{ paper.totalScore }} 分</span>
              </button>
              <el-empty v-if="filteredPapers.length === 0" description="暂无已发布试卷" :image-size="90" />
            </el-scrollbar>
          </div>
          <aside class="paper-preview">
            <div class="preview-label">当前试卷</div>
            <template v-if="selectedPaper">
              <h3>{{ selectedPaper.paperName }}</h3>
              <div class="preview-stats">
                <span>{{ selectedPaper.subjectName }}</span>
                <span>{{ selectedPaper.questionCount }} 题</span>
                <span>{{ selectedPaper.totalScore }} 分</span>
              </div>
              <p>{{ selectedPaper.description || '暂无试卷说明' }}</p>
            </template>
            <el-empty v-else description="请选择试卷" :image-size="88" />
          </aside>
        </div>

        <el-form v-show="publishStep === 1" :model="form" label-position="top" class="publish-panel">
          <el-form-item label="考试名称" required>
            <el-input v-model="form.examName" placeholder="例如：Java程序设计期中考试" />
          </el-form-item>
          <el-form-item label="考试说明">
            <el-input v-model="form.description" type="textarea" :rows="3" placeholder="选填" />
          </el-form-item>
          <div class="form-row">
            <el-form-item label="开始时间" required>
              <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" style="width: 100%" />
            </el-form-item>
            <el-form-item label="结束时间" required>
              <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" style="width: 100%" />
            </el-form-item>
          </div>
          <div class="form-row three">
            <el-form-item label="考试时长（分钟）" required>
              <el-input-number v-model="form.durationMinutes" :min="1" :max="600" />
            </el-form-item>
            <el-form-item label="次数限制" required>
              <el-input-number v-model="form.maxAttempts" :min="1" :max="20" />
            </el-form-item>
            <el-form-item label="及格线">
              <el-input-number v-model="form.passScore" :min="0" :max="selectedPaper?.totalScore || 1000" :precision="1" placeholder="选填" />
            </el-form-item>
          </div>
        </el-form>

        <div v-show="publishStep === 2" class="publish-panel target-step">
          <el-radio-group v-model="form.targetMode" class="target-mode">
            <el-radio-button label="CLASS_COURSE">课程班</el-radio-button>
            <el-radio-button label="USER">指定学生</el-radio-button>
          </el-radio-group>

          <el-form :model="form" label-position="top">
            <el-form-item v-if="form.targetMode === 'CLASS_COURSE'" label="参考课程班" required>
              <el-select v-model="form.classCourseIds" multiple placeholder="请选择参加考试的课程班" style="width: 100%" filterable>
                <el-option
                  v-for="item in classCourses"
                  :key="item.classCourseId"
                  :label="`${item.className} / ${item.courseName} / ${item.termName}`"
                  :value="item.classCourseId"
                />
              </el-select>
              <div v-if="classCourses.length === 0" class="form-hint">暂无可用课程班，请先完成班级、课程和授课分配。</div>
            </el-form-item>

            <el-form-item v-else label="参考学生" required>
              <el-select v-model="form.studentUserIds" multiple placeholder="请选择参加考试的学生" style="width: 100%" filterable>
                <el-option
                  v-for="student in targetStudents"
                  :key="student.userId"
                  :label="`${student.realName}${student.studentNo ? `（${student.studentNo}）` : ''}${student.className ? ` / ${student.className}` : ''}`"
                  :value="student.userId"
                />
              </el-select>
              <div v-if="targetStudents.length === 0" class="form-hint">暂无可用学生，请检查班级选课或授课范围。</div>
            </el-form-item>
          </el-form>

          <div class="target-summary">
            <span>已选 {{ targetCount }} 个{{ form.targetMode === 'CLASS_COURSE' ? '课程班' : '学生' }}</span>
            <el-tag v-for="label in selectedTargetLabels" :key="label" type="info">{{ label }}</el-tag>
          </div>
        </div>

        <div v-show="publishStep === 3" class="publish-panel confirm-step">
          <section class="confirm-block">
            <h4>试卷</h4>
            <p>{{ selectedPaper?.paperName || '未选择' }}</p>
            <span>{{ selectedPaper?.subjectName || '—' }} · {{ selectedPaper?.questionCount || 0 }} 题 · {{ selectedPaper?.totalScore || 0 }} 分</span>
          </section>
          <section class="confirm-block">
            <h4>安排</h4>
            <p>{{ form.examName || '未填写' }}</p>
            <span>{{ formatDateTime(form.startTime) }} 至 {{ formatDateTime(form.endTime) }} · {{ form.durationMinutes }} 分钟</span>
          </section>
          <section class="confirm-block">
            <h4>规则</h4>
            <p>{{ form.maxAttempts }} 次 · {{ passScoreLabel }}</p>
            <span>{{ form.description || '暂无考试说明' }}</span>
          </section>
          <section class="confirm-block">
            <h4>范围</h4>
            <p>{{ form.targetMode === 'CLASS_COURSE' ? '课程班' : '指定学生' }} · {{ targetCount }} 个目标</p>
            <span>{{ selectedTargetLabels.join('、') || '未选择' }}</span>
          </section>
          <section class="confirm-block preflight-block">
            <h4>发布预检</h4>
            <p>{{ preflight?.candidateCount || 0 }} 名考生 · {{ preflight?.questionCount || 0 }} 道题</p>
            <span>系统将固化考生范围和试卷快照，发布后学生端按该快照进入考试。</span>
            <el-alert
              v-for="warning in preflight?.warnings || []"
              :key="warning"
              type="warning"
              :closable="false"
              show-icon
              class="preflight-warning"
            >
              {{ warning }}
            </el-alert>
          </section>
        </div>
      </template>

      <el-form v-else :model="form" label-position="top" class="edit-panel">
        <el-alert type="info" :closable="false" show-icon class="edit-tip">
          编辑仅调整考试安排与规则，不修改试卷与参考范围；如需更换试卷或班级，请删除后重新发布。
        </el-alert>
        <el-form-item label="试卷">
          <el-input :model-value="editPaperName" disabled />
        </el-form-item>
        <el-form-item label="考试名称" required>
          <el-input v-model="form.examName" />
        </el-form-item>
        <el-form-item label="考试说明">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="开始时间" required>
            <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" required>
            <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" style="width: 100%" />
          </el-form-item>
        </div>
        <div class="form-row three">
          <el-form-item label="考试时长（分钟）" required>
            <el-input-number v-model="form.durationMinutes" :min="1" :max="600" />
          </el-form-item>
          <el-form-item label="次数限制" required>
            <el-input-number v-model="form.maxAttempts" :min="1" :max="20" />
          </el-form-item>
          <el-form-item label="及格线">
            <el-input-number v-model="form.passScore" :min="0" :precision="1" placeholder="选填" />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="!editingId && publishStep > 0" :icon="ArrowLeft" @click="previousPublishStep">上一步</el-button>
        <el-button v-if="!editingId && publishStep < 3" type="primary" :icon="ArrowRight" :loading="preflightLoading" @click="nextPublishStep">下一步</el-button>
        <el-button v-else type="primary" :icon="Check" :loading="submitting" @click="submit">
          {{ editingId ? '保存修改' : (props.role === 'ADMIN' ? '确认发布' : '提交审批') }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft, ArrowRight, Check, DocumentCopy, Plus, Search, Tickets, Timer, User, Warning } from '@element-plus/icons-vue';
import {
  approveExam,
  closeExam,
  createExam,
  deleteExam,
  exportExamApprovalLogs,
  exportExamScores,
  exportExamLifecycleHealth,
  exportExamSnapshot,
  exportScoreReleaseSafety,
  exportScoreReleaseLogs,
  finalizeActiveExamAttempts,
  forceSubmitAttempt,
  getExamApprovalLogs,
  getExamLifecycle,
  getExamLifecycleHealth,
  getExamLifecycleHealthHandoff,
  getExamSnapshot,
  getScoreReleaseReadiness,
  getScoreReleaseSafety,
  getScoreReleaseLogs,
  listExamTargetStudents,
  listTeacherExams,
  notifyExamLifecycleHealthHandoff,
  preflightExam,
  publishExamScores,
  recalculateMissingScores,
  repairExamSnapshot,
  rejectExam,
  revokeExamScores,
  updateExam,
  type ExamInfo,
  type ExamApprovalLog,
  type ExamLifecycle,
  type ExamLifecycleEvent,
  type ExamLifecycleHandoffAudience,
  type ExamLifecycleHandoffAction,
  type ExamLifecycleHealthExam,
  type ExamLifecycleHealthHandoff,
  type ExamLifecycleHealthState,
  type ExamLifecycleHealthSummary,
  type ExamPayload,
  type ExamPreflight,
  type ExamSnapshot,
  type ExamTargetStudentInfo,
  type ScoreReleaseBlockerDetail,
  type ScoreReleaseLog,
  type ScoreReleaseReadiness,
  type ScoreReleaseSafetyExam,
  type ScoreReleaseSafetyState,
  type ScoreReleaseSafetySummary
} from '../api/exam';
import { listPapers, type PaperInfo } from '../api/paper';
import { listClassCourses, type ClassCourseInfo } from '../api/basic';
import { formatDateTime } from '../utils/dateFormat';
import {
  copyExamApprovalAuditLinkToClipboard,
  copyOperationLogIdToClipboard,
  copyOperationLogLinkToClipboard,
  copyScoreReleaseAuditIdToClipboard,
  copyScoreReleaseAuditLinkToClipboard,
  writeClipboardText
} from '../utils/clipboard';
import type { RoleCode } from '../api/auth';

type TargetMode = 'CLASS_COURSE' | 'USER';
type ScoreReleaseResolutionMode = 'RECHECK' | 'APPEALS' | 'REVIEW';

const props = defineProps<{
  role: RoleCode;
}>();

const route = useRoute();
const router = useRouter();
const exams = ref<ExamInfo[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const totalExams = ref(0);
const papers = ref<PaperInfo[]>([]);
const classCourses = ref<ClassCourseInfo[]>([]);
const targetStudents = ref<ExamTargetStudentInfo[]>([]);
const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const snapshotVisible = ref(false);
const snapshotLoading = ref(false);
const snapshotExporting = ref(false);
const snapshotRepairing = ref(false);
const snapshotForceSubmitting = ref(false);
const snapshot = ref<ExamSnapshot | null>(null);
const approvalLogVisible = ref(false);
const approvalLogLoading = ref(false);
const approvalLogExporting = ref(false);
const approvalExam = ref<ExamInfo | null>(null);
const approvalLogs = ref<ExamApprovalLog[]>([]);
const scoreReleaseLogVisible = ref(false);
const scoreReleaseLogLoading = ref(false);
const scoreReleaseLogExporting = ref(false);
const scoreReleaseExam = ref<ExamInfo | null>(null);
const scoreReleaseLogs = ref<ScoreReleaseLog[]>([]);
const lifecycleVisible = ref(false);
const lifecycleLoading = ref(false);
const lifecycleExam = ref<ExamInfo | null>(null);
const lifecycle = ref<ExamLifecycle | null>(null);
const lifecycleHealthVisible = ref(false);
const lifecycleHealthLoading = ref(false);
const lifecycleHealthExporting = ref(false);
const lifecycleHealthPublishing = ref(false);
const lifecycleHealthRows = ref<ExamLifecycleHealthExam[]>([]);
const lifecycleHealthTotal = ref(0);
const lifecycleHandoffVisible = ref(false);
const lifecycleHandoffLoading = ref(false);
const lifecycleHandoffCopying = ref(false);
const lifecycleHandoffNotifyTarget = ref<ExamLifecycleHandoffAudience | ''>('');
const lifecycleHandoff = ref<ExamLifecycleHealthHandoff | null>(null);
const lifecycleHealthQuery = reactive<{
  keyword: string;
  state: ExamLifecycleHealthState;
  page: number;
  size: number;
}>({
  keyword: '',
  state: 'ALL',
  page: 1,
  size: 10
});
const scoreReleaseReadinessVisible = ref(false);
const scoreReleaseReadinessExam = ref<ExamInfo | null>(null);
const scoreReleaseReadiness = ref<ScoreReleaseReadiness | null>(null);
const scoreReleaseReadinessLoading = ref<number | null>(null);
const missingScoreRecalculating = ref<number | null>(null);
const activeAttemptsFinalizing = ref<number | null>(null);
const scoreReleaseSafetyVisible = ref(false);
const scoreReleaseSafetyLoading = ref(false);
const scoreReleaseSafetyExporting = ref(false);
const scoreReleaseSafetyRows = ref<ScoreReleaseSafetyExam[]>([]);
const scoreReleaseSafetyTotal = ref(0);
const scoreReleaseSafetyQuery = reactive<{
  keyword: string;
  state: ScoreReleaseSafetyState;
  page: number;
  size: number;
}>({
  keyword: '',
  state: 'ALL',
  page: 1,
  size: 10
});
const emptyScoreReleaseSafetySummary = (): ScoreReleaseSafetySummary => ({
  total: 0,
  ready: 0,
  blocked: 0,
  released: 0,
  revoked: 0,
  actionRequired: 0,
  blockerCounts: {}
});
const scoreReleaseSafetySummaryData = ref<ScoreReleaseSafetySummary>(emptyScoreReleaseSafetySummary());
const emptyLifecycleHealthSummary = (): ExamLifecycleHealthSummary => ({
  total: 0,
  actionRequired: 0,
  approval: 0,
  waiting: 0,
  running: 0,
  review: 0,
  scoreReady: 0,
  released: 0,
  risk: 0,
  blockerCounts: {}
});
const lifecycleHealthSummaryData = ref<ExamLifecycleHealthSummary>(emptyLifecycleHealthSummary());
const preflightLoading = ref(false);
const preflight = ref<ExamPreflight | null>(null);
const editingId = ref<number | null>(null);
const editPaperName = ref('');
const publishStep = ref(0);
const paperKeyword = ref('');
const query = reactive<{ keyword: string; status: number | null }>({ keyword: '', status: null });
const focusedExamId = computed(() => routeExamId());
const snapshotActiveCandidates = computed(() => (snapshot.value?.candidates || [])
  .filter((item) => Boolean(item.activeAttemptId)));
const scoreReleaseReadinessDetailRows = computed<ScoreReleaseBlockerDetail[]>(() => {
  const readiness = scoreReleaseReadiness.value;
  if (!readiness) return [];
  if (readiness.blockerDetails && readiness.blockerDetails.length > 0) {
    return readiness.blockerDetails;
  }
  const blockers = readiness.blockers && readiness.blockers.length > 0
    ? readiness.blockers
    : String(readiness.scoreReleaseBlockers || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  return blockers.map((code) => ({
    code,
    message: scoreReleaseBlockerText(code),
    count: scoreReleaseReadinessBlockerCount(readiness, code),
    action: scoreReleaseBlockerActionText(code)
  }));
});
const scoreReleaseReadinessMetricRows = computed(() => {
  const readiness = scoreReleaseReadiness.value;
  if (!readiness) return [];
  return [
    { key: 'attempts', label: 'Attempts', value: readinessNumberText(readiness.attemptCount) },
    { key: 'completed', label: 'Completed', value: readinessNumberText(readiness.completedAttemptCount) },
    { key: 'scored', label: 'Scored', value: readinessNumberText(readiness.scoredCompletedAttemptCount) },
    { key: 'review', label: 'Pending Review', value: readinessNumberText(readiness.pendingReviewAttemptCount) },
    { key: 'answers', label: 'Pending Answers', value: readinessNumberText(readiness.pendingAnswerReviewCount) },
    { key: 'active', label: 'Active', value: readinessNumberText(readiness.activeAttemptCount) },
    { key: 'appeals', label: 'Appeals', value: readinessNumberText(readiness.pendingScoreAppealCount) },
    { key: 'recheck', label: 'Recheck', value: readinessNumberText(readiness.openRecheckAppealCount) },
    { key: 'unscored', label: 'Missing Scores', value: readinessNumberText(readiness.unscoredCompletedAttemptCount) }
  ];
});
const scoreReleaseSafetyStatRows = computed(() => {
  const summary = scoreReleaseSafetySummaryData.value;
  return [
    { key: 'total', label: 'Total', value: readinessNumberText(summary.total) },
    { key: 'actionRequired', label: 'Action Required', value: readinessNumberText(summary.actionRequired) },
    { key: 'ready', label: 'Ready', value: readinessNumberText(summary.ready) },
    { key: 'blocked', label: 'Blocked', value: readinessNumberText(summary.blocked) },
    { key: 'released', label: 'Released', value: readinessNumberText(summary.released) },
    { key: 'revoked', label: 'Revoked', value: readinessNumberText(summary.revoked) }
  ];
});
const scoreReleaseSafetyBlockerRows = computed(() => Object.entries(scoreReleaseSafetySummaryData.value.blockerCounts || {})
  .map(([code, count]) => ({ code, count: Number(count || 0) }))
  .filter((item) => item.count > 0)
  .sort((a, b) => b.count - a.count)
  .slice(0, 8));
const lifecycleHealthStatRows = computed(() => {
  const summary = lifecycleHealthSummaryData.value;
  return [
    { key: 'total', label: 'Total', value: readinessNumberText(summary.total) },
    { key: 'actionRequired', label: 'Action Required', value: readinessNumberText(summary.actionRequired) },
    { key: 'approval', label: 'Approval', value: readinessNumberText(summary.approval) },
    { key: 'running', label: 'Running', value: readinessNumberText(summary.running) },
    { key: 'review', label: 'Review', value: readinessNumberText(summary.review) },
    { key: 'scoreReady', label: 'Score Ready', value: readinessNumberText(summary.scoreReady) },
    { key: 'released', label: 'Released', value: readinessNumberText(summary.released) },
    { key: 'risk', label: 'Risk', value: readinessNumberText(summary.risk) }
  ];
});
const lifecycleHealthBlockerRows = computed(() => Object.entries(lifecycleHealthSummaryData.value.blockerCounts || {})
  .map(([code, count]) => ({ code, count: Number(count || 0) }))
  .filter((item) => item.count > 0)
  .sort((a, b) => b.count - a.count)
  .slice(0, 8));
const lifecycleHealthPublishableRows = computed(() => lifecycleHealthRows.value
  .filter((row) => canPublishScores(row as ExamInfo)));
const lifecycleHealthReviewRows = computed(() => lifecycleHealthRows.value
  .filter((row) => canOpenLifecycleReview(row)));
const lifecycleHealthRecheckRows = computed(() => lifecycleHealthRows.value
  .filter((row) => canOpenLifecycleRecheck(row)));
const lifecycleHealthAppealRows = computed(() => lifecycleHealthRows.value
  .filter((row) => canOpenLifecycleAppeals(row)));
const lifecycleHandoffTitle = computed(() => {
  if (!lifecycleHandoff.value) return 'Lifecycle handoff';
  const keyword = lifecycleHandoff.value.keyword ? ` / ${lifecycleHandoff.value.keyword}` : '';
  return `Lifecycle handoff ${lifecycleHandoff.value.state}${keyword}`;
});
const lifecycleHandoffMetricRows = computed(() => {
  const handoff = lifecycleHandoff.value;
  if (!handoff) return [];
  return [
    { key: 'filtered', label: 'Filtered', value: readinessNumberText(handoff.filteredTotal) },
    { key: 'action', label: 'Action Rows', value: readinessNumberText(handoff.actionTotal) },
    { key: 'required', label: 'Required', value: readinessNumberText(handoff.summary.actionRequired) },
    { key: 'risk', label: 'Risk', value: readinessNumberText(handoff.summary.risk) },
    { key: 'review', label: 'Review', value: readinessNumberText(handoff.summary.review) },
    { key: 'ready', label: 'Score Ready', value: readinessNumberText(handoff.summary.scoreReady) }
  ];
});
const lifecycleHandoffText = computed(() => {
  const handoff = lifecycleHandoff.value;
  if (!handoff) return '';
  const lines = [
    `# Lifecycle Handoff ${handoff.state}`,
    `Generated: ${formatDateTime(handoff.generatedAt)}`,
    `Role: ${handoff.role || '-'}`,
    `Keyword: ${handoff.keyword || '-'}`,
    `Filtered exams: ${readinessNumberText(handoff.filteredTotal)}`,
    '',
    '## Summary',
    `- Total: ${readinessNumberText(handoff.summary.total)}`,
    `- Action required: ${readinessNumberText(handoff.summary.actionRequired)}`,
    `- Risk: ${readinessNumberText(handoff.summary.risk)}`,
    `- Approval: ${readinessNumberText(handoff.summary.approval)}`,
    `- Running: ${readinessNumberText(handoff.summary.running)}`,
    `- Review: ${readinessNumberText(handoff.summary.review)}`,
    `- Score ready: ${readinessNumberText(handoff.summary.scoreReady)}`,
    `- Released: ${readinessNumberText(handoff.summary.released)}`,
    '',
    '## Distribution',
    `- Group: ${handoffCountMapText(handoff.groupCounts)}`,
    `- Severity: ${handoffCountMapText(handoff.severityCounts)}`,
    `- State: ${handoffCountMapText(handoff.stateCounts)}`,
    '',
    '## Top Blockers',
    ...(handoff.topBlockers.length > 0
      ? handoff.topBlockers.map((item) => `- ${item.code}: ${item.count}`)
      : ['- None']),
    '',
    '## Priority Actions',
    ...(handoff.actionRows.length > 0
      ? handoff.actionRows.map((row, index) => `${index + 1}. #${row.examId} ${row.examName || '-'} / ${row.lifecycleState} / ${row.lifecycleNextAction || '-'} / ${row.targetPath || '-'}`)
      : ['- None'])
  ];
  return lines.join('\n');
});
const lifecycleTimeline = computed<ExamLifecycleEvent[]>(() => lifecycle.value?.timeline || []);
const lifecycleTruncatedCount = computed(() => Number(lifecycle.value?.summary?.timelineTruncated || 0));
const lifecycleMetricRows = computed(() => {
  const summary = lifecycle.value?.summary || {};
  return [
    { key: 'attempts', label: 'Attempts', value: lifecycleSummaryText(summary.attemptCount) },
    { key: 'active', label: 'Active', value: lifecycleSummaryText(summary.activeAttemptCount) },
    { key: 'completed', label: 'Completed', value: lifecycleSummaryText(summary.completedAttemptCount) },
    { key: 'forced', label: 'Forced Submit', value: lifecycleSummaryText(summary.forcedSubmitCount) },
    { key: 'monitor', label: 'Monitor Events', value: lifecycleSummaryText(summary.monitorEventCount) },
    { key: 'risk', label: 'High Risk', value: lifecycleSummaryText(summary.highRiskSessionCount) },
    { key: 'approval', label: 'Approval Logs', value: lifecycleSummaryText(summary.approvalLogCount) },
    { key: 'score', label: 'Score Logs', value: lifecycleSummaryText(summary.scoreReleaseLogCount) }
  ];
});

const tableRef = ref<{ clearSelection: () => void }>();
const selectedExams = ref<ExamInfo[]>([]);
const lastExamOperationAudit = ref<{ action: string; operationLogIds: Array<number | string> } | null>(null);
const lastScoreReleaseAudit = ref<{ action: string; scoreReleaseLogIds: Array<number | string> } | null>(null);

const form = reactive<{
  paperId: number | null;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  maxAttempts: number;
  passScore: number | null;
  targetMode: TargetMode;
  classCourseIds: number[];
  studentUserIds: number[];
}>({
  paperId: null,
  examName: '',
  description: '',
  startTime: '',
  endTime: '',
  durationMinutes: 60,
  maxAttempts: 1,
  passScore: null,
  targetMode: 'CLASS_COURSE',
  classCourseIds: [],
  studentUserIds: []
});

const createButtonText = computed(() => (props.role === 'ADMIN' ? '发布考试' : '提交审批'));
const dialogTitle = computed(() => (editingId.value ? '编辑考试' : (props.role === 'ADMIN' ? '发布考试' : '提交考试审批')));
const selectedPaper = computed(() => papers.value.find((paper) => paper.id === form.paperId) || null);
const filteredPapers = computed(() => {
  const keyword = paperKeyword.value.trim().toLowerCase();
  if (!keyword) return papers.value;
  return papers.value.filter((paper) =>
    [paper.paperName, paper.subjectName, paper.description || ''].some((value) => value.toLowerCase().includes(keyword))
  );
});
const selectedClassCourses = computed(() => classCourses.value.filter((item) => form.classCourseIds.includes(item.classCourseId)));
const selectedStudents = computed(() => targetStudents.value.filter((item) => form.studentUserIds.includes(item.userId)));
const targetCount = computed(() => (form.targetMode === 'CLASS_COURSE' ? form.classCourseIds.length : form.studentUserIds.length));
const selectedTargetLabels = computed(() => {
  const labels = form.targetMode === 'CLASS_COURSE'
    ? selectedClassCourses.value.map((item) => `${item.className}/${item.courseName}`)
    : selectedStudents.value.map((item) => `${item.realName}${item.studentNo ? `(${item.studentNo})` : ''}`);
  return labels.length > 6 ? [...labels.slice(0, 6), `另 ${labels.length - 6} 个`] : labels;
});
const passScoreLabel = computed(() => (form.passScore === null || form.passScore === undefined ? '未设置及格线' : `及格线 ${form.passScore} 分`));

onMounted(async () => {
  await loadExams();
  await openLifecycleHealthFromRoute();
  await openScoreReleaseReadinessFromRoute();
});

watch(
  () => route.query.examId,
  async () => {
    currentPage.value = 1;
    await loadExams();
    await openScoreReleaseReadinessFromRoute();
  }
);

watch(
  () => [route.query.lifecycleHealth, route.query.lifecycleState, route.query.lifecycleKeyword],
  () => {
    openLifecycleHealthFromRoute();
  }
);

watch(
  () => route.query.scoreReadiness,
  () => {
    openScoreReleaseReadinessFromRoute();
  }
);

function onSelectionChange(rows: ExamInfo[]) {
  selectedExams.value = rows;
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

function rememberExamOperationAudit(action: string, ids: Array<number | string | null | undefined>) {
  const operationLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (operationLogIds.length === 0) return;
  lastExamOperationAudit.value = { action, operationLogIds };
}

function examOperationAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestExamOperationAuditId() {
  const ids = lastExamOperationAudit.value?.operationLogIds;
  if (!ids?.length) return;
  try {
    await copyOperationLogIdToClipboard(ids.join(','));
    ElMessage.success('Audit ID copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit ID copy failed');
  }
}

async function copyLatestExamOperationAuditLink() {
  const id = lastExamOperationAudit.value?.operationLogIds[0];
  if (!id) return;
  try {
    await copyOperationLogLinkToClipboard(id);
    ElMessage.success('Audit link copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit link copy failed');
  }
}

function hasStarted(row: ExamInfo) {
  const start = new Date(row.startTime).getTime();
  return !Number.isNaN(start) && Date.now() >= start;
}

function hasEnded(row: ExamInfo) {
  if (row.status === 2) return true;
  const end = new Date(row.endTime).getTime();
  return !Number.isNaN(end) && Date.now() >= end;
}

function hasStudentEntered(row: ExamInfo) {
  return Number(row.startedAttemptCount || 0) > 0;
}

function canEditExam(row: ExamInfo) {
  if (hasStudentEntered(row)) return false;
  if (row.status === 0 || row.status === 3) return true;
  return row.status === 1 && !hasStarted(row);
}

function canDeleteExam(row: ExamInfo) {
  if (hasStudentEntered(row)) return false;
  if (row.status === 0 || row.status === 3) return true;
  return !hasStarted(row);
}

function canCloseExam(row: ExamInfo) {
  return row.status === 1;
}

function canPublishScores(row: ExamInfo) {
  if (row.scoreReleaseReady !== undefined && row.scoreReleaseReady !== null) {
    return Number(row.scoreReleaseReady) === 1;
  }
  return row.scoreReleaseStatus !== 1
    && hasEnded(row)
    && Number(row.nonFinalStartedAttemptCount || 0) === 0
    && Number(row.pendingAnswerReviewCount || 0) === 0
    && Number(row.pendingScoreAppealCount || 0) === 0
    && Number(row.openRecheckAppealCount || 0) === 0
    && Number(row.unscoredCompletedAttemptCount || 0) === 0
    && Number(row.completedAttemptCount || 0) > 0;
}

function publishScoresDisabledReason(row: ExamInfo) {
  const serverBlocker = scoreReleaseBlockers(row)[0];
  if (serverBlocker) return scoreReleaseBlockerText(serverBlocker);
  if (row.scoreReleaseStatus === 1) return '成绩已发布';
  if (!hasEnded(row)) return '考试未结束，不能发布成绩';
  if (Number(row.activeAttemptCount || 0) > 0) return '仍有学生作答中，不能发布成绩';
  if (Number(row.pendingReviewAttemptCount || 0) > 0) return '仍有待批阅答卷，不能发布成绩';
  if (Number(row.nonFinalStartedAttemptCount || 0) > 0) return '存在未完成判分的答卷，不能发布成绩';
  if (Number(row.pendingScoreAppealCount || 0) > 0) return '仍有待处理的成绩申诉，不能发布成绩';
  if (Number(row.openRecheckAppealCount || 0) > 0) return '仍有待完成的成绩复核申诉，不能发布成绩';
  if (Number(row.unscoredCompletedAttemptCount || 0) > 0) return '存在缺少分数的已完成答卷，不能发布成绩';
  if (Number(row.completedAttemptCount || 0) === 0) return '暂无已完成答卷，不能发布成绩';
  return '存在未完成判分的答卷，不能发布成绩';
}

function scoreReleaseBlockers(row: ExamInfo) {
  return String(row.scoreReleaseBlockers || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function scoreReleaseBlockerText(blocker: string) {
  const text: Record<string, string> = {
    ALREADY_RELEASED: 'Scores are already released.',
    EXAM_NOT_ENDED: 'The exam has not ended.',
    ACTIVE_ATTEMPTS: 'Some students are still taking the exam.',
    PENDING_REVIEW: 'Some attempts are still waiting for review.',
    PENDING_REVIEW_ANSWERS: 'Some answers are still waiting for review.',
    NON_FINAL_ATTEMPTS: 'Some started attempts are not finalized.',
    PENDING_APPEALS: 'Some score appeals are still pending.',
    OPEN_RECHECK: 'Some recheck-required appeals are still open.',
    UNSCORED_COMPLETED: 'Some completed attempts are missing scores.',
    NO_COMPLETED_ATTEMPTS: 'No finalized scored attempts are available.'
  };
  return text[blocker] || 'Scores are not ready to publish.';
}

function scoreReleaseBlockerActionText(blocker: string) {
  const text: Record<string, string> = {
    ALREADY_RELEASED: 'No action required.',
    EXAM_NOT_ENDED: 'Wait until the exam end time.',
    ACTIVE_ATTEMPTS: 'Wait for active attempts or force-submit from monitoring.',
    PENDING_REVIEW: 'Open the review queue for this exam.',
    PENDING_REVIEW_ANSWERS: 'Open the review queue for this exam.',
    NON_FINAL_ATTEMPTS: 'Finalize non-final attempts before publishing.',
    PENDING_APPEALS: 'Open pending score appeals for this exam.',
    OPEN_RECHECK: 'Open recheck review tasks for this exam.',
    UNSCORED_COMPLETED: 'Investigate completed attempts missing scores.',
    NO_COMPLETED_ATTEMPTS: 'No completed attempts are available.'
  };
  return text[blocker] || 'Resolve this blocker before publishing.';
}

function scoreReleaseSafetyStateText(state?: string | null) {
  const text: Record<string, string> = {
    READY: 'Ready',
    BLOCKED: 'Blocked',
    RELEASED: 'Released',
    REVOKED: 'Revoked'
  };
  return text[String(state || '').toUpperCase()] || 'Unknown';
}

function scoreReleaseSafetyStateType(state?: string | null) {
  const value = String(state || '').toUpperCase();
  if (value === 'READY') return 'success';
  if (value === 'RELEASED') return 'info';
  if (value === 'REVOKED') return 'warning';
  if (value === 'BLOCKED') return 'danger';
  return 'info';
}

function lifecycleStateText(state?: string | null) {
  const text: Record<string, string> = {
    APPROVAL_PENDING: 'Approval',
    APPROVAL_START_PASSED: 'Approval Risk',
    REJECTED: 'Rejected',
    SNAPSHOT_RISK: 'Snapshot Risk',
    WAITING_TO_START: 'Waiting',
    RUNNING_HEALTHY: 'Running',
    RUNNING_NO_ACTIVE: 'Open',
    TIMEOUT_PRESSURE: 'Timeout',
    MONITOR_RISK: 'Monitor Risk',
    FINALIZE_REQUIRED: 'Finalize',
    REVIEW_REQUIRED: 'Review',
    RECHECK_REQUIRED: 'Recheck',
    APPEAL_REQUIRED: 'Appeal',
    SCORE_MISSING: 'Score Missing',
    NO_COMPLETED_ATTEMPTS: 'No Completed',
    SCORE_READY: 'Score Ready',
    SCORE_BLOCKED: 'Score Blocked',
    SCORE_RELEASED: 'Released',
    UNKNOWN: 'Inspect'
  };
  return text[String(state || '').toUpperCase()] || 'Inspect';
}

function lifecycleSeverityType(severity?: string | null) {
  const value = String(severity || '').toUpperCase();
  if (value === 'HIGH') return 'danger';
  if (value === 'WARN') return 'warning';
  if (value === 'OK') return 'success';
  return 'info';
}

function lifecycleBlockers(row: Partial<ExamLifecycleHealthExam>) {
  const raw = row.lifecycleBlockers;
  if (Array.isArray(raw)) {
    return raw.map((item) => String(item)).filter(Boolean);
  }
  const source = typeof raw === 'string' && raw.trim() ? raw : row.lifecycleBlockerCodes || '';
  return String(source)
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function canFinalizeLifecycleRow(row: Partial<ExamLifecycleHealthExam>) {
  const blockers = lifecycleBlockers(row);
  return row.lifecycleState === 'FINALIZE_REQUIRED'
    || blockers.includes('ACTIVE_ATTEMPTS')
    || blockers.includes('NON_FINAL_ATTEMPTS')
    || blockers.includes('DEADLINE_PASSED_ACTIVE')
    || Number(row.activeAttemptCount || 0) > 0
    || Number(row.deadlinePassedActiveCount || 0) > 0;
}

function canRepairLifecycleSnapshot(row: Partial<ExamLifecycleHealthExam>) {
  const state = String(row.lifecycleState || '').toUpperCase();
  const blockers = lifecycleBlockers(row);
  return state === 'SNAPSHOT_RISK'
    || blockers.includes('CANDIDATE_SNAPSHOT_MISSING')
    || blockers.includes('QUESTION_SNAPSHOT_MISSING')
    || Number(row.candidateSnapshotCount || 0) === 0
    || Number(row.questionSnapshotCount || 0) === 0;
}

function canOpenLifecycleReview(row: Partial<ExamLifecycleHealthExam>) {
  const state = String(row.lifecycleState || '').toUpperCase();
  const blockers = lifecycleBlockers(row);
  return state === 'REVIEW_REQUIRED'
    || blockers.includes('PENDING_REVIEW')
    || blockers.includes('PENDING_REVIEW_ANSWERS')
    || Number(row.pendingReviewAttemptCount || 0) > 0
    || Number(row.pendingAnswerReviewCount || 0) > 0;
}

function canOpenLifecycleRecheck(row: Partial<ExamLifecycleHealthExam>) {
  const state = String(row.lifecycleState || '').toUpperCase();
  const blockers = lifecycleBlockers(row);
  return state === 'RECHECK_REQUIRED'
    || blockers.includes('OPEN_RECHECK')
    || Number(row.openRecheckAppealCount || 0) > 0;
}

function canOpenLifecycleAppeals(row: Partial<ExamLifecycleHealthExam>) {
  const state = String(row.lifecycleState || '').toUpperCase();
  const blockers = lifecycleBlockers(row);
  return state === 'APPEAL_REQUIRED'
    || blockers.includes('PENDING_APPEALS')
    || Number(row.pendingScoreAppealCount || 0) > 0;
}

function lifecycleBlockerText(blocker: string) {
  const text: Record<string, string> = {
    APPROVAL_PENDING: 'The exam is waiting for administrator approval.',
    APPROVAL_START_PASSED: 'The planned start time has already passed before approval.',
    NO_TARGET: 'The exam has no configured target range.',
    EXAM_REJECTED: 'The exam was rejected and needs editing before resubmission.',
    CANDIDATE_SNAPSHOT_MISSING: 'The published exam has no candidate snapshot.',
    QUESTION_SNAPSHOT_MISSING: 'The published exam has no question snapshot.',
    DEADLINE_PASSED_ACTIVE: 'Some attempts are still active after their server deadline.',
    OFFLINE_MONITOR: 'Some active monitor sessions have stale heartbeats.',
    HIGH_RISK_MONITOR: 'Some active monitor sessions have high risk scores.',
    STALE_DRAFTS: 'Some active attempts have stale database drafts.',
    TIMEOUT_PRESSURE: 'Some active attempts are close to timeout.',
    UNKNOWN_STATE: 'The lifecycle state needs manual inspection.'
  };
  return text[blocker] || scoreReleaseBlockerText(blocker);
}

function openLifecycleHealthReviewQueue() {
  lifecycleHealthVisible.value = false;
  router.push({ path: '/reviews', query: { reviewTaskType: 'STANDARD' } });
}

function openLifecycleHealthRecheckQueue() {
  lifecycleHealthVisible.value = false;
  router.push({
    path: '/reviews',
    query: {
      reviewTaskType: 'RECHECK',
      appealStatus: '1',
      appealHandlingResult: 'RECHECK_REQUIRED'
    }
  });
}

function openLifecycleHealthAppealQueue() {
  lifecycleHealthVisible.value = false;
  router.push({ path: '/reviews', query: { appealStatus: '0' } });
}

function openLifecycleReviewQueue(row: ExamLifecycleHealthExam) {
  openScoreReleaseResolution(row as ExamInfo, 'REVIEW');
}

function openLifecycleRecheckQueue(row: ExamLifecycleHealthExam) {
  openScoreReleaseResolution(row as ExamInfo, 'RECHECK');
}

function openLifecycleAppealQueue(row: ExamLifecycleHealthExam) {
  openScoreReleaseResolution(row as ExamInfo, 'APPEALS');
}

function openLifecycleHealthNextAction(row: ExamLifecycleHealthExam) {
  const examId = Number(row.id || row.examId || 0);
  const actionType = String(row.lifecycleNextActionType || '').toUpperCase();
  if (actionType === 'APPROVAL' && examId > 0) {
    router.push({ path: '/exam-approvals', query: { examId: String(examId) } });
    return;
  }
  if (actionType === 'EDIT') {
    openEdit(row as ExamInfo);
    return;
  }
  if (actionType === 'SNAPSHOT') {
    openSnapshot(row as ExamInfo);
    return;
  }
  if (actionType === 'MONITOR' && examId > 0) {
    router.push({ path: '/exam-monitor', query: { examId: String(examId) } });
    return;
  }
  if (actionType === 'REVIEW' && examId > 0) {
    router.push({ path: '/reviews', query: { reviewExamId: String(examId), reviewTaskType: 'STANDARD' } });
    return;
  }
  if (actionType === 'RECHECK') {
    openScoreReleaseResolution(row as ExamInfo, 'RECHECK');
    return;
  }
  if (actionType === 'APPEALS') {
    openScoreReleaseResolution(row as ExamInfo, 'APPEALS');
    return;
  }
  if (actionType === 'READINESS') {
    openScoreReleaseReadiness(row as ExamInfo);
    return;
  }
  if (actionType === 'SCORE_LOGS') {
    openScoreReleaseLogs(row as ExamInfo);
    return;
  }
  openLifecycle(row as ExamInfo);
}

function scoreReleaseReadinessBlockerCount(readiness: ScoreReleaseReadiness, blocker: string) {
  const countMap: Record<string, unknown> = {
    ACTIVE_ATTEMPTS: readiness.activeAttemptCount,
    PENDING_REVIEW: readiness.pendingReviewAttemptCount,
    PENDING_REVIEW_ANSWERS: readiness.pendingAnswerReviewCount,
    NON_FINAL_ATTEMPTS: readiness.nonFinalStartedAttemptCount,
    PENDING_APPEALS: readiness.pendingScoreAppealCount,
    OPEN_RECHECK: readiness.openRecheckAppealCount,
    UNSCORED_COMPLETED: readiness.unscoredCompletedAttemptCount,
    NO_COMPLETED_ATTEMPTS: readiness.completedAttemptCount
  };
  return countMap[blocker] as number | string | undefined;
}

function readinessNumberText(value?: number | string | boolean | null) {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? String(parsed) : '-';
}

function handoffCountMapText(value?: Record<string, number> | null) {
  const entries = Object.entries(value || {})
    .filter(([, count]) => Number(count || 0) > 0)
    .sort((left, right) => Number(right[1] || 0) - Number(left[1] || 0));
  return entries.length > 0 ? entries.map(([key, count]) => `${key} ${count}`).join(', ') : 'None';
}

function readinessCountText(value?: number | string | null) {
  return value === null || value === undefined ? '-' : readinessNumberText(value);
}

function scoreReleaseBlockerResolutionMode(blocker?: string | null): ScoreReleaseResolutionMode | null {
  if (blocker === 'OPEN_RECHECK') return 'RECHECK';
  if (blocker === 'PENDING_APPEALS') return 'APPEALS';
  if (blocker === 'PENDING_REVIEW' || blocker === 'PENDING_REVIEW_ANSWERS') return 'REVIEW';
  return null;
}

function canRecalculateScoreReleaseBlocker(blocker?: string | null) {
  return blocker === 'UNSCORED_COMPLETED';
}

function canFinalizeScoreReleaseBlocker(blocker?: string | null) {
  return blocker === 'ACTIVE_ATTEMPTS' || blocker === 'NON_FINAL_ATTEMPTS';
}

function canResolveScoreReleaseBlocker(blocker?: string | null) {
  return scoreReleaseBlockerResolutionMode(blocker) !== null;
}

function scoreReleaseResolutionMode(row: ExamInfo): ScoreReleaseResolutionMode | null {
  const blockers = scoreReleaseBlockers(row);
  if (blockers.includes('OPEN_RECHECK') || Number(row.openRecheckAppealCount || 0) > 0) {
    return 'RECHECK';
  }
  if (blockers.includes('PENDING_APPEALS') || Number(row.pendingScoreAppealCount || 0) > 0) {
    return 'APPEALS';
  }
  if (blockers.includes('PENDING_REVIEW')
    || blockers.includes('PENDING_REVIEW_ANSWERS')
    || Number(row.pendingReviewAttemptCount || 0) > 0
    || Number(row.pendingAnswerReviewCount || 0) > 0) {
    return 'REVIEW';
  }
  return null;
}

function canResolveScoreReleaseBlockers(row: ExamInfo) {
  return row.scoreReleaseStatus !== 1 && scoreReleaseResolutionMode(row) !== null;
}

function canRecalculateMissingScores(row: ExamInfo) {
  if (row.scoreReleaseStatus === 1) return false;
  const blockers = scoreReleaseBlockers(row);
  return blockers.includes('UNSCORED_COMPLETED') || Number(row.unscoredCompletedAttemptCount || 0) > 0;
}

function canFinalizeActiveAttempts(row: ExamInfo) {
  if (row.scoreReleaseStatus === 1) return false;
  if (!hasEnded(row)) return false;
  const blockers = scoreReleaseBlockers(row);
  return blockers.includes('ACTIVE_ATTEMPTS')
    || blockers.includes('NON_FINAL_ATTEMPTS')
    || Number(row.activeAttemptCount || 0) > 0;
}

function openScoreReleaseResolution(row: ExamInfo, forcedMode?: ScoreReleaseResolutionMode | null) {
  const mode = forcedMode || scoreReleaseResolutionMode(row);
  if (!mode) return;
  const examId = Number(row.id || (row as any).examId || 0);
  if (!Number.isFinite(examId) || examId <= 0) return;
  const nextQuery: Record<string, string> = {};
  if (mode === 'RECHECK') {
    nextQuery.reviewExamId = String(examId);
    nextQuery.reviewTaskType = 'RECHECK';
    nextQuery.appealExamId = String(examId);
    nextQuery.appealStatus = '1';
    nextQuery.appealHandlingResult = 'RECHECK_REQUIRED';
  } else if (mode === 'APPEALS') {
    nextQuery.appealExamId = String(examId);
    nextQuery.appealStatus = '0';
  } else {
    nextQuery.reviewExamId = String(examId);
    nextQuery.reviewTaskType = 'STANDARD';
  }
  router.push({ path: '/reviews', query: nextQuery });
}

function openScoreReleaseBlockerResolution(detail: { code?: string | null }) {
  const mode = scoreReleaseBlockerResolutionMode(detail.code);
  const row = scoreReleaseReadinessExam.value;
  if (!row || !mode) return;
  openScoreReleaseResolution(row, mode);
}

async function recalculateMissingScoresFromReadiness() {
  const row = scoreReleaseReadinessExam.value;
  const examId = Number(row?.id || scoreReleaseReadiness.value?.examId || 0);
  if (!Number.isFinite(examId) || examId <= 0) return;
  await recalculateMissingScoresById(examId, row || undefined);
}

async function finalizeActiveAttemptsFromReadiness() {
  const row = scoreReleaseReadinessExam.value;
  const examId = Number(row?.id || scoreReleaseReadiness.value?.examId || 0);
  if (!Number.isFinite(examId) || examId <= 0) return;
  await finalizeActiveAttemptsById(examId, row || undefined);
}

function canApproveExam(row: ExamInfo) {
  return props.role === 'ADMIN' && row.status === 0;
}

function canRejectExam(row: ExamInfo) {
  return props.role === 'ADMIN' && row.status === 0;
}

async function openLifecycleHealthFromRoute() {
  const enabled = routeQueryText(route.query.lifecycleHealth);
  if (enabled !== '1' && enabled?.toLowerCase() !== 'true') return;
  lifecycleHealthQuery.keyword = routeQueryText(route.query.lifecycleKeyword) || '';
  lifecycleHealthQuery.state = lifecycleHealthRouteState(route.query.lifecycleState);
  lifecycleHealthQuery.page = 1;
  lifecycleHealthVisible.value = true;
  await loadLifecycleHealth();
}

async function openScoreReleaseReadinessFromRoute() {
  const enabled = routeQueryText(route.query.scoreReadiness);
  if (enabled !== '1' && enabled?.toLowerCase() !== 'true') return;
  const examId = focusedExamId.value;
  if (!examId) return;
  const row = exams.value.find((item) => Number(item.id) === Number(examId));
  if (!row) return;
  await openScoreReleaseReadiness(row);
}

function lifecycleHealthRouteState(value: unknown): ExamLifecycleHealthState {
  const state = (routeQueryText(value) || 'ACTION_REQUIRED').toUpperCase();
  const allowed: ExamLifecycleHealthState[] = [
    'ALL',
    'ACTION_REQUIRED',
    'APPROVAL',
    'WAITING',
    'RUNNING',
    'REVIEW',
    'SCORE_READY',
    'RELEASED',
    'RISK'
  ];
  return allowed.includes(state as ExamLifecycleHealthState) ? state as ExamLifecycleHealthState : 'ACTION_REQUIRED';
}

function routeQueryText(value: unknown) {
  if (Array.isArray(value)) return value.length > 0 ? String(value[0]) : '';
  if (value === null || value === undefined) return '';
  return String(value);
}

async function loadExams() {
  loading.value = true;
  try {
    const response = await listTeacherExams({
      keyword: query.keyword,
      status: typeof query.status === 'number' ? query.status : null,
      examId: focusedExamId.value,
      page: currentPage.value,
      size: pageSize.value
    });
    exams.value = response.data.list;
    totalExams.value = response.data.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '考试列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function openLifecycleHealth() {
  lifecycleHealthVisible.value = true;
  lifecycleHealthQuery.page = 1;
  await loadLifecycleHealth();
}

async function reloadLifecycleHealth() {
  lifecycleHealthQuery.page = 1;
  await loadLifecycleHealth();
}

async function loadLifecycleHealth() {
  lifecycleHealthLoading.value = true;
  try {
    const response = (await getExamLifecycleHealth({
      keyword: lifecycleHealthQuery.keyword,
      state: lifecycleHealthQuery.state,
      page: lifecycleHealthQuery.page,
      size: lifecycleHealthQuery.size
    })).data;
    lifecycleHealthQuery.state = response.state;
    lifecycleHealthRows.value = response.page.list;
    lifecycleHealthTotal.value = response.page.total;
    lifecycleHealthSummaryData.value = {
      ...emptyLifecycleHealthSummary(),
      ...response.summary,
      blockerCounts: response.summary.blockerCounts || {}
    };
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Lifecycle health loading failed');
  } finally {
    lifecycleHealthLoading.value = false;
  }
}

function handleLifecycleHealthStateChange() {
  lifecycleHealthQuery.page = 1;
  loadLifecycleHealth();
}

function handleLifecycleHealthPageChange(page: number) {
  lifecycleHealthQuery.page = page;
  loadLifecycleHealth();
}

function handleLifecycleHealthSizeChange(size: number) {
  lifecycleHealthQuery.size = size;
  lifecycleHealthQuery.page = 1;
  loadLifecycleHealth();
}

async function exportLifecycleHealthRows() {
  lifecycleHealthExporting.value = true;
  try {
    await exportExamLifecycleHealth({
      keyword: lifecycleHealthQuery.keyword,
      state: lifecycleHealthQuery.state
    });
    ElMessage.success('Lifecycle health export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Lifecycle health export failed');
  } finally {
    lifecycleHealthExporting.value = false;
  }
}

async function openLifecycleHandoff() {
  lifecycleHandoffVisible.value = true;
  await loadLifecycleHandoff();
}

async function loadLifecycleHandoff() {
  lifecycleHandoffLoading.value = true;
  try {
    lifecycleHandoff.value = (await getExamLifecycleHealthHandoff({
      keyword: lifecycleHealthQuery.keyword,
      state: lifecycleHealthQuery.state
    })).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Lifecycle handoff loading failed');
  } finally {
    lifecycleHandoffLoading.value = false;
  }
}

async function copyLifecycleHandoff() {
  if (!lifecycleHandoffText.value) return;
  lifecycleHandoffCopying.value = true;
  try {
    await writeClipboardText(lifecycleHandoffText.value);
    ElMessage.success('Lifecycle handoff copied');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Lifecycle handoff copy failed');
  } finally {
    lifecycleHandoffCopying.value = false;
  }
}

async function notifyLifecycleHandoff(audience: ExamLifecycleHandoffAudience) {
  lifecycleHandoffNotifyTarget.value = audience;
  try {
    const response = (await notifyExamLifecycleHealthHandoff({
      keyword: lifecycleHealthQuery.keyword,
      state: lifecycleHealthQuery.state,
      audience
    })).data;
    lifecycleHandoff.value = response.handoff;
    ElMessage.success(`Lifecycle handoff notified ${response.recipientCount} recipient(s)`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Lifecycle handoff notify failed');
  } finally {
    lifecycleHandoffNotifyTarget.value = '';
  }
}

function openLifecycleHandoffTarget(row: ExamLifecycleHandoffAction) {
  if (!row.targetPath) return;
  lifecycleHandoffVisible.value = false;
  lifecycleHealthVisible.value = false;
  router.push(row.targetPath);
}

async function openScoreReleaseSafety() {
  scoreReleaseSafetyVisible.value = true;
  scoreReleaseSafetyQuery.page = 1;
  await loadScoreReleaseSafety();
}

async function reloadScoreReleaseSafety() {
  scoreReleaseSafetyQuery.page = 1;
  await loadScoreReleaseSafety();
}

async function loadScoreReleaseSafety() {
  scoreReleaseSafetyLoading.value = true;
  try {
    const response = (await getScoreReleaseSafety({
      keyword: scoreReleaseSafetyQuery.keyword,
      state: scoreReleaseSafetyQuery.state,
      page: scoreReleaseSafetyQuery.page,
      size: scoreReleaseSafetyQuery.size
    })).data;
    scoreReleaseSafetyQuery.state = response.state;
    scoreReleaseSafetyRows.value = response.page.list;
    scoreReleaseSafetyTotal.value = response.page.total;
    scoreReleaseSafetySummaryData.value = {
      ...emptyScoreReleaseSafetySummary(),
      ...response.summary,
      blockerCounts: response.summary.blockerCounts || {}
    };
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Score release safety loading failed');
  } finally {
    scoreReleaseSafetyLoading.value = false;
  }
}

function handleScoreReleaseSafetyStateChange() {
  scoreReleaseSafetyQuery.page = 1;
  loadScoreReleaseSafety();
}

function handleScoreReleaseSafetyPageChange(page: number) {
  scoreReleaseSafetyQuery.page = page;
  loadScoreReleaseSafety();
}

function handleScoreReleaseSafetySizeChange(size: number) {
  scoreReleaseSafetyQuery.size = size;
  scoreReleaseSafetyQuery.page = 1;
  loadScoreReleaseSafety();
}

async function exportScoreReleaseSafetyRows() {
  scoreReleaseSafetyExporting.value = true;
  try {
    await exportScoreReleaseSafety({
      keyword: scoreReleaseSafetyQuery.keyword,
      state: scoreReleaseSafetyQuery.state
    });
    ElMessage.success('Score safety export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Score safety export failed');
  } finally {
    scoreReleaseSafetyExporting.value = false;
  }
}

function routeExamId() {
  const raw = Array.isArray(route.query.examId) ? route.query.examId[0] : route.query.examId;
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function examRowClassName({ row }: { row: ExamInfo }) {
  return row.id === focusedExamId.value ? 'exam-management-row-focused' : '';
}

function handlePageChange(page: number) {
  currentPage.value = page;
  loadExams();
}

function handleFilterChange() {
  currentPage.value = 1;
  loadExams();
}

function handleSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
  loadExams();
}

function resetForm() {
  form.paperId = null;
  form.examName = '';
  form.description = '';
  form.startTime = '';
  form.endTime = '';
  form.durationMinutes = 60;
  form.maxAttempts = 1;
  form.passScore = null;
  form.targetMode = 'CLASS_COURSE';
  form.classCourseIds = [];
  form.studentUserIds = [];
  publishStep.value = 0;
  paperKeyword.value = '';
  preflight.value = null;
}

async function openCreate() {
  editingId.value = null;
  editPaperName.value = '';
  resetForm();
  dialogVisible.value = true;
  try {
    const [paperResponse, classCourseResponse, studentResponse] = await Promise.all([
      listPapers({ status: 1 }),
      listClassCourses({ status: 1 }),
      listExamTargetStudents()
    ]);
    papers.value = paperResponse.data.list;
    classCourses.value = classCourseResponse.data;
    targetStudents.value = studentResponse.data;
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '发布数据加载失败');
  }
}

function openEdit(row: ExamInfo) {
  if (!canEditExam(row)) {
    ElMessage.warning('考试已开始、已关闭或已有学生进入，不能再编辑安排');
    return;
  }
  editingId.value = row.id;
  editPaperName.value = row.paperName || '';
  resetForm();
  form.paperId = row.paperId;
  form.examName = row.examName;
  form.description = row.description || '';
  form.startTime = row.startTime;
  form.endTime = row.endTime;
  form.durationMinutes = row.durationMinutes;
  form.maxAttempts = row.maxAttempts || 1;
  form.passScore = row.passScore ?? null;
  dialogVisible.value = true;
}

async function openSnapshot(row: ExamInfo) {
  snapshotVisible.value = true;
  snapshotLoading.value = true;
  snapshot.value = null;
  try {
    snapshot.value = (await getExamSnapshot(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '快照加载失败');
  } finally {
    snapshotLoading.value = false;
  }
}

async function exportSnapshotEvidence() {
  const exam = snapshot.value?.exam;
  if (!exam?.id) return;
  snapshotExporting.value = true;
  try {
    await exportExamSnapshot(exam.id, exam.examName);
    ElMessage.success('Snapshot evidence export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Snapshot evidence export failed');
  } finally {
    snapshotExporting.value = false;
  }
}

function openSnapshotExportAudit() {
  const examId = snapshot.value?.exam?.id;
  if (!examId) return;
  router.push({
    path: '/monitor/logs',
    query: {
      tab: 'operation',
      action: 'EXPORT_EXAM_SNAPSHOT',
      target: `EXAM#${examId}`
    }
  });
}

async function repairCurrentSnapshot() {
  const exam = snapshot.value?.exam;
  if (!exam?.id) return;
  await repairSnapshotById(exam.id);
}

async function repairLifecycleSnapshot(row: ExamLifecycleHealthExam) {
  const examId = Number(row.id || row.examId || 0);
  if (!Number.isFinite(examId) || examId <= 0) return;
  await repairSnapshotById(examId);
}

async function repairSnapshotById(examId: number) {
  try {
    await ElMessageBox.confirm(
      'Repair candidate and question snapshots from the current exam targets and paper? Existing candidate snapshots and submitted answers will not be deleted.',
      'Repair Snapshot',
      {
        type: 'warning',
        confirmButtonText: 'Repair',
        cancelButtonText: 'Cancel'
      }
    );
  } catch {
    return;
  }
  snapshotRepairing.value = true;
  try {
    const response = await repairExamSnapshot(examId);
    rememberExamOperationAudit('Repair exam snapshot', [response.data.operationLogId]);
    ElMessage.success(
      `Snapshot repaired: candidates ${response.data.candidateSnapshotBefore} -> ${response.data.candidateSnapshotAfter}, questions ${response.data.questionSnapshotBefore} -> ${response.data.questionSnapshotAfter}`
    );
    if (snapshotVisible.value && snapshot.value?.exam?.id === examId) {
      snapshot.value = (await getExamSnapshot(examId)).data;
    }
    await loadExams();
    if (lifecycleHealthVisible.value) {
      await loadLifecycleHealth();
    }
    if (scoreReleaseSafetyVisible.value) {
      await loadScoreReleaseSafety();
    }
    if (lifecycleHandoffVisible.value) {
      await loadLifecycleHandoff();
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Snapshot repair failed');
  } finally {
    snapshotRepairing.value = false;
  }
}

async function recalculateMissingScoresFromLifecycle(row: ExamLifecycleHealthExam) {
  await recalculateMissingScoresById(Number(row.id || row.examId || 0), row as ExamInfo);
}

async function recalculateMissingScoresFromSafety(row: ScoreReleaseSafetyExam) {
  await recalculateMissingScoresById(Number(row.id || row.examId || 0), row as ExamInfo);
}

async function finalizeActiveAttemptsFromLifecycle(row: ExamLifecycleHealthExam) {
  await finalizeActiveAttemptsById(Number(row.id || row.examId || 0), row as ExamInfo);
}

async function finalizeActiveAttemptsFromSafety(row: ScoreReleaseSafetyExam) {
  await finalizeActiveAttemptsById(Number(row.id || row.examId || 0), row as ExamInfo);
}

async function recalculateMissingScoresById(examId: number, row?: ExamInfo) {
  if (!Number.isFinite(examId) || examId <= 0) return;
  try {
    await ElMessageBox.confirm(
      'Recalculate missing scores for finalized attempts that already have answer records and no pending review? This does not publish scores and does not overwrite existing scores.',
      'Recalculate Missing Scores',
      {
        type: 'warning',
        confirmButtonText: 'Recalculate',
        cancelButtonText: 'Cancel'
      }
    );
  } catch {
    return;
  }
  missingScoreRecalculating.value = examId;
  try {
    const response = await recalculateMissingScores(examId);
    rememberExamOperationAudit('Recalculate missing scores', [response.data.operationLogId]);
    if (row) {
      row.unscoredCompletedAttemptCount = response.data.missingAfter;
      row.scoreReleaseReady = response.data.scoreReleaseReady ?? row.scoreReleaseReady;
      row.scoreReleaseBlockers = response.data.scoreReleaseBlockers ?? row.scoreReleaseBlockers;
      row.scoreReleaseStatus = response.data.scoreReleaseStatus ?? row.scoreReleaseStatus;
    }
    ElMessage.success(
      `Missing scores recalculated: ${response.data.recalculatedAttempts}/${response.data.eligibleAttempts}, remaining ${response.data.missingAfter}`
    );
    await loadExams();
    if (scoreReleaseReadinessVisible.value && scoreReleaseReadinessExam.value) {
      const readiness = (await getScoreReleaseReadiness(examId)).data;
      applyScoreReleaseReadiness(scoreReleaseReadinessExam.value, readiness);
      scoreReleaseReadiness.value = readiness;
    }
    if (scoreReleaseSafetyVisible.value) {
      await loadScoreReleaseSafety();
    }
    if (lifecycleHealthVisible.value) {
      await loadLifecycleHealth();
    }
    if (lifecycleHandoffVisible.value) {
      await loadLifecycleHandoff();
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Missing score recalculation failed');
  } finally {
    missingScoreRecalculating.value = null;
  }
}

async function finalizeActiveAttemptsById(examId: number, row?: ExamInfo) {
  if (!Number.isFinite(examId) || examId <= 0) return;
  try {
    await ElMessageBox.confirm(
      'Force-submit active attempts from saved drafts? Missing answers will be treated as unanswered. Attempts that require manual review will stay in the review queue.',
      'Finalize Active Attempts',
      {
        type: 'warning',
        confirmButtonText: 'Finalize',
        cancelButtonText: 'Cancel'
      }
    );
  } catch {
    return;
  }
  activeAttemptsFinalizing.value = examId;
  try {
    const response = await finalizeActiveExamAttempts(examId);
    rememberExamOperationAudit('Finalize active attempts', [response.data.operationLogId]);
    if (row) {
      row.activeAttemptCount = Number(response.data.activeAfter ?? row.activeAttemptCount ?? 0);
      row.pendingReviewAttemptCount = Number(response.data.pendingReviewAfter ?? row.pendingReviewAttemptCount ?? 0);
      row.completedAttemptCount = Number(response.data.completedAfter ?? row.completedAttemptCount ?? 0);
      row.nonFinalStartedAttemptCount = Number(response.data.nonFinalAfter ?? row.nonFinalStartedAttemptCount ?? 0);
      row.scoreReleaseReady = response.data.scoreReleaseReady ?? row.scoreReleaseReady;
      row.scoreReleaseBlockers = response.data.scoreReleaseBlockers ?? row.scoreReleaseBlockers;
      row.scoreReleaseStatus = response.data.scoreReleaseStatus ?? row.scoreReleaseStatus;
    }
    const failed = Number(response.data.failedAttempts || 0);
    const pending = Number(response.data.pendingReviewAfter || 0);
    const suffix = failed > 0 ? `, failed ${failed}` : '';
    ElMessage.success(
      `Finalized ${response.data.forcedSubmittedAttempts}/${response.data.eligibleActiveAttempts} active attempt(s), pending review ${pending}${suffix}`
    );
    await loadExams();
    if (scoreReleaseReadinessVisible.value && scoreReleaseReadinessExam.value) {
      const readiness = (await getScoreReleaseReadiness(examId)).data;
      applyScoreReleaseReadiness(scoreReleaseReadinessExam.value, readiness);
      scoreReleaseReadiness.value = readiness;
    }
    if (scoreReleaseSafetyVisible.value) {
      await loadScoreReleaseSafety();
    }
    if (lifecycleHealthVisible.value) {
      await loadLifecycleHealth();
    }
    if (lifecycleHandoffVisible.value) {
      await loadLifecycleHandoff();
    }
    if (snapshotVisible.value && snapshot.value?.exam?.id === examId) {
      snapshot.value = (await getExamSnapshot(examId)).data;
    }
    if (pending > 0) {
      ElMessage.warning('Some attempts still require manual review before score release.');
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Active attempt finalization failed');
  } finally {
    activeAttemptsFinalizing.value = null;
  }
}

async function openApprovalLogs(row: ExamInfo) {
  approvalExam.value = row;
  approvalLogVisible.value = true;
  approvalLogLoading.value = true;
  approvalLogs.value = [];
  try {
    approvalLogs.value = (await getExamApprovalLogs(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '审批记录加载失败');
  } finally {
    approvalLogLoading.value = false;
  }
}

async function exportApprovalLogRows() {
  if (!approvalExam.value) return;
  approvalLogExporting.value = true;
  try {
    await exportExamApprovalLogs(approvalExam.value.id, approvalExam.value.examName);
    ElMessage.success('Approval log export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Export failed');
  } finally {
    approvalLogExporting.value = false;
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
    ElMessage.success('审批审计链接已复制');
  } catch {
    ElMessage.error('审批审计链接复制失败');
  }
}

async function openScoreReleaseLogs(row: ExamInfo) {
  scoreReleaseExam.value = row;
  scoreReleaseLogVisible.value = true;
  scoreReleaseLogLoading.value = true;
  scoreReleaseLogs.value = [];
  try {
    scoreReleaseLogs.value = (await getScoreReleaseLogs(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '成绩发布记录加载失败');
  } finally {
    scoreReleaseLogLoading.value = false;
  }
}

async function exportScoreReleaseLogRows() {
  if (!scoreReleaseExam.value) return;
  scoreReleaseLogExporting.value = true;
  try {
    await exportScoreReleaseLogs(scoreReleaseExam.value.id, scoreReleaseExam.value.examName);
    ElMessage.success('Score release log export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Export failed');
  } finally {
    scoreReleaseLogExporting.value = false;
  }
}

function openScoreReleaseAudit(logId?: number | null) {
  if (!logId) return;
  router.push(`/monitor/logs?scoreReleaseLogId=${encodeURIComponent(String(logId))}`);
}

async function copyScoreReleaseAuditLink(logId?: number | null) {
  try {
    const link = await copyScoreReleaseAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('成绩审计链接已复制');
  } catch {
    ElMessage.error('成绩审计链接复制失败');
  }
}

async function openLifecycle(row: ExamInfo) {
  lifecycleExam.value = row;
  lifecycle.value = null;
  lifecycleVisible.value = true;
  lifecycleLoading.value = true;
  try {
    lifecycle.value = (await getExamLifecycle(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Lifecycle load failed');
  } finally {
    lifecycleLoading.value = false;
  }
}

function lifecycleSummaryText(value: unknown) {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? String(parsed) : '-';
}

function lifecycleEventKey(event: ExamLifecycleEvent, index: number) {
  return `${event.source}-${event.sourceId ?? 'new'}-${event.eventType}-${index}`;
}

function lifecycleEventType(event: ExamLifecycleEvent) {
  const type = event.eventType || '';
  if (type.includes('REJECT') || type.includes('REVOKE')) return 'danger';
  if (type.includes('FORCE') || type.includes('WARN') || type.includes('CLOSE')) return 'warning';
  if (type.includes('APPROVE') || type.includes('PUBLISH') || type.includes('CREATED')) return 'success';
  if (type.includes('UPDATE')) return 'primary';
  return 'info';
}

function lifecycleEventLabel(event: ExamLifecycleEvent) {
  const map: Record<string, string> = {
    CREATED: 'Exam created',
    CREATE_EXAM: 'Exam created',
    UPDATE_EXAM: 'Exam updated',
    CLOSE_EXAM: 'Exam closed',
    DELETE_EXAM: 'Exam deleted',
    APPROVAL_SUBMIT: 'Submitted for approval',
    APPROVAL_RESUBMIT: 'Resubmitted for approval',
    APPROVAL_APPROVE: 'Approved and published',
    APPROVAL_REJECT: 'Rejected by admin',
    APPROVAL_DIRECT_PUBLISH: 'Directly published by admin',
    SCORE_PUBLISH: 'Scores published',
    SCORE_REVOKE: 'Scores revoked',
    MONITOR_WARN: 'Monitor warning sent',
    MONITOR_ACKNOWLEDGE: 'Monitor action acknowledged',
    MONITOR_FORCE_SUBMIT: 'Monitor forced submission',
    MONITOR_NOTE: 'Monitor note recorded',
    MONITOR_RULES_REMINDER: 'Rules reminder sent',
    ATTEMPT_FORCED_SUBMIT: 'Attempt force-submitted'
  };
  return map[event.eventType] || event.eventType || event.source;
}

function lifecycleStatusFlow(event: ExamLifecycleEvent) {
  if (event.statusFrom === null || event.statusFrom === undefined || event.statusTo === null || event.statusTo === undefined) {
    return '';
  }
  const formatter = event.source === 'SCORE_RELEASE_LOG' ? scoreReleaseStatusText : approvalStatusText;
  return `${formatter(lifecycleStatusNumber(event.statusFrom))} -> ${formatter(lifecycleStatusNumber(event.statusTo))}`;
}

function lifecycleStatusNumber(value: number | string | null | undefined) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function lifecycleDetailEntries(event: ExamLifecycleEvent) {
  return Object.entries(event.details || {})
    .filter(([, value]) => value !== null && value !== undefined && value !== '')
    .map(([key, value]) => ({
      label: lifecycleDetailLabel(key),
      value: lifecycleDetailValue(value)
    }));
}

function lifecycleDetailLabel(key: string) {
  const map: Record<string, string> = {
    paperName: 'Paper',
    subjectName: 'Subject',
    targetCount: 'Targets',
    candidateCount: 'Candidates',
    notifiedStudentCount: 'Students Notified',
    notifiedAttemptCount: 'Attempts Notified',
    visibleAttemptCount: 'Visible Attempts',
    sessionId: 'Session',
    attemptId: 'Attempt',
    studentName: 'Student',
    studentNo: 'Student No',
    notificationSent: 'Notice Sent',
    notificationId: 'Notice',
    target: 'Target',
    ip: 'IP'
  };
  return map[key] || key;
}

function lifecycleDetailValue(value: unknown) {
  if (typeof value === 'boolean') return value ? 'Yes' : 'No';
  if (typeof value === 'number' || typeof value === 'string') return String(value);
  return JSON.stringify(value);
}

async function forceSubmitCandidate(attemptId?: number | null) {
  if (!attemptId) return;
  try {
    await ElMessageBox.confirm('确认强制提交该学生当前答卷吗？系统会使用已保存草稿生成答卷，未保存内容将按未答处理。', '强制交卷', {
      type: 'warning',
      confirmButtonText: '确认强制交卷',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    await forceSubmitAttempt(attemptId);
    ElMessage.success('已强制交卷');
    const examId = snapshot.value?.exam?.id;
    if (examId) {
      snapshot.value = (await getExamSnapshot(examId)).data;
    }
    await loadExams();
    if (lifecycleHealthVisible.value) {
      await loadLifecycleHealth();
    }
    if (scoreReleaseSafetyVisible.value) {
      await loadScoreReleaseSafety();
    }
    if (lifecycleHandoffVisible.value) {
      await loadLifecycleHandoff();
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '强制交卷失败');
  }
}

async function forceSubmitActiveSnapshotCandidates() {
  const rows = snapshotActiveCandidates.value;
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(
      `Force-submit ${rows.length} active attempt(s)? Saved drafts will be submitted and missing answers will be treated as unanswered.`,
      'Force Submit Active',
      {
        type: 'warning',
        confirmButtonText: 'Force Submit',
        cancelButtonText: 'Cancel'
      }
    );
  } catch {
    return;
  }
  snapshotForceSubmitting.value = true;
  const failed: string[] = [];
  try {
    for (const row of rows) {
      const attemptId = Number(row.activeAttemptId || 0);
      if (!Number.isFinite(attemptId) || attemptId <= 0) continue;
      try {
        await forceSubmitAttempt(attemptId);
      } catch (error) {
        failed.push(`${row.realName || row.studentNo || `Attempt #${attemptId}`}: ${error instanceof Error ? error.message : 'failed'}`);
      }
    }
    const succeeded = rows.length - failed.length;
    if (succeeded > 0) {
      ElMessage.success(`Force-submitted ${succeeded} active attempt(s)`);
    }
    if (failed.length > 0) {
      ElMessage.warning(`Failed ${failed.length} attempt(s). ${failed.slice(0, 3).join('; ')}`);
    }
    await refreshAfterSnapshotForceSubmit();
  } finally {
    snapshotForceSubmitting.value = false;
  }
}

async function refreshAfterSnapshotForceSubmit() {
  const examId = snapshot.value?.exam?.id;
  if (examId) {
    snapshot.value = (await getExamSnapshot(examId)).data;
  }
  await loadExams();
  if (lifecycleHealthVisible.value) {
    await loadLifecycleHealth();
  }
  if (scoreReleaseSafetyVisible.value) {
    await loadScoreReleaseSafety();
  }
  if (lifecycleHandoffVisible.value) {
    await loadLifecycleHandoff();
  }
}

function selectPaper(paper: PaperInfo) {
  preflight.value = null;
  form.paperId = paper.id;
  if (!form.examName.trim()) {
    form.examName = `${paper.paperName}考试`;
  }
  if (form.passScore === null && Number(paper.totalScore) > 0) {
    form.passScore = Math.round(Number(paper.totalScore) * 0.6 * 10) / 10;
  }
}

function previousPublishStep() {
  if (publishStep.value === 3) {
    preflight.value = null;
  }
  publishStep.value = Math.max(0, publishStep.value - 1);
}

async function nextPublishStep() {
  if (!validatePublishStep(publishStep.value)) return;
  if (publishStep.value === 2) {
    const ok = await runPreflight();
    if (!ok) return;
  }
  publishStep.value = Math.min(3, publishStep.value + 1);
}

function validatePublishStep(step: number) {
  if (step === 0 && !form.paperId) {
    ElMessage.warning('请选择试卷');
    return false;
  }
  if (step === 1) {
    return validateSchedule();
  }
  if (step === 2) {
    if (form.targetMode === 'CLASS_COURSE' && form.classCourseIds.length === 0) {
      ElMessage.warning('请至少选择一个参考课程班');
      return false;
    }
    if (form.targetMode === 'USER' && form.studentUserIds.length === 0) {
      ElMessage.warning('请至少选择一个参考学生');
      return false;
    }
  }
  return true;
}

function validateSchedule() {
  if (!form.examName.trim()) {
    ElMessage.warning('请填写考试名称');
    return false;
  }
  if (!form.startTime || !form.endTime) {
    ElMessage.warning('请选择开始和结束时间');
    return false;
  }
  if (form.startTime >= form.endTime) {
    ElMessage.warning('开始时间必须早于结束时间');
    return false;
  }
  if (!form.durationMinutes || form.durationMinutes < 1) {
    ElMessage.warning('考试时长必须大于 0');
    return false;
  }
  if (!form.maxAttempts || form.maxAttempts < 1) {
    ElMessage.warning('次数限制必须大于 0');
    return false;
  }
  if (form.passScore !== null && form.passScore < 0) {
    ElMessage.warning('及格线不能小于 0');
    return false;
  }
  if (selectedPaper.value && form.passScore !== null && form.passScore > Number(selectedPaper.value.totalScore)) {
    ElMessage.warning('及格线不能高于试卷总分');
    return false;
  }
  return true;
}

function validateAll() {
  return validatePublishStep(0) && validateSchedule() && validatePublishStep(2);
}

function buildExamPayload(): ExamPayload {
  return {
    paperId: form.paperId as number,
    examName: form.examName.trim(),
    description: form.description.trim(),
    startTime: form.startTime,
    endTime: form.endTime,
    durationMinutes: form.durationMinutes,
    maxAttempts: form.maxAttempts,
    passScore: form.passScore,
    classCourseIds: form.targetMode === 'CLASS_COURSE' ? form.classCourseIds : [],
    studentUserIds: form.targetMode === 'USER' ? form.studentUserIds : []
  };
}

async function runPreflight() {
  if (!validateAll()) return false;
  preflightLoading.value = true;
  try {
    const response = await preflightExam(buildExamPayload());
    preflight.value = response.data;
    if (!response.data.ok) {
      ElMessage.error(response.data.errors?.[0] || '发布预检未通过');
      return false;
    }
    return true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布预检失败');
    return false;
  } finally {
    preflightLoading.value = false;
  }
}

async function submit() {
  if (editingId.value) {
    if (!validateSchedule()) return;
  } else if (!validateAll()) {
    return;
  }

  submitting.value = true;
  try {
    if (editingId.value) {
      const response = await updateExam(editingId.value, {
        examName: form.examName.trim(),
        description: form.description.trim(),
        startTime: form.startTime,
        endTime: form.endTime,
        durationMinutes: form.durationMinutes,
        maxAttempts: form.maxAttempts,
        passScore: form.passScore
      });
      rememberExamOperationAudit('Update exam', [response.data.operationLogId]);
      ElMessage.success('考试已更新');
    } else {
      if (!preflight.value?.ok) {
        const ok = await runPreflight();
        if (!ok) return;
      }
      const response = await createExam(buildExamPayload());
      rememberExamOperationAudit('Create exam', [response.data.operationLogId]);
      ElMessage.success(props.role === 'ADMIN'
        ? publishNotificationSummary(response.data)
        : '考试发布申请已提交，等待管理员审批');
    }
    dialogVisible.value = false;
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function batchDelete() {
  const rows = selectedExams.value.filter((row) => canDeleteExam(row));
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${rows.length} 场考试吗？未开始的答卷会一并移除，该操作不可恢复。`, '批量删除', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    const responses = await Promise.all(rows.map((row) => deleteExam(row.id)));
    rememberExamOperationAudit('Batch delete exams', responses.map((response) => response.data.operationLogId));
    ElMessage.success(`已删除 ${rows.length} 场考试`);
    clearSelection();
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败');
  }
}

async function close(row: ExamInfo) {
  if (!canCloseExam(row)) {
    ElMessage.warning('该考试已关闭');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认提前结束考试「${row.examName}」吗？结束后学生将无法再进入答题。`, '提前结束', {
      type: 'warning',
      confirmButtonText: '确认结束',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    const response = await closeExam(row.id);
    rememberExamOperationAudit('Close exam', [response.data.operationLogId]);
    ElMessage.success('考试已结束');
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败');
  }
}

async function remove(row: ExamInfo) {
  if (!canDeleteExam(row)) {
    ElMessage.warning('考试已开始或已有学生进入，不能删除');
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除考试「${row.examName}」吗？未开始的答卷会一并移除。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    const response = await deleteExam(row.id);
    rememberExamOperationAudit('Delete exam', [response.data.operationLogId]);
    ElMessage.success('考试已删除');
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败');
  }
}

async function approve(row: ExamInfo) {
  let note = '';
  try {
    const result = await ElMessageBox.prompt(`确认批准「${row.examName}」并正式发布给学生吗？`, '批准发布', {
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
    ElMessage.success(publishNotificationSummary(response.data));
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批准失败');
  }
}

async function reject(row: ExamInfo) {
  let note = '';
  try {
    const result = await ElMessageBox.prompt(`请输入「${row.examName}」的驳回原因。`, '驳回发布', {
      type: 'warning',
      inputType: 'textarea',
      inputPlaceholder: '例如：考试时间与课程安排冲突，请调整后重新提交',
      inputValidator: (value) => Boolean(String(value || '').trim()) || '请填写驳回原因',
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消'
    });
    note = String(result.value || '').trim();
  } catch {
    return;
  }
  try {
    await rejectExam(row.id, { note });
    ElMessage.success('考试发布申请已驳回');
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '驳回失败');
  }
}

function isScoreReleaseReady(readiness: ScoreReleaseReadiness) {
  return Number(readiness.scoreReleaseReady ?? readiness.ready ?? 0) === 1;
}

function scoreReleaseReadinessBlockers(readiness: ScoreReleaseReadiness) {
  if (readiness.blockerDetails && readiness.blockerDetails.length > 0) {
    return readiness.blockerDetails.map((detail) => {
      const countText = detail.count !== undefined ? ` (${detail.count})` : '';
      const actionText = detail.action ? ` Next: ${detail.action}` : '';
      return `${detail.message || scoreReleaseBlockerText(detail.code)}${countText}.${actionText}`;
    });
  }
  const blockers = readiness.blockers && readiness.blockers.length > 0
    ? readiness.blockers
    : String(readiness.scoreReleaseBlockers || '')
      .split(',')
      .map((item) => item.trim())
      .filter(Boolean);
  return blockers.map((blocker) => scoreReleaseBlockerText(blocker));
}

function scoreReleaseReadinessSummary(readiness: ScoreReleaseReadiness) {
  const scored = Number(readiness.scoredCompletedAttemptCount ?? readiness.completedAttemptCount ?? 0);
  const total = Number(readiness.attemptCount ?? 0);
  return `Ready to publish ${scored} scored attempts from ${total} total attempts.`;
}

function applyScoreReleaseReadiness(row: ExamInfo, readiness: ScoreReleaseReadiness) {
  row.scoreReleaseReady = readiness.scoreReleaseReady ?? readiness.ready ?? 0;
  row.scoreReleaseBlockers = readiness.scoreReleaseBlockers ?? (readiness.blockers || []).join(',');
  row.scoreReleaseStatus = readiness.scoreReleaseStatus ?? row.scoreReleaseStatus;
  row.completedAttemptCount = readiness.completedAttemptCount ?? row.completedAttemptCount;
  row.unscoredCompletedAttemptCount = readiness.unscoredCompletedAttemptCount ?? row.unscoredCompletedAttemptCount;
  row.pendingReviewAttemptCount = readiness.pendingReviewAttemptCount ?? row.pendingReviewAttemptCount;
  row.pendingAnswerReviewCount = readiness.pendingAnswerReviewCount ?? row.pendingAnswerReviewCount;
  row.activeAttemptCount = readiness.activeAttemptCount ?? row.activeAttemptCount;
  row.nonFinalStartedAttemptCount = readiness.nonFinalStartedAttemptCount ?? row.nonFinalStartedAttemptCount;
  row.pendingScoreAppealCount = readiness.pendingScoreAppealCount ?? row.pendingScoreAppealCount;
  row.openRecheckAppealCount = readiness.openRecheckAppealCount ?? row.openRecheckAppealCount;
}

function showScoreReleaseReadiness(row: ExamInfo, readiness: ScoreReleaseReadiness) {
  scoreReleaseReadinessExam.value = row;
  scoreReleaseReadiness.value = readiness;
  scoreReleaseReadinessVisible.value = true;
}

async function openScoreReleaseReadiness(row: ExamInfo) {
  scoreReleaseReadinessExam.value = row;
  scoreReleaseReadiness.value = null;
  scoreReleaseReadinessVisible.value = true;
  try {
    scoreReleaseReadinessLoading.value = row.id;
    const readiness = (await getScoreReleaseReadiness(row.id)).data;
    applyScoreReleaseReadiness(row, readiness);
    showScoreReleaseReadiness(row, readiness);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Score release readiness check failed');
  } finally {
    scoreReleaseReadinessLoading.value = null;
  }
}

async function publishScores(row: ExamInfo) {
  let readiness: ScoreReleaseReadiness | null = null;
  try {
    scoreReleaseReadinessLoading.value = row.id;
    readiness = (await getScoreReleaseReadiness(row.id)).data;
    applyScoreReleaseReadiness(row, readiness);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Score release readiness check failed');
    return;
  } finally {
    scoreReleaseReadinessLoading.value = null;
  }

  if (!readiness) return;
  if (!isScoreReleaseReady(readiness)) {
    showScoreReleaseReadiness(row, readiness);
    ElMessage.warning('Scores are not ready to publish. Review the blocker details.');
    return;
  }

  try {
    await ElMessageBox.confirm(scoreReleaseReadinessSummary(readiness), 'Publish scores', {
      type: 'warning',
      confirmButtonText: 'Publish',
      cancelButtonText: 'Cancel'
    });
  } catch {
    return;
  }

  try {
    scoreReleaseReadinessLoading.value = row.id;
    const response = await publishExamScores(row.id);
    const completed = response.data.completedAttempts ?? 0;
    const notified = response.data.notifiedStudents ?? 0;
    rememberScoreReleaseAudit('Publish scores', [response.data.scoreReleaseLogId]);
    ElMessage.success(`Scores published: ${completed} attempts, ${notified} students notified${scoreReleaseLogSuffix(response.data.scoreReleaseLogId)}`);
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Score publish failed');
  } finally {
    scoreReleaseReadinessLoading.value = null;
  }
}

async function publishScoresFromLifecycleHealth(row: ExamLifecycleHealthExam) {
  await publishScores(row as ExamInfo);
  if (lifecycleHealthVisible.value) {
    await loadLifecycleHealth();
  }
  if (lifecycleHandoffVisible.value) {
    await loadLifecycleHandoff();
  }
}

async function publishReadyLifecycleRows() {
  const rows = lifecycleHealthPublishableRows.value;
  if (rows.length === 0) {
    ElMessage.warning('No ready score releases on the current lifecycle page');
    return;
  }
  try {
    await ElMessageBox.confirm(
      `Publish scores for ${rows.length} ready exam(s) on the current lifecycle page? Backend release guards will still validate every exam.`,
      'Publish Ready Scores',
      {
        type: 'warning',
        confirmButtonText: 'Publish',
        cancelButtonText: 'Cancel'
      }
    );
  } catch {
    return;
  }

  lifecycleHealthPublishing.value = true;
  const published: Array<{ examName: string; scoreReleaseLogId?: number | null }> = [];
  const failed: Array<{ examName: string; message: string }> = [];
  let completedAttempts = 0;
  let notifiedStudents = 0;
  try {
    for (const row of rows) {
      const examId = Number(row.id || row.examId || 0);
      if (!Number.isFinite(examId) || examId <= 0) {
        failed.push({ examName: row.examName || 'Unknown exam', message: 'Missing exam id' });
        continue;
      }
      try {
        const response = await publishExamScores(examId);
        completedAttempts += Number(response.data.completedAttempts || 0);
        notifiedStudents += Number(response.data.notifiedStudents || 0);
        published.push({
          examName: row.examName || `Exam #${examId}`,
          scoreReleaseLogId: response.data.scoreReleaseLogId
        });
      } catch (error) {
        failed.push({
          examName: row.examName || `Exam #${examId}`,
          message: error instanceof Error ? error.message : 'Publish failed'
        });
      }
    }
    rememberScoreReleaseAudit('Publish ready lifecycle scores', published.map((item) => item.scoreReleaseLogId));
    if (published.length > 0) {
      ElMessage.success(`Published ${published.length} exam(s): ${completedAttempts} attempts, ${notifiedStudents} students notified`);
    }
    if (failed.length > 0) {
      const preview = failed.slice(0, 3).map((item) => `${item.examName}: ${item.message}`).join('; ');
      ElMessage.warning(`Failed ${failed.length} exam(s). ${preview}`);
    }
    await loadExams();
    await loadLifecycleHealth();
    if (scoreReleaseSafetyVisible.value) {
      await loadScoreReleaseSafety();
    }
    if (lifecycleHandoffVisible.value) {
      await loadLifecycleHandoff();
    }
  } finally {
    lifecycleHealthPublishing.value = false;
  }
}

async function publishScoresFromSafety(row: ScoreReleaseSafetyExam) {
  await publishScores(row);
  if (scoreReleaseSafetyVisible.value) {
    await loadScoreReleaseSafety();
  }
  if (lifecycleHealthVisible.value) {
    await loadLifecycleHealth();
  }
}

async function publishScoresLegacy(row: ExamInfo) {
  try {
    await ElMessageBox.confirm(`确认发布「${row.examName}」的成绩吗？发布后学生可查看成绩和答题详情。`, '发布成绩', {
      type: 'warning',
      confirmButtonText: '确认发布',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    const response = await publishExamScores(row.id);
    const completed = response.data.completedAttempts ?? 0;
    const notified = response.data.notifiedStudents ?? 0;
    rememberScoreReleaseAudit('Publish scores', [response.data.scoreReleaseLogId]);
    ElMessage.success(`成绩已发布：${completed} 份答卷，已通知 ${notified} 名学生${scoreReleaseLogSuffix(response.data.scoreReleaseLogId)}`);
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布失败');
  }
}

async function revokeScores(row: ExamInfo) {
  let reason = '';
  try {
    const result = await ElMessageBox.prompt(`请输入撤回「${row.examName}」成绩的原因。撤回后学生端将不可见。`, '撤回成绩', {
      type: 'warning',
      inputType: 'textarea',
      inputPlaceholder: '例如：发现评分规则需要复核，成绩将重新发布',
      inputValidator: (value) => Boolean(String(value || '').trim()) || '请填写撤回原因',
      confirmButtonText: '确认撤回',
      cancelButtonText: '取消'
    });
    reason = String(result.value || '').trim();
  } catch {
    return;
  }
  try {
    const response = await revokeExamScores(row.id, { reason });
    const affected = response.data.visibleAttemptsBeforeRevoke ?? 0;
    rememberScoreReleaseAudit('Revoke scores', [response.data.scoreReleaseLogId]);
    ElMessage.success(`成绩已撤回：${affected} 份学生成绩已隐藏${scoreReleaseLogSuffix(response.data.scoreReleaseLogId)}`);
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '撤回失败');
  }
}

async function exportScores(row: ExamInfo) {
  if (!canExportScores(row)) {
    ElMessage.warning(exportScoresDisabledReason(row));
    return;
  }
  try {
    await exportExamScores(row.id, row.examName);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  }
}

function canExportScores(row: ExamInfo) {
  return row.scoreReleaseStatus === 1;
}

function exportScoresDisabledReason(row: ExamInfo) {
  if (hasScoreRevokeAudit(row)) {
    return '成绩已撤回，不能导出成绩单';
  }
  return '成绩未发布，不能导出成绩单';
}

function scoreText(value?: number | null) {
  return value === null || value === undefined ? '—' : `${value} 分`;
}

function phaseText(row: ExamInfo) {
  if (row.status === 0) return '待审批';
  if (row.status === 2) return '已关闭';
  if (row.status === 3) return '已驳回';
  const now = Date.now();
  const start = new Date(row.startTime).getTime();
  const end = new Date(row.endTime).getTime();
  if (Number.isNaN(start) || Number.isNaN(end)) return '已创建';
  if (now < start) return '未开始';
  if (now > end) return '已结束';
  return '进行中';
}

function phaseType(row: ExamInfo) {
  const text = phaseText(row);
  if (text === '进行中') return 'success';
  if (text === '已结束' || text === '已关闭') return 'info';
  if (text === '已驳回') return 'danger';
  return 'warning';
}

function approvalActionText(action: string) {
  const map: Record<string, string> = {
    SUBMIT: '提交审批',
    RESUBMIT: '重新提交',
    APPROVE: '批准发布',
    REJECT: '驳回',
    DIRECT_PUBLISH: '管理员发布'
  };
  return map[action] || action;
}

function approvalPublishStatsText(row: ExamApprovalLog) {
  if (row.action !== 'APPROVE' && row.action !== 'DIRECT_PUBLISH') return '-';
  return `${row.candidateCount ?? 0} 人候选 / ${row.notifiedStudentCount ?? 0} 人通知 / ${row.notifiedAttemptCount ?? 0} 份答卷`;
}

function publishNotificationSummary(row: ExamInfo) {
  return `考试已发布：${row.publishCandidateCount ?? 0} 名候选考生，已通知 ${row.publishNotifiedStudentCount ?? 0} 名学生，生成 ${row.publishNotifiedAttemptCount ?? 0} 份答卷`;
}

function approvalStatusText(status?: number | null) {
  if (status === null || status === undefined) return '-';
  const map: Record<number, string> = {
    0: '待审批',
    1: '已发布',
    2: '已关闭',
    3: '已驳回'
  };
  return map[status] || String(status);
}

function scoreReleaseActionText(action: string) {
  const map: Record<string, string> = {
    PUBLISH: '发布',
    REVOKE: '撤回'
  };
  return map[action] || action;
}

function scoreReleaseStatusText(status?: number | null) {
  if (status === null || status === undefined) return '-';
  const map: Record<number, string> = {
    0: '未发布/已撤回',
    1: '已发布'
  };
  return map[status] || String(status);
}

function scoreReleaseLogSuffix(logId?: number | null) {
  return logId ? `，成绩记录 #${logId}` : '';
}

function rememberScoreReleaseAudit(action: string, ids: Array<number | string | null | undefined>) {
  const scoreReleaseLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (scoreReleaseLogIds.length === 0) return;
  lastScoreReleaseAudit.value = { action, scoreReleaseLogIds };
}

function scoreReleaseAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestScoreReleaseAuditId() {
  const ids = lastScoreReleaseAudit.value?.scoreReleaseLogIds;
  if (!ids?.length) return;
  try {
    await copyScoreReleaseAuditIdToClipboard(ids.join(','));
    ElMessage.success('Score release audit ID copied');
  } catch {
    ElMessage.error('Score release audit ID copy failed');
  }
}

async function copyLatestScoreReleaseAuditLink() {
  const id = lastScoreReleaseAudit.value?.scoreReleaseLogIds[0];
  if (!id) return;
  try {
    await copyScoreReleaseAuditLinkToClipboard(id);
    ElMessage.success('Score release audit link copied');
  } catch {
    ElMessage.error('Score release audit link copy failed');
  }
}

function scoreReleaseText(row: ExamInfo) {
  if (row.scoreReleaseStatus === 1) return '已发布';
  if (hasScoreRevokeAudit(row)) return '已撤回';
  return '未发布';
}

function scoreReleaseType(row: ExamInfo) {
  if (row.scoreReleaseStatus === 1) return 'success';
  if (hasScoreRevokeAudit(row)) return 'warning';
  return 'info';
}

function hasScoreRevokeAudit(row: ExamInfo) {
  return Boolean(row.scoreRevokedAt || row.scoreRevokeReason || row.scoreReleaseNote);
}

function scoreReleaseDetail(row: ExamInfo) {
  if (row.scoreReleaseStatus === 1) {
    const parts = [];
    if (row.scorePublishedAt) parts.push(`发布时间：${formatDateTime(row.scorePublishedAt)}`);
    if (row.scorePublishedByName) parts.push(`发布人：${row.scorePublishedByName}`);
    if (row.scorePublishNote) parts.push(`发布说明：${row.scorePublishNote}`);
    return parts.join('；');
  }
  if (hasScoreRevokeAudit(row)) {
    const parts = [];
    const revokeReason = row.scoreRevokeReason || row.scoreReleaseNote;
    if (row.scoreRevokedAt) parts.push(`撤回时间：${formatDateTime(row.scoreRevokedAt)}`);
    if (row.scoreRevokedByName) parts.push(`撤回人：${row.scoreRevokedByName}`);
    if (revokeReason) parts.push(`撤回原因：${revokeReason}`);
    return parts.join('；');
  }
  return '';
}

function sourceText(value?: string) {
  const map: Record<string, string> = {
    CLASS: '班级',
    CLASS_COURSE: '课程班',
    USER: '指定学生',
    LEGACY: '历史数据'
  };
  return value ? map[value] || value : '-';
}

function questionTypeText(value?: string) {
  const map: Record<string, string> = {
    SINGLE_CHOICE: '单选题',
    MULTIPLE_CHOICE: '多选题',
    TRUE_FALSE: '判断题',
    FILL_BLANK: '填空题',
    SUBJECTIVE: '主观题'
  };
  return value ? map[value] || value : '-';
}

function attemptStatusText(value?: number | null) {
  const map: Record<number, string> = {
    0: '未开始',
    1: '进行中',
    2: '已交卷',
    4: '待批阅',
    5: '已完成'
  };
  return value === null || value === undefined ? '-' : map[value] || String(value);
}
</script>

<style scoped>
.form-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 14px;
}

.form-row.three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.form-hint {
  color: #b45309;
  font-size: 12px;
  margin-top: 6px;
}

.exam-operation-audit,
.score-release-audit {
  margin-bottom: 14px;
}

.exam-operation-audit-content,
.score-release-audit-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.readiness-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.readiness-head > div {
  display: grid;
  gap: 4px;
}

.readiness-head strong {
  color: #111827;
  font-size: 16px;
}

.readiness-head span {
  color: #64748b;
  font-size: 13px;
}

.readiness-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.readiness-stat {
  min-height: 66px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.readiness-stat span {
  color: #64748b;
  font-size: 12px;
}

.readiness-stat strong {
  color: #111827;
  font-size: 18px;
}

.readiness-alert {
  margin-bottom: 14px;
}

.readiness-blocker-detail {
  display: grid;
  gap: 4px;
}

.readiness-blocker-detail strong {
  color: #111827;
}

.readiness-blocker-detail span {
  color: #64748b;
  font-size: 13px;
}

.score-safety-toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 14px;
}

.score-safety-toolbar .el-input {
  width: min(320px, 100%);
}

.score-safety-toolbar .el-select {
  width: 170px;
}

.score-safety-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(126px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.score-safety-stat {
  min-height: 64px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.score-safety-stat span {
  color: #64748b;
  font-size: 12px;
}

.score-safety-stat strong {
  color: #111827;
  font-size: 18px;
}

.score-safety-blockers {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
  color: #64748b;
  font-size: 13px;
}

.mp-table-gap {
  margin-top: 14px;
}

.score-safety-exam-cell,
.score-safety-counts {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.score-safety-exam-cell strong {
  min-width: 0;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-safety-exam-cell span,
.score-safety-counts span {
  color: #64748b;
  font-size: 12px;
}

.score-safety-counts strong {
  color: #111827;
}

.score-safety-blocker-list {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.score-safety-more {
  color: #64748b;
  font-size: 12px;
}

.publish-steps {
  margin: 2px 0 18px;
}

.publish-panel,
.edit-panel {
  min-height: 420px;
}

.paper-step {
  display: grid;
  grid-template-columns: minmax(0, 1.25fr) minmax(280px, 0.75fr);
  gap: 18px;
}

.paper-picker {
  display: grid;
  gap: 12px;
}

.paper-scroll {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.paper-option {
  width: 100%;
  min-height: 72px;
  display: grid;
  gap: 6px;
  padding: 13px 14px;
  border: 0;
  border-bottom: 1px solid #edf2f7;
  background: #ffffff;
  color: #1f2937;
  text-align: left;
  cursor: pointer;
}

.paper-option:hover,
.paper-option.active {
  background: #eff6ff;
}

.paper-option.active {
  box-shadow: inset 3px 0 0 #2563eb;
}

.paper-option strong,
.paper-option span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.paper-option span {
  color: #64748b;
  font-size: 13px;
}

.paper-preview {
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 18px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fafc;
  color: #1f2937;
}

.preview-label {
  color: #64748b;
  font-size: 13px;
}

.paper-preview h3,
.paper-preview p {
  margin: 0;
}

.paper-preview p {
  color: #475569;
  line-height: 1.7;
}

.preview-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-stats span {
  padding: 4px 8px;
  border-radius: 6px;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
}

.target-step {
  display: grid;
  align-content: start;
  gap: 18px;
}

.target-mode {
  width: max-content;
}

.target-summary {
  min-height: 86px;
  display: flex;
  flex-wrap: wrap;
  align-content: flex-start;
  gap: 8px;
  padding: 14px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
}

.target-summary > span:first-child {
  width: 100%;
  color: #334155;
  font-weight: 600;
}

.confirm-step {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.confirm-block {
  display: grid;
  gap: 8px;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.confirm-block h4,
.confirm-block p,
.confirm-block span {
  margin: 0;
}

.confirm-block h4 {
  color: #64748b;
  font-size: 13px;
}

.confirm-block p {
  color: #111827;
  font-weight: 700;
}

.confirm-block span {
  color: #475569;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.preflight-block {
  grid-column: 1 / -1;
}

.preflight-warning {
  margin-top: 4px;
}

.edit-tip {
  margin-bottom: 14px;
}

.exam-snapshot-cell {
  display: grid;
  gap: 2px;
  color: #475569;
  font-size: 12px;
  line-height: 1.35;
}

.mp-table-card :deep(.exam-management-row-focused td) {
  background: #fffbeb !important;
}

.snapshot-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.snapshot-summary > div {
  display: grid;
  gap: 6px;
  min-height: 74px;
  padding: 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.snapshot-summary span {
  color: #64748b;
  font-size: 13px;
}

.snapshot-summary strong {
  min-width: 0;
  color: #111827;
  font-size: 18px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.snapshot-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  color: #64748b;
  font-size: 13px;
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
.score-release-log-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.lifecycle-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.lifecycle-head > div {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.lifecycle-head strong {
  min-width: 0;
  color: #111827;
  font-size: 16px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lifecycle-head span {
  color: #64748b;
  font-size: 13px;
}

.lifecycle-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(118px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.lifecycle-stat {
  min-height: 64px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.lifecycle-stat span {
  color: #64748b;
  font-size: 12px;
}

.lifecycle-stat strong {
  color: #111827;
  font-size: 18px;
}

.lifecycle-alert {
  margin-bottom: 14px;
}

.lifecycle-timeline {
  padding: 4px 4px 4px 0;
}

.lifecycle-event {
  display: grid;
  gap: 8px;
}

.lifecycle-event-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.lifecycle-event-title strong {
  color: #111827;
}

.lifecycle-event-meta,
.lifecycle-event-details {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.lifecycle-event-meta {
  color: #64748b;
  font-size: 12px;
}

.lifecycle-event p {
  margin: 0;
  color: #374151;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

:deep(.exam-publish-dialog .el-dialog__body) {
  max-height: min(76vh, 760px);
  overflow: auto;
}

@media (max-width: 760px) {
  .form-row,
  .form-row.three,
  .paper-step,
  .confirm-step,
  .snapshot-summary {
    grid-template-columns: 1fr;
  }

  .target-mode {
    width: 100%;
  }

  :deep(.target-mode .el-radio-button) {
    width: 50%;
  }

  :deep(.target-mode .el-radio-button__inner) {
    width: 100%;
  }
}
</style>
