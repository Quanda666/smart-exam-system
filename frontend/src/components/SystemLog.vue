<template>
  <section class="system-log">
    <div class="log-topbar">
      <div>
        <h3>系统日志</h3>
        <p>{{ logSubtitle }}</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="log-workbench">
      <el-tabs v-model="activeTab" class="log-tabs">
        <el-tab-pane label="登录审计" name="login">
          <div class="login-log-toolbar">
            <el-input v-model="loginQuery.logId" placeholder="登录日志 ID" clearable @blur="normalizeLoginLogIdField" @keyup.enter="searchLoginLogs" />
            <el-input v-model="loginQuery.keyword" placeholder="账号、安全操作、详情或 IP" clearable @keyup.enter="searchLoginLogs" />
            <el-input v-model="loginQuery.action" placeholder="动作" clearable @keyup.enter="searchLoginLogs" />
            <el-input v-model="loginQuery.operatorId" placeholder="操作人 ID" clearable @keyup.enter="searchLoginLogs" />
            <el-select v-model="loginQuery.success" placeholder="结果" clearable>
              <el-option label="成功" :value="true" />
              <el-option label="失败" :value="false" />
            </el-select>
            <el-date-picker
              v-model="loginDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchLoginLogs">查询</el-button>
            <el-button @click="resetLoginFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="loginExporting" @click="exportLoginLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="loginLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="125">
              <template #default="scope">
                <div class="login-log-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制登录审计 ID"
                    aria-label="复制登录审计 ID"
                    @click="copyLoginAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制登录审计链接"
                    aria-label="复制登录审计链接"
                    @click="copyLoginAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="结果" min-width="85">
              <template #default="scope">
                <el-tag size="small" :type="loginSuccess(scope.row.success) ? 'success' : 'danger'">
                  {{ loginSuccess(scope.row.success) ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="账号/用户" min-width="140">
              <template #default="scope">{{ scope.row.operatorName || scope.row.operatorId || '-' }}</template>
            </el-table-column>
            <el-table-column prop="action" label="动作" min-width="140" />
            <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
            <el-table-column prop="ip" label="IP" min-width="130" />
            <template #empty>
              <el-empty description="暂无登录审计日志" />
            </template>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="操作日志" name="operation">
          <div class="operation-log-toolbar">
            <el-input v-model="operationQuery.logId" placeholder="操作日志 ID" clearable @blur="normalizeOperationLogIdField" @keyup.enter="searchOperationLogs" />
            <el-input v-model="operationQuery.keyword" placeholder="操作人、动作、对象、详情或 IP" clearable @keyup.enter="searchOperationLogs" />
            <el-input v-model="operationQuery.action" placeholder="动作" clearable @keyup.enter="searchOperationLogs" />
            <el-input v-model="operationQuery.target" placeholder="对象" clearable @keyup.enter="searchOperationLogs" />
            <el-date-picker
              v-model="operationDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchOperationLogs">查询</el-button>
            <el-button @click="resetOperationFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="operationExporting" @click="exportOperationLogRows">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="operationLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="125">
              <template #default="scope">
                <div class="operation-log-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制操作日志 ID"
                    aria-label="复制操作日志 ID"
                    @click="copyOperationLogId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制操作日志链接"
                    aria-label="复制操作日志链接"
                    @click="copyOperationLogLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="created_at" label="时间" min-width="155" />
            <el-table-column label="操作人" min-width="110">
              <template #default="scope">{{ scope.row.operator_name || '-' }}</template>
            </el-table-column>
            <el-table-column label="动作" min-width="130">
              <template #default="scope">
                <el-tag size="small" type="info">{{ scope.row.action || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="对象" min-width="140">
              <template #default="scope">{{ scope.row.target || '-' }}</template>
            </el-table-column>
            <el-table-column label="详情" min-width="220" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.detail || '-' }}</template>
            </el-table-column>
            <el-table-column label="IP" min-width="130">
              <template #default="scope">{{ scope.row.ip || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无操作日志" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="AI 调用日志" name="ai">
          <div class="ai-log-toolbar">
            <el-input v-model="aiQuery.keyword" placeholder="用户、场景、提示词、响应或错误" clearable @keyup.enter="searchAiLogs" />
            <el-select v-model="aiQuery.scene" placeholder="场景" clearable>
              <el-option v-for="item in sceneOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="aiQuery.success" placeholder="结果" clearable>
              <el-option label="成功" :value="true" />
              <el-option label="失败" :value="false" />
            </el-select>
            <el-date-picker
              v-model="aiDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchAiLogs">查询</el-button>
            <el-button @click="resetAiFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="aiExporting" @click="exportAiLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="aiLogs" border max-height="620">
            <el-table-column prop="createdAt" label="时间" min-width="155" />
            <el-table-column label="用户" min-width="110">
              <template #default="scope">{{ scope.row.userName || scope.row.userId || '-' }}</template>
            </el-table-column>
            <el-table-column label="场景" min-width="140">
              <template #default="scope">
                <el-tag size="small">{{ sceneText(scope.row.scene) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果" min-width="80">
              <template #default="scope">
                <el-tag size="small" :type="isSuccess(scope.row.success) ? 'success' : 'danger'">
                  {{ isSuccess(scope.row.success) ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="提示词" min-width="220" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.prompt || '-' }}</template>
            </el-table-column>
            <el-table-column label="响应" min-width="220" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.response || '-' }}</template>
            </el-table-column>
            <el-table-column label="错误" min-width="160" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.errorMessage || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无 AI 调用日志" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="考试审批提醒审计" name="approvalReminder">
          <div class="approval-reminder-toolbar">
            <el-input v-model="approvalReminderQuery.logId" placeholder="提醒日志 ID" clearable @blur="normalizeApprovalReminderLogIdField" @keyup.enter="searchApprovalReminderLogs" />
            <el-input v-model="approvalReminderQuery.keyword" placeholder="触发人、状态、来源、节点或消息" clearable @keyup.enter="searchApprovalReminderLogs" />
            <el-select v-model="approvalReminderQuery.status" placeholder="状态" clearable>
              <el-option label="已发送" value="SENT" />
              <el-option label="已禁用" value="SKIPPED_DISABLED" />
              <el-option label="无超期" value="SKIPPED_EMPTY" />
              <el-option label="无接收人" value="SKIPPED_NO_RECIPIENT" />
              <el-option label="冷却期" value="SKIPPED_COOLDOWN" />
              <el-option label="计划已禁用" value="SKIPPED_SCHEDULE_DISABLED" />
              <el-option label="等待间隔" value="SKIPPED_SCHEDULE_INTERVAL" />
            </el-select>
            <el-select v-model="approvalReminderQuery.triggerSource" placeholder="来源" clearable>
              <el-option label="手动" value="MANUAL" />
              <el-option label="计划任务" value="SCHEDULE" />
            </el-select>
            <el-date-picker
              v-model="approvalReminderDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchApprovalReminderLogs">查询</el-button>
            <el-button @click="resetApprovalReminderFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="approvalReminderExporting" @click="exportApprovalReminderLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="approvalReminderLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="135">
              <template #default="scope">
                <div class="approval-reminder-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制审批提醒审计 ID"
                    aria-label="复制审批提醒审计 ID"
                    @click="copyApprovalReminderAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制审批提醒审计链接"
                    aria-label="复制审批提醒审计链接"
                    @click="copyApprovalReminderAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" min-width="130">
              <template #default="scope">
                <el-tag size="small" :type="approvalReminderStatusTag(scope.row.status)">
                  {{ approvalReminderStatusText(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="来源" min-width="95">
              <template #default="scope">{{ approvalReminderSourceText(scope.row.triggerSource) }}</template>
            </el-table-column>
            <el-table-column label="触发人" min-width="130">
              <template #default="scope">{{ scope.row.triggeredByName || scope.row.triggeredBy || '-' }}</template>
            </el-table-column>
            <el-table-column label="超期 / 接收人" min-width="140">
              <template #default="scope">{{ scope.row.overdueExamCount || 0 }} / {{ scope.row.recipientCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="阈值" min-width="120">
              <template #default="scope">{{ scope.row.overdueHours }}h / {{ scope.row.cooldownHours }}h</template>
            </el-table-column>
            <el-table-column label="节点 / 耗时" min-width="150" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.nodeId || '-' }} / {{ durationText(scope.row.durationMs) }}</template>
            </el-table-column>
            <el-table-column prop="message" label="消息" min-width="180" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.message || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无审批提醒审计日志" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="考试审批审计" name="examApproval">
          <div class="exam-approval-toolbar">
            <el-input v-model="examApprovalQuery.logId" placeholder="审批日志 ID" clearable @blur="normalizeExamApprovalLogIdField" @keyup.enter="searchExamApprovalLogs" />
            <el-input v-model="examApprovalQuery.keyword" placeholder="考试、试卷、操作人或备注" clearable @keyup.enter="searchExamApprovalLogs" />
            <el-select v-model="examApprovalQuery.action" placeholder="动作" clearable>
              <el-option label="提交" value="SUBMIT" />
              <el-option label="重新提交" value="RESUBMIT" />
              <el-option label="通过" value="APPROVE" />
              <el-option label="驳回" value="REJECT" />
              <el-option label="直接发布" value="DIRECT_PUBLISH" />
            </el-select>
            <el-date-picker
              v-model="examApprovalDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchExamApprovalLogs">查询</el-button>
            <el-button @click="resetExamApprovalFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="examApprovalExporting" @click="exportExamApprovalLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="examApprovalLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="135">
              <template #default="scope">
                <div class="exam-approval-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制考试审批审计 ID"
                    aria-label="复制考试审批审计 ID"
                    @click="copyExamApprovalAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制考试审批审计链接"
                    aria-label="复制考试审批审计链接"
                    @click="copyExamApprovalAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="动作" min-width="110">
              <template #default="scope">
                <el-tag size="small" :type="examApprovalActionTag(scope.row.action)">
                  {{ examApprovalActionText(scope.row.action) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="examName" label="考试" min-width="150" show-overflow-tooltip />
            <el-table-column prop="paperName" label="试卷" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" min-width="140">
              <template #default="scope">
                {{ examApprovalStatusText(scope.row.statusFrom) }} -> {{ examApprovalStatusText(scope.row.statusTo) }}
              </template>
            </el-table-column>
            <el-table-column label="操作人" min-width="110">
              <template #default="scope">{{ scope.row.actorName || scope.row.actorId || '-' }}</template>
            </el-table-column>
            <el-table-column label="发布影响" min-width="190">
              <template #default="scope">
                {{ scope.row.candidateCount || 0 }} 名考生 / {{ scope.row.notifiedStudentCount || 0 }} 名学生 / {{ scope.row.notifiedAttemptCount || 0 }} 次答卷
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" min-width="180" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.note || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无考试审批审计日志" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="成绩发布审计" name="scoreRelease">
          <div class="score-release-toolbar">
            <el-input v-model="scoreReleaseQuery.logId" placeholder="成绩发布日志 ID" clearable @blur="normalizeScoreReleaseLogIdField" @keyup.enter="searchScoreReleaseLogs" />
            <el-input v-model="scoreReleaseQuery.keyword" placeholder="考试、试卷或处理人" clearable @keyup.enter="searchScoreReleaseLogs" />
            <el-select v-model="scoreReleaseQuery.action" placeholder="动作" clearable>
              <el-option label="发布" value="PUBLISH" />
              <el-option label="撤回" value="REVOKE" />
            </el-select>
            <el-date-picker
              v-model="scoreReleaseDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchScoreReleaseLogs">查询</el-button>
            <el-button @click="resetScoreReleaseFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="scoreReleaseExporting" @click="exportScoreReleaseLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="scoreReleaseLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="135">
              <template #default="scope">
                <div class="score-release-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制成绩发布审计 ID"
                    aria-label="复制成绩发布审计 ID"
                    @click="copyScoreReleaseAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制成绩发布审计链接"
                    aria-label="复制成绩发布审计链接"
                    @click="copyScoreReleaseAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="动作" min-width="85">
              <template #default="scope">
                <el-tag size="small" :type="scope.row.action === 'REVOKE' ? 'warning' : 'success'">
                  {{ scoreReleaseActionText(scope.row.action) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="examName" label="考试" min-width="150" show-overflow-tooltip />
            <el-table-column prop="paperName" label="试卷" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态流转" min-width="130">
              <template #default="scope">
                {{ scoreReleaseStatusText(scope.row.statusFrom) }} -> {{ scoreReleaseStatusText(scope.row.statusTo) }}
              </template>
            </el-table-column>
            <el-table-column label="处理人" min-width="110">
              <template #default="scope">{{ scope.row.actorName || scope.row.actorId || '-' }}</template>
            </el-table-column>
            <el-table-column label="影响答卷" min-width="95">
              <template #default="scope">{{ scope.row.visibleAttemptCount || 0 }}</template>
            </el-table-column>
            <el-table-column label="通知" min-width="115">
              <template #default="scope">{{ scope.row.notifiedStudentCount || 0 }} 人 / {{ scope.row.notifiedAttemptCount || 0 }} 份</template>
            </el-table-column>
            <el-table-column prop="note" label="说明/原因" min-width="180" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.note || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无成绩发布审计记录" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="成绩申诉审计" name="scoreAppeal">
          <div class="score-appeal-toolbar">
            <el-input v-model="scoreAppealQuery.logId" placeholder="成绩申诉日志 ID" clearable @blur="normalizeScoreAppealLogIdField" @keyup.enter="searchScoreAppealLogs" />
            <el-input v-model="scoreAppealQuery.keyword" placeholder="考试、学生、处理人或说明" clearable @keyup.enter="searchScoreAppealLogs" />
            <el-select v-model="scoreAppealQuery.action" placeholder="动作" clearable>
              <el-option label="提交申诉" value="SUBMIT" />
              <el-option label="处理回复" value="REPLY" />
              <el-option label="重开复核" value="RECHECK_OPEN" />
              <el-option label="完成复核" value="CLOSE_RECHECK" />
            </el-select>
            <el-select v-model="scoreAppealQuery.handlingResult" placeholder="处理结果" clearable>
              <el-option label="维持原分" value="MAINTAINED" />
              <el-option label="需要复核" value="RECHECK_REQUIRED" />
              <el-option label="已线下调整" value="ADJUSTED_OFFLINE" />
            </el-select>
            <el-date-picker
              v-model="scoreAppealDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchScoreAppealLogs">查询</el-button>
            <el-button @click="resetScoreAppealFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="scoreAppealExporting" @click="exportScoreAppealLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="scoreAppealLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="135">
              <template #default="scope">
                <div class="score-appeal-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制成绩申诉审计 ID"
                    aria-label="复制成绩申诉审计 ID"
                    @click="copyScoreAppealAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制成绩申诉审计链接"
                    aria-label="复制成绩申诉审计链接"
                    @click="copyScoreAppealAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="动作" min-width="95">
              <template #default="scope">
                <el-tag size="small" :type="scoreAppealActionTag(scope.row.action)">
                  {{ scoreAppealActionText(scope.row.action) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="examName" label="考试" min-width="150" show-overflow-tooltip />
            <el-table-column label="学生" min-width="110">
              <template #default="scope">{{ scope.row.studentName || scope.row.userId || '-' }}</template>
            </el-table-column>
            <el-table-column label="申诉对象" min-width="150" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.questionStem || '整张试卷' }}</template>
            </el-table-column>
            <el-table-column label="状态流转" min-width="115">
              <template #default="scope">
                {{ scoreAppealStatusText(scope.row.statusFrom) }} -> {{ scoreAppealStatusText(scope.row.statusTo) }}
              </template>
            </el-table-column>
            <el-table-column label="处理结果" min-width="105">
              <template #default="scope">{{ scoreAppealHandlingResultText(scope.row.handlingResult) }}</template>
            </el-table-column>
            <el-table-column label="操作人" min-width="110">
              <template #default="scope">{{ scope.row.actorName || scope.row.actorId || '-' }}</template>
            </el-table-column>
            <el-table-column prop="note" label="说明" min-width="180" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.note || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无成绩申诉审计记录" />
            </template>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="阅卷评分审计" name="reviewScore">
          <div class="review-score-toolbar">
            <el-input v-model="reviewScoreQuery.logId" placeholder="评分日志 ID" clearable @blur="normalizeReviewScoreLogIdField" @keyup.enter="searchReviewScoreLogs" />
            <el-input v-model="reviewScoreQuery.keyword" placeholder="考试、学生、阅卷人、题目或评语" clearable @keyup.enter="searchReviewScoreLogs" />
            <el-input v-model="reviewScoreQuery.examId" placeholder="考试 ID" clearable @keyup.enter="searchReviewScoreLogs" />
            <el-input v-model="reviewScoreQuery.reviewerId" placeholder="阅卷人 ID" clearable @keyup.enter="searchReviewScoreLogs" />
            <el-date-picker
              v-model="reviewScoreDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchReviewScoreLogs">查询</el-button>
            <el-button @click="resetReviewScoreFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="reviewScoreExporting" @click="exportReviewScoreLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="reviewScoreLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="135">
              <template #default="scope">
                <div class="review-score-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制阅卷评分审计 ID"
                    aria-label="复制阅卷评分审计 ID"
                    @click="copyReviewScoreAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制阅卷评分审计链接"
                    aria-label="复制阅卷评分审计链接"
                    @click="copyReviewScoreAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="examName" label="考试" min-width="150" show-overflow-tooltip />
            <el-table-column label="学生" min-width="120">
              <template #default="scope">{{ scope.row.studentName || scope.row.userId || '-' }}</template>
            </el-table-column>
            <el-table-column prop="studentNo" label="学号" min-width="105" />
            <el-table-column label="分数" min-width="130">
              <template #default="scope">
                {{ scope.row.oldScore ?? 0 }} -> {{ scope.row.newScore }} / {{ scope.row.maxScore }}
              </template>
            </el-table-column>
            <el-table-column label="阅卷人" min-width="120">
              <template #default="scope">{{ scope.row.reviewerName || scope.row.reviewerId || '-' }}</template>
            </el-table-column>
            <el-table-column prop="questionStem" label="题目" min-width="180" show-overflow-tooltip />
            <el-table-column prop="comment" label="评语" min-width="160" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.comment || '-' }}</template>
            </el-table-column>
            <el-table-column label="ID" min-width="150">
              <template #default="scope">
                A{{ scope.row.attemptId }} / R{{ scope.row.answerRecordId }} / Q{{ scope.row.questionId }}
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无阅卷评分审计记录" />
            </template>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="题目审核审计" name="questionReview">
          <div class="question-review-toolbar">
            <el-input v-model="questionReviewQuery.logId" placeholder="题目审核日志 ID" clearable @blur="normalizeQuestionReviewLogIdField" @keyup.enter="searchQuestionReviewLogs" />
            <el-input v-model="questionReviewQuery.questionId" placeholder="题目 ID" clearable @keyup.enter="searchQuestionReviewLogs" />
            <el-input v-model="questionReviewQuery.keyword" placeholder="题目、学科、操作人或评语" clearable @keyup.enter="searchQuestionReviewLogs" />
            <el-select v-model="questionReviewQuery.actionType" placeholder="动作" clearable>
              <el-option v-for="item in questionReviewActionOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="questionReviewQuery.reviewStatus" placeholder="审核状态" clearable>
              <el-option v-for="item in questionReviewStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-input v-model="questionReviewQuery.subjectId" placeholder="学科 ID" clearable @keyup.enter="searchQuestionReviewLogs" />
            <el-input v-model="questionReviewQuery.operatorId" placeholder="操作人 ID" clearable @keyup.enter="searchQuestionReviewLogs" />
            <el-date-picker
              v-model="questionReviewDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchQuestionReviewLogs">查询</el-button>
            <el-button @click="resetQuestionReviewFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="questionReviewExporting" @click="exportQuestionReviewLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="questionReviewLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="135">
              <template #default="scope">
                <div class="question-review-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制题目审核审计 ID"
                    aria-label="复制题目审核审计 ID"
                    @click="copyQuestionReviewAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制题目审核审计链接"
                    aria-label="复制题目审核审计链接"
                    @click="copyQuestionReviewAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.operatedAt) }}</template>
            </el-table-column>
            <el-table-column label="动作" min-width="120">
              <template #default="scope">
                <el-tag size="small" :type="questionReviewActionTag(scope.row.actionType)">
                  {{ questionReviewActionText(scope.row.actionType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="questionStem" label="题目" min-width="200" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.questionStem || `题目 #${scope.row.questionId}` }}</template>
            </el-table-column>
            <el-table-column prop="subjectName" label="学科" min-width="110" show-overflow-tooltip />
            <el-table-column label="审核" min-width="155">
              <template #default="scope">
                {{ questionReviewStatusText(scope.row.fromReviewStatus) }} -> {{ questionReviewStatusText(scope.row.toReviewStatus) }}
              </template>
            </el-table-column>
            <el-table-column label="题目状态" min-width="115">
              <template #default="scope">{{ questionPublishStatusText(scope.row.fromStatus) }} -> {{ questionPublishStatusText(scope.row.toStatus) }}</template>
            </el-table-column>
            <el-table-column label="操作人" min-width="130">
              <template #default="scope">{{ scope.row.operatorName || scope.row.operatorUsername || scope.row.operatedBy || '-' }}</template>
            </el-table-column>
            <el-table-column label="创建人" min-width="120">
              <template #default="scope">{{ scope.row.creatorName || scope.row.creatorUsername || scope.row.createdBy || '-' }}</template>
            </el-table-column>
            <el-table-column label="ID" min-width="120">
              <template #default="scope">Q{{ scope.row.questionId }} / v{{ scope.row.versionNo }}</template>
            </el-table-column>
            <el-table-column prop="comment" label="评语" min-width="180" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.comment || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无题目审核审计记录" />
            </template>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="系统配置审计" name="systemConfig">
          <div class="system-config-toolbar">
            <el-input v-model="systemConfigQuery.logId" placeholder="配置日志 ID" clearable @blur="normalizeSystemConfigLogIdField" @keyup.enter="searchSystemConfigLogs" />
            <el-input v-model="systemConfigQuery.keyword" placeholder="配置键、类别、值或操作人" clearable @keyup.enter="searchSystemConfigLogs" />
            <el-select v-model="systemConfigQuery.category" clearable placeholder="类别">
              <el-option v-for="item in systemConfigCategories" :key="item" :label="item" :value="item" />
            </el-select>
            <el-input v-model="systemConfigQuery.configKey" placeholder="配置键" clearable @keyup.enter="searchSystemConfigLogs" />
            <el-input v-model="systemConfigQuery.actorId" placeholder="操作人 ID" clearable @keyup.enter="searchSystemConfigLogs" />
            <el-date-picker
              v-model="systemConfigDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchSystemConfigLogs">查询</el-button>
            <el-button @click="resetSystemConfigFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="systemConfigExporting" @click="exportSystemConfigLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="systemConfigLogs" border max-height="620">
            <el-table-column label="日志 ID" min-width="135">
              <template #default="scope">
                <div class="system-config-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制系统配置审计 ID"
                    aria-label="复制系统配置审计 ID"
                    @click="copySystemConfigAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制系统配置审计链接"
                    aria-label="复制系统配置审计链接"
                    @click="copySystemConfigAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column prop="configKey" label="配置键" min-width="180" show-overflow-tooltip />
            <el-table-column label="类别" min-width="105">
              <template #default="scope">
                <el-tag size="small" type="info">{{ scope.row.category || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="valueType" label="类型" min-width="85" />
            <el-table-column prop="oldValue" label="旧值" min-width="140" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.oldValue ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="newValue" label="新值" min-width="140" show-overflow-tooltip />
            <el-table-column label="操作人" min-width="130">
              <template #default="scope">{{ scope.row.actorName || scope.row.actorUsername || scope.row.actorId || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无系统配置审计记录" />
            </template>
          </el-table>
        </el-tab-pane>
        <el-tab-pane label="通知审计" name="notification">
          <div class="notification-log-toolbar">
            <el-input
              v-model="notificationQuery.notificationId"
              placeholder="通知 ID"
              clearable
              @blur="normalizeNotificationAuditIdField"
              @keyup.enter="searchNotificationLogs"
            />
            <el-input v-model="notificationQuery.keyword" placeholder="接收人、标题、内容或类型" clearable @keyup.enter="searchNotificationLogs" />
            <el-input v-model="notificationQuery.type" placeholder="类型" clearable @keyup.enter="searchNotificationLogs" />
            <el-input v-model="notificationQuery.relatedType" placeholder="关联类型" clearable @keyup.enter="searchNotificationLogs" />
            <el-input v-model="notificationQuery.relatedId" placeholder="关联 ID" clearable @keyup.enter="searchNotificationLogs" />
            <el-select v-model="notificationQuery.read" placeholder="已读状态" clearable>
              <el-option label="未读" :value="false" />
              <el-option label="已读" :value="true" />
            </el-select>
            <el-date-picker
              v-model="notificationDateRange"
              type="datetimerange"
              value-format="YYYY-MM-DDTHH:mm:ss"
              range-separator="至"
              start-placeholder="开始时间"
              end-placeholder="结束时间"
              style="width: 100%"
            />
            <el-button type="primary" @click="searchNotificationLogs">查询</el-button>
            <el-button @click="resetNotificationFilters">重置</el-button>
            <el-button type="success" plain :icon="Download" :loading="notificationExporting" @click="exportNotificationLogs">
              导出
            </el-button>
          </div>

          <el-table v-loading="loading" :data="notificationLogs" border max-height="620">
            <el-table-column label="ID" min-width="135">
              <template #default="scope">
                <div class="notification-id-cell">
                  <span>#{{ scope.row.id }}</span>
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制通知审计 ID"
                    aria-label="复制通知审计 ID"
                    @click="copyNotificationAuditId(scope.row.id)"
                  />
                  <el-button
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制通知审计链接"
                    aria-label="复制通知审计链接"
                    @click="copyNotificationAuditLink(scope.row.id)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="时间" min-width="155">
              <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="接收人" min-width="130">
              <template #default="scope">{{ scope.row.realName || scope.row.username || scope.row.userId || '-' }}</template>
            </el-table-column>
            <el-table-column label="类型" min-width="130">
              <template #default="scope">
                <el-tag size="small" type="info">{{ scope.row.type || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
            <el-table-column prop="content" label="内容" min-width="200" show-overflow-tooltip />
            <el-table-column label="已读" min-width="80">
              <template #default="scope">
                <el-tag size="small" :type="notificationReadTag(scope.row.isRead)">
                  {{ notificationReadText(scope.row.isRead) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="关联" min-width="160">
              <template #default="scope">
                <div class="notification-related-cell">
                  <span>{{ scope.row.relatedType || '-' }} / {{ scope.row.relatedId || '-' }}</span>
                  <el-button
                    v-if="scope.row.relatedType && scope.row.relatedId"
                    link
                    type="primary"
                    :icon="DocumentCopy"
                    title="复制关联通知审计链接"
                    aria-label="复制关联通知审计链接"
                    @click="copyNotificationRelatedAuditLink(scope.row.relatedType, scope.row.relatedId)"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="link" label="链接" min-width="160" show-overflow-tooltip />
            <template #empty>
              <el-empty description="暂无通知审计记录" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <div class="log-pagination">
        <el-pagination
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalLogs"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import { DocumentCopy, Download, Refresh } from '@element-plus/icons-vue';
import {
  exportApprovalReminderAuditLogs,
  exportAiUsageLogs,
  exportExamApprovalAuditLogs,
  exportLoginAuditLogs,
  exportNotificationAuditLogs,
  exportOperationLogs,
  exportQuestionReviewAuditLogs,
  exportReviewScoreAuditLogs,
  exportScoreReleaseAuditLogs,
  exportScoreAppealAuditLogs,
  exportSystemConfigAuditLogs,
  listAiUsageLogs,
  listApprovalReminderAuditLogs,
  listExamApprovalAuditLogs,
  listLoginAuditLogs,
  listNotificationAuditLogs,
  listOperationLogs,
  listQuestionReviewAuditLogs,
  listReviewScoreAuditLogs,
  listScoreAppealAuditLogs,
  listScoreReleaseAuditLogs,
  listSystemConfigAuditLogs,
  type AiUsageLog,
  type ApprovalReminderAuditLog,
  type ExamApprovalAuditLog,
  type LoginAuditLog,
  type NotificationAuditLog,
  type OperationLog,
  type QuestionReviewAuditLog,
  type ReviewScoreAuditLog,
  type ScoreAppealAuditLog,
  type ScoreReleaseAuditLog,
  type SystemConfigAuditLog
} from '../api/admin';
import {
  copyApprovalReminderAuditLinkToClipboard,
  copyApprovalReminderLogIdToClipboard,
  copyExamApprovalAuditIdToClipboard,
  copyExamApprovalAuditLinkToClipboard,
  copyLoginAuditIdToClipboard,
  copyLoginAuditLinkToClipboard,
  copyNotificationAuditIdToClipboard,
  copyNotificationAuditLinkToClipboard,
  copyNotificationRelatedAuditLinkToClipboard,
  copyOperationLogIdToClipboard,
  copyOperationLogLinkToClipboard,
  copyQuestionReviewAuditIdToClipboard,
  copyQuestionReviewAuditLinkToClipboard,
  copyReviewScoreAuditIdToClipboard,
  copyReviewScoreAuditLinkToClipboard,
  copyScoreAppealAuditIdToClipboard,
  copyScoreAppealAuditLinkToClipboard,
  copyScoreReleaseAuditIdToClipboard,
  copyScoreReleaseAuditLinkToClipboard,
  copySystemConfigAuditIdToClipboard,
  copySystemConfigAuditLinkToClipboard
} from '../utils/clipboard';
import { formatDateTime } from '../utils/dateFormat';

type LogTab = 'operation' | 'login' | 'ai' | 'approvalReminder' | 'examApproval' | 'scoreRelease' | 'scoreAppeal' | 'reviewScore' | 'questionReview' | 'systemConfig' | 'notification';

const route = useRoute();
const logTabs: LogTab[] = ['operation', 'login', 'ai', 'approvalReminder', 'examApproval', 'scoreRelease', 'scoreAppeal', 'reviewScore', 'questionReview', 'systemConfig', 'notification'];

const sceneOptions = [
  { label: 'AI 出题', value: 'QUESTION_GENERATE' },
  { label: '题目文档识别', value: 'QUESTION_IMPORT' },
  { label: '课程材料生成', value: 'MATERIAL_GENERATE' },
  { label: '错题讲解', value: 'WRONG_QUESTION_EXPLAIN' },
  { label: '复习建议', value: 'SUGGEST_REVIEW' }
];
const systemConfigCategories = ['EXAM', 'APPROVAL', 'MONITOR', 'SCORE', 'SYSTEM', 'GENERAL'];
const questionReviewActionOptions = [
  { label: '创建', value: 'CREATE' },
  { label: '编辑', value: 'EDIT' },
  { label: '提交审核', value: 'SUBMIT_REVIEW' },
  { label: '通过', value: 'APPROVE' },
  { label: '驳回', value: 'REJECT' },
  { label: '上线', value: 'ONLINE' },
  { label: '下线', value: 'OFFLINE' },
  { label: '删除', value: 'DELETE' }
];
const questionReviewStatusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审核', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已驳回', value: 'REJECTED' }
];

const activeTab = ref<LogTab>('operation');
const loading = ref(false);
const operationExporting = ref(false);
const loginExporting = ref(false);
const aiExporting = ref(false);
const approvalReminderExporting = ref(false);
const examApprovalExporting = ref(false);
const scoreReleaseExporting = ref(false);
const scoreAppealExporting = ref(false);
const reviewScoreExporting = ref(false);
const questionReviewExporting = ref(false);
const systemConfigExporting = ref(false);
const notificationExporting = ref(false);
const operationLogs = ref<OperationLog[]>([]);
const loginLogs = ref<LoginAuditLog[]>([]);
const aiLogs = ref<AiUsageLog[]>([]);
const approvalReminderLogs = ref<ApprovalReminderAuditLog[]>([]);
const examApprovalLogs = ref<ExamApprovalAuditLog[]>([]);
const scoreReleaseLogs = ref<ScoreReleaseAuditLog[]>([]);
const scoreAppealLogs = ref<ScoreAppealAuditLog[]>([]);
const reviewScoreLogs = ref<ReviewScoreAuditLog[]>([]);
const questionReviewLogs = ref<QuestionReviewAuditLog[]>([]);
const systemConfigLogs = ref<SystemConfigAuditLog[]>([]);
const notificationLogs = ref<NotificationAuditLog[]>([]);
const operationPage = ref(1);
const operationSize = ref(10);
const operationTotal = ref(0);
const loginPage = ref(1);
const loginSize = ref(10);
const loginTotal = ref(0);
const aiPage = ref(1);
const aiSize = ref(10);
const aiTotal = ref(0);
const approvalReminderPage = ref(1);
const approvalReminderSize = ref(10);
const approvalReminderTotal = ref(0);
const examApprovalPage = ref(1);
const examApprovalSize = ref(10);
const examApprovalTotal = ref(0);
const scoreReleasePage = ref(1);
const scoreReleaseSize = ref(10);
const scoreReleaseTotal = ref(0);
const scoreAppealPage = ref(1);
const scoreAppealSize = ref(10);
const scoreAppealTotal = ref(0);
const reviewScorePage = ref(1);
const reviewScoreSize = ref(10);
const reviewScoreTotal = ref(0);
const questionReviewPage = ref(1);
const questionReviewSize = ref(10);
const questionReviewTotal = ref(0);
const systemConfigPage = ref(1);
const systemConfigSize = ref(10);
const systemConfigTotal = ref(0);
const notificationPage = ref(1);
const notificationSize = ref(10);
const notificationTotal = ref(0);
const aiQuery = reactive<{ scene: string; success: boolean | null; keyword: string }>({
  scene: '',
  success: null,
  keyword: ''
});
const aiDateRange = ref<[string, string] | null>(null);
const operationQuery = reactive<{ logId: string; keyword: string; action: string; target: string }>({
  logId: '',
  keyword: '',
  action: '',
  target: ''
});
const operationDateRange = ref<[string, string] | null>(null);
const loginQuery = reactive<{ logId: string; keyword: string; action: string; operatorId: string; success: boolean | null }>({
  logId: '',
  keyword: '',
  action: '',
  operatorId: '',
  success: null
});
const loginDateRange = ref<[string, string] | null>(null);
const approvalReminderQuery = reactive<{ logId: string; keyword: string; status: string; triggerSource: string }>({
  logId: '',
  keyword: '',
  status: '',
  triggerSource: ''
});
const approvalReminderDateRange = ref<[string, string] | null>(null);
const examApprovalQuery = reactive<{ logId: string; keyword: string; action: string }>({
  logId: '',
  keyword: '',
  action: ''
});
const examApprovalDateRange = ref<[string, string] | null>(null);
const scoreReleaseQuery = reactive<{ logId: string; keyword: string; action: string }>({
  logId: '',
  keyword: '',
  action: ''
});
const scoreReleaseDateRange = ref<[string, string] | null>(null);
const scoreAppealQuery = reactive<{ logId: string; keyword: string; action: string; handlingResult: string }>({
  logId: '',
  keyword: '',
  action: '',
  handlingResult: ''
});
const scoreAppealDateRange = ref<[string, string] | null>(null);
const reviewScoreQuery = reactive<{ logId: string; keyword: string; examId: string; reviewerId: string }>({
  logId: '',
  keyword: '',
  examId: '',
  reviewerId: ''
});
const reviewScoreDateRange = ref<[string, string] | null>(null);
const questionReviewQuery = reactive<{
  logId: string;
  questionId: string;
  keyword: string;
  actionType: string;
  reviewStatus: string;
  subjectId: string;
  operatorId: string;
}>({
  logId: '',
  questionId: '',
  keyword: '',
  actionType: '',
  reviewStatus: '',
  subjectId: '',
  operatorId: ''
});
const questionReviewDateRange = ref<[string, string] | null>(null);
const systemConfigQuery = reactive<{ logId: string; keyword: string; category: string; configKey: string; actorId: string }>({
  logId: '',
  keyword: '',
  category: '',
  configKey: '',
  actorId: ''
});
const systemConfigDateRange = ref<[string, string] | null>(null);
const notificationQuery = reactive<{
  notificationId: string;
  keyword: string;
  type: string;
  relatedType: string;
  relatedId: string;
  read: boolean | null;
}>({
  notificationId: '',
  keyword: '',
  type: '',
  relatedType: '',
  relatedId: '',
  read: null
});
const notificationDateRange = ref<[string, string] | null>(null);

const logSubtitle = computed(() => {
  if (activeTab.value === 'operation') return '后台操作记录';
  if (activeTab.value === 'login') return '登录与认证安全审计';
  if (activeTab.value === 'ai') return 'AI 调用审计';
  if (activeTab.value === 'approvalReminder') return '审批提醒调度审计';
  if (activeTab.value === 'examApproval') return '考试审批生命周期审计';
  if (activeTab.value === 'scoreRelease') return '成绩发布与撤回审计';
  if (activeTab.value === 'reviewScore') return '阅卷评分审计';
  if (activeTab.value === 'questionReview') return '题目审核生命周期审计';
  if (activeTab.value === 'systemConfig') return '系统配置变更审计';
  if (activeTab.value === 'notification') return '通知投递审计';
  return '成绩申诉生命周期审计';
});
const currentPage = computed(() => {
  if (activeTab.value === 'operation') return operationPage.value;
  if (activeTab.value === 'login') return loginPage.value;
  if (activeTab.value === 'ai') return aiPage.value;
  if (activeTab.value === 'approvalReminder') return approvalReminderPage.value;
  if (activeTab.value === 'examApproval') return examApprovalPage.value;
  if (activeTab.value === 'scoreRelease') return scoreReleasePage.value;
  if (activeTab.value === 'reviewScore') return reviewScorePage.value;
  if (activeTab.value === 'questionReview') return questionReviewPage.value;
  if (activeTab.value === 'systemConfig') return systemConfigPage.value;
  if (activeTab.value === 'notification') return notificationPage.value;
  return scoreAppealPage.value;
});
const pageSize = computed(() => {
  if (activeTab.value === 'operation') return operationSize.value;
  if (activeTab.value === 'login') return loginSize.value;
  if (activeTab.value === 'ai') return aiSize.value;
  if (activeTab.value === 'approvalReminder') return approvalReminderSize.value;
  if (activeTab.value === 'examApproval') return examApprovalSize.value;
  if (activeTab.value === 'scoreRelease') return scoreReleaseSize.value;
  if (activeTab.value === 'reviewScore') return reviewScoreSize.value;
  if (activeTab.value === 'questionReview') return questionReviewSize.value;
  if (activeTab.value === 'systemConfig') return systemConfigSize.value;
  if (activeTab.value === 'notification') return notificationSize.value;
  return scoreAppealSize.value;
});
const totalLogs = computed(() => {
  if (activeTab.value === 'operation') return operationTotal.value;
  if (activeTab.value === 'login') return loginTotal.value;
  if (activeTab.value === 'ai') return aiTotal.value;
  if (activeTab.value === 'approvalReminder') return approvalReminderTotal.value;
  if (activeTab.value === 'examApproval') return examApprovalTotal.value;
  if (activeTab.value === 'scoreRelease') return scoreReleaseTotal.value;
  if (activeTab.value === 'reviewScore') return reviewScoreTotal.value;
  if (activeTab.value === 'questionReview') return questionReviewTotal.value;
  if (activeTab.value === 'systemConfig') return systemConfigTotal.value;
  if (activeTab.value === 'notification') return notificationTotal.value;
  return scoreAppealTotal.value;
});

hydrateLogRouteQuery();
onMounted(() => load());
watch(activeTab, () => load());
watch(
  () => route.fullPath,
  () => {
    const previousTab = activeTab.value;
    const changed = hydrateLogRouteQuery();
    if (changed && activeTab.value === previousTab) {
      load();
    }
  }
);

async function load() {
  loading.value = true;
  try {
    if (activeTab.value === 'operation') {
      const response = await listOperationLogs(operationPage.value, operationSize.value, {
        logId: normalizedOperationLogId() || undefined,
        keyword: operationQuery.keyword.trim() || undefined,
        action: operationQuery.action.trim() || undefined,
        target: operationQuery.target.trim() || undefined,
        startFrom: operationDateRange.value?.[0],
        startTo: operationDateRange.value?.[1]
      });
      operationLogs.value = response.data.list;
      operationTotal.value = response.data.total;
    } else if (activeTab.value === 'login') {
      const response = await listLoginAuditLogs(loginPage.value, loginSize.value, {
        logId: normalizedLoginLogId() || undefined,
        keyword: loginQuery.keyword.trim() || undefined,
        action: loginQuery.action.trim() || undefined,
        operatorId: loginQuery.operatorId.trim() || undefined,
        success: loginQuery.success,
        startFrom: loginDateRange.value?.[0],
        startTo: loginDateRange.value?.[1]
      });
      loginLogs.value = response.data.list;
      loginTotal.value = response.data.total;
    } else if (activeTab.value === 'ai') {
      const response = await listAiUsageLogs(aiPage.value, aiSize.value, {
        scene: aiQuery.scene || undefined,
        success: aiQuery.success,
        keyword: aiQuery.keyword.trim() || undefined,
        startFrom: aiDateRange.value?.[0],
        startTo: aiDateRange.value?.[1]
      });
      aiLogs.value = response.data.list;
      aiTotal.value = response.data.total;
    } else if (activeTab.value === 'approvalReminder') {
      const response = await listApprovalReminderAuditLogs(approvalReminderPage.value, approvalReminderSize.value, {
        logId: normalizedApprovalReminderLogId() || undefined,
        keyword: approvalReminderQuery.keyword.trim() || undefined,
        status: approvalReminderQuery.status || undefined,
        triggerSource: approvalReminderQuery.triggerSource || undefined,
        startFrom: approvalReminderDateRange.value?.[0],
        startTo: approvalReminderDateRange.value?.[1]
      });
      approvalReminderLogs.value = response.data.list;
      approvalReminderTotal.value = response.data.total;
    } else if (activeTab.value === 'examApproval') {
      const response = await listExamApprovalAuditLogs(examApprovalPage.value, examApprovalSize.value, {
        logId: normalizedExamApprovalLogId() || undefined,
        keyword: examApprovalQuery.keyword.trim() || undefined,
        action: examApprovalQuery.action || undefined,
        startFrom: examApprovalDateRange.value?.[0],
        startTo: examApprovalDateRange.value?.[1]
      });
      examApprovalLogs.value = response.data.list;
      examApprovalTotal.value = response.data.total;
    } else if (activeTab.value === 'scoreRelease') {
      const response = await listScoreReleaseAuditLogs(scoreReleasePage.value, scoreReleaseSize.value, {
        logId: normalizedScoreReleaseLogId() || undefined,
        keyword: scoreReleaseQuery.keyword.trim() || undefined,
        action: scoreReleaseQuery.action || undefined,
        startFrom: scoreReleaseDateRange.value?.[0],
        startTo: scoreReleaseDateRange.value?.[1]
      });
      scoreReleaseLogs.value = response.data.list;
      scoreReleaseTotal.value = response.data.total;
    } else if (activeTab.value === 'reviewScore') {
      const response = await listReviewScoreAuditLogs(reviewScorePage.value, reviewScoreSize.value, {
        logId: normalizedReviewScoreLogId() || undefined,
        keyword: reviewScoreQuery.keyword.trim() || undefined,
        examId: reviewScoreQuery.examId.trim() || undefined,
        reviewerId: reviewScoreQuery.reviewerId.trim() || undefined,
        startFrom: reviewScoreDateRange.value?.[0],
        startTo: reviewScoreDateRange.value?.[1]
      });
      reviewScoreLogs.value = response.data.list;
      reviewScoreTotal.value = response.data.total;
    } else if (activeTab.value === 'questionReview') {
      const response = await listQuestionReviewAuditLogs(questionReviewPage.value, questionReviewSize.value, {
        logId: normalizedQuestionReviewLogId() || undefined,
        questionId: questionReviewQuery.questionId.trim() || undefined,
        keyword: questionReviewQuery.keyword.trim() || undefined,
        actionType: questionReviewQuery.actionType || undefined,
        reviewStatus: questionReviewQuery.reviewStatus || undefined,
        subjectId: questionReviewQuery.subjectId.trim() || undefined,
        operatorId: questionReviewQuery.operatorId.trim() || undefined,
        startFrom: questionReviewDateRange.value?.[0],
        startTo: questionReviewDateRange.value?.[1]
      });
      questionReviewLogs.value = response.data.list;
      questionReviewTotal.value = response.data.total;
    } else if (activeTab.value === 'systemConfig') {
      const response = await listSystemConfigAuditLogs(systemConfigPage.value, systemConfigSize.value, {
        logId: normalizedSystemConfigLogId() || undefined,
        keyword: systemConfigQuery.keyword.trim() || undefined,
        category: systemConfigQuery.category || undefined,
        configKey: systemConfigQuery.configKey.trim() || undefined,
        actorId: systemConfigQuery.actorId.trim() || undefined,
        startFrom: systemConfigDateRange.value?.[0],
        startTo: systemConfigDateRange.value?.[1]
      });
      systemConfigLogs.value = response.data.list;
      systemConfigTotal.value = response.data.total;
    } else if (activeTab.value === 'notification') {
      const response = await listNotificationAuditLogs(notificationPage.value, notificationSize.value, {
        notificationId: normalizedNotificationAuditId() || undefined,
        keyword: notificationQuery.keyword.trim() || undefined,
        type: notificationQuery.type.trim() || undefined,
        relatedType: notificationQuery.relatedType.trim() || undefined,
        relatedId: notificationQuery.relatedId.trim() || undefined,
        read: notificationQuery.read,
        startFrom: notificationDateRange.value?.[0],
        startTo: notificationDateRange.value?.[1]
      });
      notificationLogs.value = response.data.list;
      notificationTotal.value = response.data.total;
    } else {
      const response = await listScoreAppealAuditLogs(scoreAppealPage.value, scoreAppealSize.value, {
        logId: normalizedScoreAppealLogId() || undefined,
        keyword: scoreAppealQuery.keyword.trim() || undefined,
        action: scoreAppealQuery.action || undefined,
        handlingResult: scoreAppealQuery.handlingResult || undefined,
        startFrom: scoreAppealDateRange.value?.[0],
        startTo: scoreAppealDateRange.value?.[1]
      });
      scoreAppealLogs.value = response.data.list;
      scoreAppealTotal.value = response.data.total;
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '日志加载失败');
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  if (activeTab.value === 'operation') {
    operationPage.value = page;
  } else if (activeTab.value === 'login') {
    loginPage.value = page;
  } else if (activeTab.value === 'ai') {
    aiPage.value = page;
  } else if (activeTab.value === 'approvalReminder') {
    approvalReminderPage.value = page;
  } else if (activeTab.value === 'examApproval') {
    examApprovalPage.value = page;
  } else if (activeTab.value === 'scoreRelease') {
    scoreReleasePage.value = page;
  } else if (activeTab.value === 'reviewScore') {
    reviewScorePage.value = page;
  } else if (activeTab.value === 'questionReview') {
    questionReviewPage.value = page;
  } else if (activeTab.value === 'systemConfig') {
    systemConfigPage.value = page;
  } else if (activeTab.value === 'notification') {
    notificationPage.value = page;
  } else {
    scoreAppealPage.value = page;
  }
  load();
}

function handleSizeChange(size: number) {
  if (activeTab.value === 'operation') {
    operationSize.value = size;
    operationPage.value = 1;
  } else if (activeTab.value === 'login') {
    loginSize.value = size;
    loginPage.value = 1;
  } else if (activeTab.value === 'ai') {
    aiSize.value = size;
    aiPage.value = 1;
  } else if (activeTab.value === 'approvalReminder') {
    approvalReminderSize.value = size;
    approvalReminderPage.value = 1;
  } else if (activeTab.value === 'examApproval') {
    examApprovalSize.value = size;
    examApprovalPage.value = 1;
  } else if (activeTab.value === 'scoreRelease') {
    scoreReleaseSize.value = size;
    scoreReleasePage.value = 1;
  } else if (activeTab.value === 'reviewScore') {
    reviewScoreSize.value = size;
    reviewScorePage.value = 1;
  } else if (activeTab.value === 'questionReview') {
    questionReviewSize.value = size;
    questionReviewPage.value = 1;
  } else if (activeTab.value === 'systemConfig') {
    systemConfigSize.value = size;
    systemConfigPage.value = 1;
  } else if (activeTab.value === 'notification') {
    notificationSize.value = size;
    notificationPage.value = 1;
  } else {
    scoreAppealSize.value = size;
    scoreAppealPage.value = 1;
  }
  load();
}

function searchAiLogs() {
  aiPage.value = 1;
  load();
}

function resetAiFilters() {
  aiQuery.scene = '';
  aiQuery.success = null;
  aiQuery.keyword = '';
  aiDateRange.value = null;
  aiPage.value = 1;
  load();
}

async function exportAiLogs() {
  aiExporting.value = true;
  try {
    await exportAiUsageLogs({
      scene: aiQuery.scene || undefined,
      success: aiQuery.success,
      keyword: aiQuery.keyword.trim() || undefined,
      startFrom: aiDateRange.value?.[0],
      startTo: aiDateRange.value?.[1]
    });
    ElMessage.success('AI 调用日志导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    aiExporting.value = false;
  }
}

function searchOperationLogs() {
  normalizeOperationLogIdField();
  operationPage.value = 1;
  load();
}

function resetOperationFilters() {
  operationQuery.logId = '';
  operationQuery.keyword = '';
  operationQuery.action = '';
  operationQuery.target = '';
  operationDateRange.value = null;
  operationPage.value = 1;
  load();
}

async function exportOperationLogRows() {
  normalizeOperationLogIdField();
  operationExporting.value = true;
  try {
    await exportOperationLogs({
      logId: normalizedOperationLogId() || undefined,
      keyword: operationQuery.keyword.trim() || undefined,
      action: operationQuery.action.trim() || undefined,
      target: operationQuery.target.trim() || undefined,
      startFrom: operationDateRange.value?.[0],
      startTo: operationDateRange.value?.[1]
    });
    ElMessage.success('操作日志导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    operationExporting.value = false;
  }
}

function normalizedOperationLogId() {
  return extractOperationLogId(operationQuery.logId);
}

function extractOperationLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeOperationLogIdField() {
  operationQuery.logId = normalizedOperationLogId();
}

function searchLoginLogs() {
  normalizeLoginLogIdField();
  loginPage.value = 1;
  load();
}

function resetLoginFilters() {
  loginQuery.logId = '';
  loginQuery.keyword = '';
  loginQuery.action = '';
  loginQuery.operatorId = '';
  loginQuery.success = null;
  loginDateRange.value = null;
  loginPage.value = 1;
  load();
}

async function exportLoginLogs() {
  normalizeLoginLogIdField();
  loginExporting.value = true;
  try {
    await exportLoginAuditLogs({
      logId: normalizedLoginLogId() || undefined,
      keyword: loginQuery.keyword.trim() || undefined,
      action: loginQuery.action.trim() || undefined,
      operatorId: loginQuery.operatorId.trim() || undefined,
      success: loginQuery.success,
      startFrom: loginDateRange.value?.[0],
      startTo: loginDateRange.value?.[1]
    });
    ElMessage.success('登录审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    loginExporting.value = false;
  }
}

function normalizedLoginLogId() {
  return extractLoginLogId(loginQuery.logId);
}

function extractLoginLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeLoginLogIdField() {
  loginQuery.logId = normalizedLoginLogId();
}

function searchApprovalReminderLogs() {
  approvalReminderPage.value = 1;
  load();
}

function resetApprovalReminderFilters() {
  approvalReminderQuery.logId = '';
  approvalReminderQuery.keyword = '';
  approvalReminderQuery.status = '';
  approvalReminderQuery.triggerSource = '';
  approvalReminderDateRange.value = null;
  approvalReminderPage.value = 1;
  load();
}

async function exportApprovalReminderLogs() {
  approvalReminderExporting.value = true;
  try {
    await exportApprovalReminderAuditLogs({
      logId: normalizedApprovalReminderLogId() || undefined,
      keyword: approvalReminderQuery.keyword.trim() || undefined,
      status: approvalReminderQuery.status || undefined,
      triggerSource: approvalReminderQuery.triggerSource || undefined,
      startFrom: approvalReminderDateRange.value?.[0],
      startTo: approvalReminderDateRange.value?.[1]
    });
    ElMessage.success('审批提醒审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    approvalReminderExporting.value = false;
  }
}

function normalizedApprovalReminderLogId() {
  return extractApprovalReminderLogId(approvalReminderQuery.logId);
}

function extractApprovalReminderLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeApprovalReminderLogIdField() {
  approvalReminderQuery.logId = normalizedApprovalReminderLogId();
}

function searchExamApprovalLogs() {
  examApprovalPage.value = 1;
  load();
}

function resetExamApprovalFilters() {
  examApprovalQuery.logId = '';
  examApprovalQuery.keyword = '';
  examApprovalQuery.action = '';
  examApprovalDateRange.value = null;
  examApprovalPage.value = 1;
  load();
}

async function exportExamApprovalLogs() {
  examApprovalExporting.value = true;
  try {
    await exportExamApprovalAuditLogs({
      logId: normalizedExamApprovalLogId() || undefined,
      keyword: examApprovalQuery.keyword.trim() || undefined,
      action: examApprovalQuery.action || undefined,
      startFrom: examApprovalDateRange.value?.[0],
      startTo: examApprovalDateRange.value?.[1]
    });
    ElMessage.success('考试审批审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    examApprovalExporting.value = false;
  }
}

function normalizedExamApprovalLogId() {
  return extractExamApprovalLogId(examApprovalQuery.logId);
}

function extractExamApprovalLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeExamApprovalLogIdField() {
  examApprovalQuery.logId = normalizedExamApprovalLogId();
}

function searchScoreReleaseLogs() {
  scoreReleasePage.value = 1;
  load();
}

function resetScoreReleaseFilters() {
  scoreReleaseQuery.logId = '';
  scoreReleaseQuery.keyword = '';
  scoreReleaseQuery.action = '';
  scoreReleaseDateRange.value = null;
  scoreReleasePage.value = 1;
  load();
}

async function exportScoreReleaseLogs() {
  scoreReleaseExporting.value = true;
  try {
    await exportScoreReleaseAuditLogs({
      logId: normalizedScoreReleaseLogId() || undefined,
      keyword: scoreReleaseQuery.keyword.trim() || undefined,
      action: scoreReleaseQuery.action || undefined,
      startFrom: scoreReleaseDateRange.value?.[0],
      startTo: scoreReleaseDateRange.value?.[1]
    });
    ElMessage.success('成绩发布审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    scoreReleaseExporting.value = false;
  }
}

function normalizedScoreReleaseLogId() {
  return extractScoreReleaseLogId(scoreReleaseQuery.logId);
}

function extractScoreReleaseLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeScoreReleaseLogIdField() {
  scoreReleaseQuery.logId = normalizedScoreReleaseLogId();
}

function normalizedScoreAppealLogId() {
  return extractScoreAppealLogId(scoreAppealQuery.logId);
}

function extractScoreAppealLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeScoreAppealLogIdField() {
  scoreAppealQuery.logId = normalizedScoreAppealLogId();
}

function normalizedReviewScoreLogId() {
  return extractReviewScoreLogId(reviewScoreQuery.logId);
}

function extractReviewScoreLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeReviewScoreLogIdField() {
  reviewScoreQuery.logId = normalizedReviewScoreLogId();
}

function searchScoreAppealLogs() {
  scoreAppealPage.value = 1;
  load();
}

function resetScoreAppealFilters() {
  scoreAppealQuery.logId = '';
  scoreAppealQuery.keyword = '';
  scoreAppealQuery.action = '';
  scoreAppealQuery.handlingResult = '';
  scoreAppealDateRange.value = null;
  scoreAppealPage.value = 1;
  load();
}

async function exportScoreAppealLogs() {
  scoreAppealExporting.value = true;
  try {
    await exportScoreAppealAuditLogs({
      logId: normalizedScoreAppealLogId() || undefined,
      keyword: scoreAppealQuery.keyword.trim() || undefined,
      action: scoreAppealQuery.action || undefined,
      handlingResult: scoreAppealQuery.handlingResult || undefined,
      startFrom: scoreAppealDateRange.value?.[0],
      startTo: scoreAppealDateRange.value?.[1]
    });
    ElMessage.success('成绩申诉审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    scoreAppealExporting.value = false;
  }
}

function searchReviewScoreLogs() {
  reviewScorePage.value = 1;
  load();
}

function resetReviewScoreFilters() {
  reviewScoreQuery.logId = '';
  reviewScoreQuery.keyword = '';
  reviewScoreQuery.examId = '';
  reviewScoreQuery.reviewerId = '';
  reviewScoreDateRange.value = null;
  reviewScorePage.value = 1;
  load();
}

async function exportReviewScoreLogs() {
  reviewScoreExporting.value = true;
  try {
    await exportReviewScoreAuditLogs({
      logId: normalizedReviewScoreLogId() || undefined,
      keyword: reviewScoreQuery.keyword.trim() || undefined,
      examId: reviewScoreQuery.examId.trim() || undefined,
      reviewerId: reviewScoreQuery.reviewerId.trim() || undefined,
      startFrom: reviewScoreDateRange.value?.[0],
      startTo: reviewScoreDateRange.value?.[1]
    });
    ElMessage.success('阅卷评分审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    reviewScoreExporting.value = false;
  }
}

function searchQuestionReviewLogs() {
  normalizeQuestionReviewLogIdField();
  questionReviewPage.value = 1;
  load();
}

function resetQuestionReviewFilters() {
  questionReviewQuery.logId = '';
  questionReviewQuery.questionId = '';
  questionReviewQuery.keyword = '';
  questionReviewQuery.actionType = '';
  questionReviewQuery.reviewStatus = '';
  questionReviewQuery.subjectId = '';
  questionReviewQuery.operatorId = '';
  questionReviewDateRange.value = null;
  questionReviewPage.value = 1;
  load();
}

async function exportQuestionReviewLogs() {
  normalizeQuestionReviewLogIdField();
  questionReviewExporting.value = true;
  try {
    await exportQuestionReviewAuditLogs({
      logId: normalizedQuestionReviewLogId() || undefined,
      questionId: questionReviewQuery.questionId.trim() || undefined,
      keyword: questionReviewQuery.keyword.trim() || undefined,
      actionType: questionReviewQuery.actionType || undefined,
      reviewStatus: questionReviewQuery.reviewStatus || undefined,
      subjectId: questionReviewQuery.subjectId.trim() || undefined,
      operatorId: questionReviewQuery.operatorId.trim() || undefined,
      startFrom: questionReviewDateRange.value?.[0],
      startTo: questionReviewDateRange.value?.[1]
    });
    ElMessage.success('题目审核审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    questionReviewExporting.value = false;
  }
}

function normalizedQuestionReviewLogId() {
  return extractQuestionReviewLogId(questionReviewQuery.logId);
}

function extractQuestionReviewLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeQuestionReviewLogIdField() {
  questionReviewQuery.logId = normalizedQuestionReviewLogId();
}

function searchSystemConfigLogs() {
  normalizeSystemConfigLogIdField();
  systemConfigPage.value = 1;
  load();
}

function resetSystemConfigFilters() {
  systemConfigQuery.logId = '';
  systemConfigQuery.keyword = '';
  systemConfigQuery.category = '';
  systemConfigQuery.configKey = '';
  systemConfigQuery.actorId = '';
  systemConfigDateRange.value = null;
  systemConfigPage.value = 1;
  load();
}

async function exportSystemConfigLogs() {
  normalizeSystemConfigLogIdField();
  systemConfigExporting.value = true;
  try {
    await exportSystemConfigAuditLogs({
      logId: normalizedSystemConfigLogId() || undefined,
      keyword: systemConfigQuery.keyword.trim() || undefined,
      category: systemConfigQuery.category || undefined,
      configKey: systemConfigQuery.configKey.trim() || undefined,
      actorId: systemConfigQuery.actorId.trim() || undefined,
      startFrom: systemConfigDateRange.value?.[0],
      startTo: systemConfigDateRange.value?.[1]
    });
    ElMessage.success('系统配置审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    systemConfigExporting.value = false;
  }
}

function normalizedSystemConfigLogId() {
  return extractSystemConfigLogId(systemConfigQuery.logId);
}

function extractSystemConfigLogId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeSystemConfigLogIdField() {
  systemConfigQuery.logId = normalizedSystemConfigLogId();
}

function searchNotificationLogs() {
  normalizeNotificationAuditIdField();
  notificationPage.value = 1;
  load();
}

function resetNotificationFilters() {
  notificationQuery.notificationId = '';
  notificationQuery.keyword = '';
  notificationQuery.type = '';
  notificationQuery.relatedType = '';
  notificationQuery.relatedId = '';
  notificationQuery.read = null;
  notificationDateRange.value = null;
  notificationPage.value = 1;
  load();
}

async function exportNotificationLogs() {
  normalizeNotificationAuditIdField();
  notificationExporting.value = true;
  try {
    await exportNotificationAuditLogs({
      notificationId: normalizedNotificationAuditId() || undefined,
      keyword: notificationQuery.keyword.trim() || undefined,
      type: notificationQuery.type.trim() || undefined,
      relatedType: notificationQuery.relatedType.trim() || undefined,
      relatedId: notificationQuery.relatedId.trim() || undefined,
      read: notificationQuery.read,
      startFrom: notificationDateRange.value?.[0],
      startTo: notificationDateRange.value?.[1]
    });
    ElMessage.success('通知审计导出已开始');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    notificationExporting.value = false;
  }
}

function normalizedNotificationAuditId() {
  return extractNotificationAuditId(notificationQuery.notificationId);
}

function extractNotificationAuditId(rawValue: string) {
  const value = rawValue.trim();
  if (!value) return '';
  const match = value.match(/\d+/);
  return match ? match[0] : '';
}

function normalizeNotificationAuditIdField() {
  notificationQuery.notificationId = normalizedNotificationAuditId();
}

function hydrateLogRouteQuery() {
  let changed = false;
  const tab = firstQueryValue(route.query.tab);
  if (isLogTab(tab) && activeTab.value !== tab) {
    activeTab.value = tab;
    changed = true;
  }
  const hasOperationLogIdQuery = hasRouteQueryKey('operationLogId')
    || (tab === 'operation' && hasRouteQueryKey('logId'));
  const operationLogId = hasRouteQueryKey('operationLogId')
    ? firstQueryValue(route.query.operationLogId)
    : firstQueryValue(route.query.logId);
  if (hasOperationLogIdQuery) {
    const normalizedLogId = extractOperationLogId(operationLogId);
    if (activeTab.value !== 'operation') {
      activeTab.value = 'operation';
      changed = true;
    }
    if (operationQuery.logId !== normalizedLogId) {
      operationQuery.logId = normalizedLogId;
      operationPage.value = 1;
      changed = true;
    }
  } else if (tab === 'operation' && operationQuery.logId) {
    operationQuery.logId = '';
    operationPage.value = 1;
    changed = true;
  }
  if (tab === 'operation') {
    const nextAction = firstQueryValue(route.query.action).trim();
    const nextTarget = firstQueryValue(route.query.target).trim();
    const nextKeyword = firstQueryValue(route.query.keyword).trim();
    if (operationQuery.action !== nextAction) {
      operationQuery.action = nextAction;
      operationPage.value = 1;
      changed = true;
    }
    if (operationQuery.target !== nextTarget) {
      operationQuery.target = nextTarget;
      operationPage.value = 1;
      changed = true;
    }
    if (operationQuery.keyword !== nextKeyword) {
      operationQuery.keyword = nextKeyword;
      operationPage.value = 1;
      changed = true;
    }
  }
  const hasLoginLogIdQuery = hasRouteQueryKey('loginLogId')
    || (tab === 'login' && hasRouteQueryKey('logId'));
  const loginLogId = hasRouteQueryKey('loginLogId')
    ? firstQueryValue(route.query.loginLogId)
    : firstQueryValue(route.query.logId);
  if (hasLoginLogIdQuery) {
    const normalizedLogId = extractLoginLogId(loginLogId);
    if (activeTab.value !== 'login') {
      activeTab.value = 'login';
      changed = true;
    }
    if (loginQuery.logId !== normalizedLogId) {
      loginQuery.logId = normalizedLogId;
      loginPage.value = 1;
      changed = true;
    }
  } else if (tab === 'login' && loginQuery.logId) {
    loginQuery.logId = '';
    loginPage.value = 1;
    changed = true;
  }
  const hasScoreReleaseLogIdQuery = hasRouteQueryKey('scoreReleaseLogId')
    || (tab === 'scoreRelease' && hasRouteQueryKey('logId'));
  const scoreReleaseLogId = hasRouteQueryKey('scoreReleaseLogId')
    ? firstQueryValue(route.query.scoreReleaseLogId)
    : firstQueryValue(route.query.logId);
  if (hasScoreReleaseLogIdQuery) {
    const normalizedLogId = extractScoreReleaseLogId(scoreReleaseLogId);
    if (activeTab.value !== 'scoreRelease') {
      activeTab.value = 'scoreRelease';
      changed = true;
    }
    if (scoreReleaseQuery.logId !== normalizedLogId) {
      scoreReleaseQuery.logId = normalizedLogId;
      scoreReleasePage.value = 1;
      changed = true;
    }
  } else if (tab === 'scoreRelease' && scoreReleaseQuery.logId) {
    scoreReleaseQuery.logId = '';
    scoreReleasePage.value = 1;
    changed = true;
  }
  const hasApprovalReminderLogIdQuery = hasRouteQueryKey('approvalReminderLogId')
    || (tab === 'approvalReminder' && hasRouteQueryKey('logId'));
  const approvalReminderLogId = hasRouteQueryKey('approvalReminderLogId')
    ? firstQueryValue(route.query.approvalReminderLogId)
    : firstQueryValue(route.query.logId);
  if (hasApprovalReminderLogIdQuery) {
    const normalizedLogId = extractApprovalReminderLogId(approvalReminderLogId);
    if (activeTab.value !== 'approvalReminder') {
      activeTab.value = 'approvalReminder';
      changed = true;
    }
    if (approvalReminderQuery.logId !== normalizedLogId) {
      approvalReminderQuery.logId = normalizedLogId;
      approvalReminderPage.value = 1;
      changed = true;
    }
  } else if (tab === 'approvalReminder' && approvalReminderQuery.logId) {
    approvalReminderQuery.logId = '';
    approvalReminderPage.value = 1;
    changed = true;
  }
  const hasExamApprovalLogIdQuery = hasRouteQueryKey('examApprovalLogId')
    || (tab === 'examApproval' && hasRouteQueryKey('logId'));
  const examApprovalLogId = hasRouteQueryKey('examApprovalLogId')
    ? firstQueryValue(route.query.examApprovalLogId)
    : firstQueryValue(route.query.logId);
  if (hasExamApprovalLogIdQuery) {
    const normalizedLogId = extractExamApprovalLogId(examApprovalLogId);
    if (activeTab.value !== 'examApproval') {
      activeTab.value = 'examApproval';
      changed = true;
    }
    if (examApprovalQuery.logId !== normalizedLogId) {
      examApprovalQuery.logId = normalizedLogId;
      examApprovalPage.value = 1;
      changed = true;
    }
  } else if (tab === 'examApproval' && examApprovalQuery.logId) {
    examApprovalQuery.logId = '';
    examApprovalPage.value = 1;
    changed = true;
  }
  const hasScoreAppealLogIdQuery = hasRouteQueryKey('scoreAppealLogId')
    || (tab === 'scoreAppeal' && hasRouteQueryKey('logId'));
  const scoreAppealLogId = hasRouteQueryKey('scoreAppealLogId')
    ? firstQueryValue(route.query.scoreAppealLogId)
    : firstQueryValue(route.query.logId);
  if (hasScoreAppealLogIdQuery) {
    const normalizedLogId = extractScoreAppealLogId(scoreAppealLogId);
    if (activeTab.value !== 'scoreAppeal') {
      activeTab.value = 'scoreAppeal';
      changed = true;
    }
    if (scoreAppealQuery.logId !== normalizedLogId) {
      scoreAppealQuery.logId = normalizedLogId;
      scoreAppealPage.value = 1;
      changed = true;
    }
  } else if (tab === 'scoreAppeal' && scoreAppealQuery.logId) {
    scoreAppealQuery.logId = '';
    scoreAppealPage.value = 1;
    changed = true;
  }
  const hasReviewScoreLogIdQuery = hasRouteQueryKey('reviewScoreLogId')
    || (tab === 'reviewScore' && hasRouteQueryKey('logId'));
  const reviewScoreLogId = hasRouteQueryKey('reviewScoreLogId')
    ? firstQueryValue(route.query.reviewScoreLogId)
    : firstQueryValue(route.query.logId);
  if (hasReviewScoreLogIdQuery) {
    const normalizedLogId = extractReviewScoreLogId(reviewScoreLogId);
    if (activeTab.value !== 'reviewScore') {
      activeTab.value = 'reviewScore';
      changed = true;
    }
    if (reviewScoreQuery.logId !== normalizedLogId) {
      reviewScoreQuery.logId = normalizedLogId;
      reviewScorePage.value = 1;
      changed = true;
    }
  } else if (tab === 'reviewScore' && reviewScoreQuery.logId) {
    reviewScoreQuery.logId = '';
    reviewScorePage.value = 1;
    changed = true;
  }
  const hasQuestionReviewLogIdQuery = hasRouteQueryKey('questionReviewLogId')
    || (tab === 'questionReview' && hasRouteQueryKey('logId'));
  const questionReviewLogId = hasRouteQueryKey('questionReviewLogId')
    ? firstQueryValue(route.query.questionReviewLogId)
    : firstQueryValue(route.query.logId);
  if (hasQuestionReviewLogIdQuery) {
    const normalizedLogId = extractQuestionReviewLogId(questionReviewLogId);
    if (activeTab.value !== 'questionReview') {
      activeTab.value = 'questionReview';
      changed = true;
    }
    if (questionReviewQuery.logId !== normalizedLogId) {
      questionReviewQuery.logId = normalizedLogId;
      questionReviewPage.value = 1;
      changed = true;
    }
  } else if (tab === 'questionReview' && questionReviewQuery.logId) {
    questionReviewQuery.logId = '';
    questionReviewPage.value = 1;
    changed = true;
  }
  const hasSystemConfigLogIdQuery = hasRouteQueryKey('systemConfigLogId')
    || (tab === 'systemConfig' && hasRouteQueryKey('logId'));
  const systemConfigLogId = hasRouteQueryKey('systemConfigLogId')
    ? firstQueryValue(route.query.systemConfigLogId)
    : firstQueryValue(route.query.logId);
  if (hasSystemConfigLogIdQuery) {
    const normalizedLogId = extractSystemConfigLogId(systemConfigLogId);
    if (activeTab.value !== 'systemConfig') {
      activeTab.value = 'systemConfig';
      changed = true;
    }
    if (systemConfigQuery.logId !== normalizedLogId) {
      systemConfigQuery.logId = normalizedLogId;
      systemConfigPage.value = 1;
      changed = true;
    }
  } else if (tab === 'systemConfig' && systemConfigQuery.logId) {
    systemConfigQuery.logId = '';
    systemConfigPage.value = 1;
    changed = true;
  }
  const hasNotificationIdQuery = hasRouteQueryKey('notificationId');
  const notificationId = firstQueryValue(route.query.notificationId);
  if (hasNotificationIdQuery) {
    const normalizedNotificationId = extractNotificationAuditId(notificationId);
    if (activeTab.value !== 'notification') {
      activeTab.value = 'notification';
      changed = true;
    }
    if (notificationQuery.notificationId !== normalizedNotificationId) {
      notificationQuery.notificationId = normalizedNotificationId;
      notificationPage.value = 1;
      changed = true;
    }
  } else if (tab === 'notification' && notificationQuery.notificationId) {
    notificationQuery.notificationId = '';
    notificationPage.value = 1;
    changed = true;
  }
  if (hydrateNotificationRelationQuery(tab)) {
    changed = true;
  }
  return changed;
}

function hydrateNotificationRelationQuery(tab: string) {
  let changed = false;
  const hasRelatedTypeQuery = hasRouteQueryKey('relatedType');
  const hasRelatedIdQuery = hasRouteQueryKey('relatedId');
  if (!hasRelatedTypeQuery && !hasRelatedIdQuery) return false;
  if (activeTab.value !== 'notification') {
    activeTab.value = 'notification';
    changed = true;
  }
  const relatedType = hasRelatedTypeQuery ? firstQueryValue(route.query.relatedType).trim() : '';
  const relatedId = hasRelatedIdQuery ? firstQueryValue(route.query.relatedId).trim() : '';
  if (notificationQuery.relatedType !== relatedType) {
    notificationQuery.relatedType = relatedType;
    changed = true;
  }
  if (notificationQuery.relatedId !== relatedId) {
    notificationQuery.relatedId = relatedId;
    changed = true;
  }
  if (changed || tab === 'notification') {
    notificationPage.value = 1;
  }
  return changed;
}

function hasRouteQueryKey(key: string) {
  return Object.prototype.hasOwnProperty.call(route.query, key);
}

function firstQueryValue(value: unknown) {
  if (Array.isArray(value)) {
    return value[0] ? String(value[0]) : '';
  }
  return value === undefined || value === null ? '' : String(value);
}

function isLogTab(value: string): value is LogTab {
  return logTabs.includes(value as LogTab);
}

async function copyOperationLogId(logId?: number | string | null) {
  try {
    const value = await copyOperationLogIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`操作日志 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制操作日志 ID 失败');
  }
}

async function copyOperationLogLink(logId?: number | string | null) {
  try {
    const link = await copyOperationLogLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('操作日志链接已复制');
  } catch {
    ElMessage.error('复制操作日志链接失败');
  }
}

async function copyLoginAuditId(logId?: number | string | null) {
  try {
    const value = await copyLoginAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`登录审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制登录审计 ID 失败');
  }
}

async function copyLoginAuditLink(logId?: number | string | null) {
  try {
    const link = await copyLoginAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('登录审计链接已复制');
  } catch {
    ElMessage.error('复制登录审计链接失败');
  }
}

async function copyApprovalReminderAuditId(logId?: number | string | null) {
  try {
    const value = await copyApprovalReminderLogIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`审批提醒审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制审批提醒审计 ID 失败');
  }
}

async function copyApprovalReminderAuditLink(logId?: number | string | null) {
  try {
    const link = await copyApprovalReminderAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('审批提醒审计链接已复制');
  } catch {
    ElMessage.error('复制审批提醒审计链接失败');
  }
}

async function copyExamApprovalAuditId(logId?: number | string | null) {
  try {
    const value = await copyExamApprovalAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`考试审批审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制考试审批审计 ID 失败');
  }
}

async function copyExamApprovalAuditLink(logId?: number | string | null) {
  try {
    const link = await copyExamApprovalAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('考试审批审计链接已复制');
  } catch {
    ElMessage.error('复制考试审批审计链接失败');
  }
}

async function copyScoreReleaseAuditId(logId?: number | string | null) {
  try {
    const value = await copyScoreReleaseAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`成绩发布审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制成绩发布审计 ID 失败');
  }
}

async function copyScoreReleaseAuditLink(logId?: number | string | null) {
  try {
    const link = await copyScoreReleaseAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('成绩发布审计链接已复制');
  } catch {
    ElMessage.error('复制成绩发布审计链接失败');
  }
}

async function copyScoreAppealAuditId(logId?: number | string | null) {
  try {
    const value = await copyScoreAppealAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`成绩申诉审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制成绩申诉审计 ID 失败');
  }
}

async function copyScoreAppealAuditLink(logId?: number | string | null) {
  try {
    const link = await copyScoreAppealAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('成绩申诉审计链接已复制');
  } catch {
    ElMessage.error('复制成绩申诉审计链接失败');
  }
}

async function copyReviewScoreAuditId(logId?: number | string | null) {
  try {
    const value = await copyReviewScoreAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`阅卷评分审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制阅卷评分审计 ID 失败');
  }
}

async function copyReviewScoreAuditLink(logId?: number | string | null) {
  try {
    const link = await copyReviewScoreAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('阅卷评分审计链接已复制');
  } catch {
    ElMessage.error('复制阅卷评分审计链接失败');
  }
}

async function copyQuestionReviewAuditId(logId?: number | string | null) {
  try {
    const value = await copyQuestionReviewAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`题目审核审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制题目审核审计 ID 失败');
  }
}

async function copyQuestionReviewAuditLink(logId?: number | string | null) {
  try {
    const link = await copyQuestionReviewAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('题目审核审计链接已复制');
  } catch {
    ElMessage.error('复制题目审核审计链接失败');
  }
}

async function copySystemConfigAuditId(logId?: number | string | null) {
  try {
    const value = await copySystemConfigAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`系统配置审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制系统配置审计 ID 失败');
  }
}

async function copySystemConfigAuditLink(logId?: number | string | null) {
  try {
    const link = await copySystemConfigAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('系统配置审计链接已复制');
  } catch {
    ElMessage.error('复制系统配置审计链接失败');
  }
}

async function copyNotificationAuditId(notificationId?: number | string | null) {
  try {
    const value = await copyNotificationAuditIdToClipboard(notificationId);
    if (!value) return;
    ElMessage.success(`通知审计 ID 已复制：${value}`);
  } catch {
    ElMessage.error('复制通知审计 ID 失败');
  }
}

async function copyNotificationAuditLink(notificationId?: number | string | null) {
  try {
    const link = await copyNotificationAuditLinkToClipboard(notificationId);
    if (!link) return;
    ElMessage.success('通知审计链接已复制');
  } catch {
    ElMessage.error('复制通知审计链接失败');
  }
}

async function copyNotificationRelatedAuditLink(relatedType?: string | null, relatedId?: number | string | null) {
  try {
    const link = await copyNotificationRelatedAuditLinkToClipboard(relatedType, relatedId);
    if (!link) return;
    ElMessage.success('关联通知审计链接已复制');
  } catch {
    ElMessage.error('复制关联通知审计链接失败');
  }
}

function isSuccess(value: boolean | number) {
  return value === true || value === 1;
}

function loginSuccess(value?: boolean | number | null) {
  return value === true || value === 1;
}

function notificationReadText(value: number | boolean) {
  return value === true || value === 1 ? '已读' : '未读';
}

function notificationReadTag(value: number | boolean) {
  return value === true || value === 1 ? 'success' : 'warning';
}

function sceneText(scene?: string) {
  return sceneOptions.find((item) => item.value === scene)?.label || scene || '-';
}

function approvalReminderStatusText(status?: string | null) {
  const map: Record<string, string> = {
    SENT: '已发送',
    SKIPPED_DISABLED: '已禁用',
    SKIPPED_EMPTY: '无超期',
    SKIPPED_NO_RECIPIENT: '无接收人',
    SKIPPED_COOLDOWN: '冷却期',
    SKIPPED_SCHEDULE_DISABLED: '计划已禁用',
    SKIPPED_SCHEDULE_INTERVAL: '等待间隔'
  };
  return status ? map[status] || status : '-';
}

function approvalReminderStatusTag(status?: string | null) {
  if (status === 'SENT') return 'success';
  if (status === 'SKIPPED_COOLDOWN' || status === 'SKIPPED_SCHEDULE_INTERVAL') return 'warning';
  if (status === 'SKIPPED_DISABLED' || status === 'SKIPPED_NO_RECIPIENT' || status === 'SKIPPED_SCHEDULE_DISABLED') return 'danger';
  return 'info';
}

function approvalReminderSourceText(source?: string | null) {
  const map: Record<string, string> = {
    MANUAL: '手动',
    SCHEDULE: '计划任务'
  };
  return source ? map[source] || source : '-';
}

function durationText(value?: number | null) {
  if (value === null || value === undefined) return '-';
  if (value < 1000) return `${value}ms`;
  return `${(value / 1000).toFixed(1)}s`;
}

function questionReviewActionText(action?: string | null) {
  const map: Record<string, string> = {
    CREATE: '创建',
    EDIT: '编辑',
    SUBMIT_REVIEW: '提交审核',
    APPROVE: '通过',
    REJECT: '驳回',
    ONLINE: '上线',
    OFFLINE: '下线',
    DELETE: '删除'
  };
  return action ? map[action] || action : '-';
}

function questionReviewActionTag(action?: string | null) {
  if (action === 'APPROVE' || action === 'ONLINE') return 'success';
  if (action === 'REJECT' || action === 'DELETE') return 'danger';
  if (action === 'SUBMIT_REVIEW') return 'warning';
  return 'info';
}

function questionReviewStatusText(status?: string | null) {
  const map: Record<string, string> = {
    DRAFT: '草稿',
    PENDING: '待审核',
    APPROVED: '已通过',
    REJECTED: '已驳回'
  };
  return status ? map[status] || status : '-';
}

function questionPublishStatusText(status?: number | null) {
  if (status === null || status === undefined) return '-';
  return status === 1 ? '已上线' : '已下线';
}

function examApprovalActionText(action: string) {
  const map: Record<string, string> = {
    SUBMIT: '提交审批',
    RESUBMIT: '重新提交',
    APPROVE: '通过',
    REJECT: '驳回',
    DIRECT_PUBLISH: '直接发布'
  };
  return map[action] || action;
}

function examApprovalActionTag(action: string) {
  if (action === 'APPROVE' || action === 'DIRECT_PUBLISH') return 'success';
  if (action === 'REJECT') return 'danger';
  if (action === 'RESUBMIT') return 'warning';
  return 'info';
}

function examApprovalStatusText(status?: number | null) {
  if (status === null || status === undefined) return '-';
  const map: Record<number, string> = {
    0: '待审批',
    1: '已发布',
    2: '已结束',
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

function scoreAppealActionText(action?: string) {
  const map: Record<string, string> = {
    SUBMIT: '提交申诉',
    REPLY: '处理回复',
    RECHECK_OPEN: '重开复核',
    CLOSE_RECHECK: '完成复核'
  };
  return action ? map[action] || action : '-';
}

function scoreAppealActionTag(action?: string) {
  if (action === 'SUBMIT') return 'warning';
  if (action === 'RECHECK_OPEN') return 'primary';
  if (action === 'CLOSE_RECHECK') return 'success';
  return 'info';
}

function scoreAppealStatusText(status?: number | null) {
  if (status === null || status === undefined) return '-';
  const map: Record<number, string> = {
    0: '待处理',
    1: '已回复',
    2: '已关闭'
  };
  return map[status] || String(status);
}

function scoreAppealHandlingResultText(value?: string | null) {
  const map: Record<string, string> = {
    MAINTAINED: '维持原分',
    RECHECK_REQUIRED: '需要复核',
    ADJUSTED_OFFLINE: '已线下调整'
  };
  return value ? map[value] || value : '-';
}
</script>

<style scoped>
.system-log {
  display: grid;
  gap: 16px;
}

.log-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.log-topbar h3 {
  margin: 0 0 4px;
  color: #111827;
  font-size: 22px;
}

.log-topbar p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.log-workbench {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  overflow-x: auto;
}

.log-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.log-tabs :deep(.el-table) {
  max-width: 100%;
}

.score-release-id-cell,
.approval-reminder-id-cell,
.exam-approval-id-cell,
.score-appeal-id-cell,
.review-score-id-cell,
.question-review-id-cell,
.operation-log-id-cell,
.login-log-id-cell,
.system-config-id-cell,
.notification-id-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.score-release-id-cell span,
.approval-reminder-id-cell span,
.exam-approval-id-cell span,
.score-appeal-id-cell span,
.review-score-id-cell span,
.question-review-id-cell span,
.operation-log-id-cell span,
.login-log-id-cell span,
.system-config-id-cell span,
.notification-id-cell span {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  color: #111827;
}

.notification-related-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.notification-related-cell span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-release-id-cell .el-button,
.approval-reminder-id-cell .el-button,
.exam-approval-id-cell .el-button,
.score-appeal-id-cell .el-button,
.review-score-id-cell .el-button,
.question-review-id-cell .el-button,
.operation-log-id-cell .el-button,
.login-log-id-cell .el-button,
.system-config-id-cell .el-button,
.notification-id-cell .el-button,
.notification-related-cell .el-button {
  padding: 0;
}

.operation-log-toolbar,
.ai-log-toolbar {
  display: grid;
  grid-template-columns: minmax(160px, 220px) minmax(140px, 180px) auto auto minmax(0, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.operation-log-toolbar {
  grid-template-columns: minmax(150px, 210px) minmax(180px, 260px) minmax(120px, 160px) minmax(120px, 160px) minmax(240px, 320px) auto auto auto minmax(0, 1fr);
}

.ai-log-toolbar {
  grid-template-columns: minmax(180px, 260px) minmax(140px, 190px) minmax(100px, 140px) minmax(240px, 320px) auto auto auto minmax(0, 1fr);
}

.score-release-toolbar,
.approval-reminder-toolbar,
.exam-approval-toolbar,
.score-appeal-toolbar,
.review-score-toolbar,
.question-review-toolbar,
.login-log-toolbar,
.system-config-toolbar,
.notification-log-toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 260px) minmax(120px, 160px) minmax(140px, 180px) auto auto auto minmax(0, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.notification-log-toolbar {
  grid-template-columns:
    minmax(180px, 260px)
    minmax(120px, 160px)
    minmax(140px, 180px)
    minmax(110px, 140px)
    minmax(120px, 150px)
    minmax(240px, 320px)
    auto
    auto
    auto
    minmax(0, 1fr);
}

.question-review-toolbar {
  grid-template-columns:
    minmax(170px, 230px)
    minmax(120px, 150px)
    minmax(200px, 280px)
    minmax(130px, 170px)
    minmax(130px, 170px)
    minmax(110px, 140px)
    minmax(120px, 150px)
    minmax(240px, 320px)
    auto
    auto
    auto
    minmax(0, 1fr);
}

.system-config-toolbar {
  grid-template-columns:
    minmax(170px, 230px)
    minmax(180px, 250px)
    minmax(120px, 150px)
    minmax(170px, 230px)
    minmax(110px, 140px)
    minmax(240px, 320px)
    auto
    auto
    auto
    minmax(0, 1fr);
}

.login-log-toolbar {
  grid-template-columns:
    minmax(150px, 210px)
    minmax(180px, 260px)
    minmax(120px, 160px)
    minmax(120px, 150px)
    minmax(110px, 140px)
    minmax(240px, 320px)
    auto
    auto
    auto
    minmax(0, 1fr);
}

.log-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .log-topbar,
  .log-pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .operation-log-toolbar,
  .ai-log-toolbar,
  .score-release-toolbar,
  .approval-reminder-toolbar,
  .exam-approval-toolbar,
  .score-appeal-toolbar,
  .review-score-toolbar,
  .question-review-toolbar,
  .login-log-toolbar,
  .system-config-toolbar,
  .notification-log-toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 560px) {
  .operation-log-toolbar,
  .ai-log-toolbar,
  .approval-reminder-toolbar,
  .exam-approval-toolbar,
  .review-score-toolbar,
  .question-review-toolbar,
  .login-log-toolbar,
  .system-config-toolbar,
  .notification-log-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
