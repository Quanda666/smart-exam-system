<template>
  <div class="mp-page">
    <el-alert
      v-if="lastReviewScoreAudit"
      class="review-score-audit"
      type="success"
      :closable="true"
      show-icon
      @close="lastReviewScoreAudit = null"
    >
      <template #title>
        <div class="review-score-audit-content">
          <span>{{ lastReviewScoreAudit.action }} audit recorded: {{ reviewScoreAuditText(lastReviewScoreAudit.reviewScoreLogIds) }}</span>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestReviewScoreAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestReviewScoreAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

    <el-alert
      v-if="lastScoreAppealAudit"
      class="score-appeal-audit"
      type="success"
      :closable="true"
      show-icon
      @close="lastScoreAppealAudit = null"
    >
      <template #title>
        <div class="score-appeal-audit-content">
          <span>{{ lastScoreAppealAudit.action }} audit recorded: {{ scoreAppealAuditText(lastScoreAppealAudit.scoreAppealLogIds) }}</span>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestScoreAppealAuditId">Copy audit ID</el-button>
          <el-button link type="primary" :icon="DocumentCopy" @click="copyLatestScoreAppealAuditLink">Copy audit link</el-button>
        </div>
      </template>
    </el-alert>

    <el-alert
      v-if="reviewRouteFilterActive"
      class="review-route-filter"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #title>
        <div class="review-route-filter-content">
          <span>{{ reviewRouteFilterText }}</span>
          <el-button link type="primary" @click="clearReviewRouteFilters">Clear filters</el-button>
        </div>
      </template>
    </el-alert>

    <el-alert
      v-if="lastReviewReleaseHandoff"
      class="review-release-handoff"
      :type="lastReviewReleaseHandoff.ready ? 'success' : 'warning'"
      :closable="true"
      show-icon
      @close="lastReviewReleaseHandoff = null"
    >
      <template #title>
        <div class="review-release-handoff-content">
          <span>{{ reviewReleaseHandoffText }}</span>
          <el-button link type="primary" @click="openReviewReleaseReadiness">Score Readiness</el-button>
        </div>
      </template>
    </el-alert>

    <div class="mp-table-card review-progress-card">
      <div class="review-progress-toolbar">
        <h3>Review progress</h3>
        <el-button link type="primary" :icon="Refresh" :loading="progressLoading" @click="loadReviewProgress">Refresh</el-button>
      </div>
      <el-table v-loading="progressLoading" :data="reviewProgress" :row-class-name="reviewProgressRowClassName">
        <el-table-column prop="examName" label="Exam" min-width="180" />
        <el-table-column label="Attempts" width="160">
          <template #default="scope">
            {{ numberText((scope.row as ReviewProgress).pendingAttemptCount) }} pending /
            {{ numberText((scope.row as ReviewProgress).attemptCount) }} total
          </template>
        </el-table-column>
        <el-table-column label="Answers" width="170">
          <template #default="scope">
            {{ numberText((scope.row as ReviewProgress).pendingAnswerCount) }} pending /
            {{ numberText((scope.row as ReviewProgress).reviewableAnswerCount) }} total
            <div v-if="numberText((scope.row as ReviewProgress).pendingRecheckAnswerCount) > 0" class="recheck-count">
              {{ numberText((scope.row as ReviewProgress).pendingRecheckAnswerCount) }} recheck
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Progress" min-width="180">
          <template #default="scope">
            <el-progress
              :percentage="reviewProgressPercent(scope.row as ReviewProgress)"
              :status="reviewProgressStatus(scope.row as ReviewProgress)"
            />
          </template>
        </el-table-column>
        <el-table-column label="Release gate" width="130">
          <template #default="scope">
            <el-tag :type="reviewBlocksRelease(scope.row as ReviewProgress) ? 'warning' : 'success'">
              {{ reviewBlocksRelease(scope.row as ReviewProgress) ? 'Blocked' : 'Clear' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Action" width="250" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="focusReviewExam(scope.row as ReviewProgress)">
              Focus
            </el-button>
            <el-button
              link
              type="primary"
              :disabled="!preferredReviewAttemptId(scope.row as ReviewProgress)"
              @click="startReview(Number(preferredReviewAttemptId(scope.row as ReviewProgress)))"
            >
              Review
            </el-button>
            <el-button
              v-if="!reviewBlocksRelease(scope.row as ReviewProgress)"
              link
              type="success"
              @click="openReviewProgressReadiness(scope.row as ReviewProgress)"
            >
              Readiness
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!progressLoading && reviewProgress.length === 0" description="No review progress yet" :image-size="90" />
    </div>

    <div class="mp-table-card">
      <div class="pending-review-toolbar">
        <h3>Pending reviews</h3>
        <div class="pending-review-filter">
          <el-radio-group v-model="reviewTaskTypeFilter" size="small" @change="updateReviewTaskTypeFilter">
            <el-radio-button label="ALL">All</el-radio-button>
            <el-radio-button label="RECHECK">Recheck</el-radio-button>
            <el-radio-button label="STANDARD">Standard</el-radio-button>
          </el-radio-group>
          <el-tag v-if="focusedReviewExamId" type="warning">
            Exam #{{ focusedReviewExamId }}
          </el-tag>
          <el-button v-if="focusedReviewExamId" link type="primary" @click="clearReviewExamFilter">Clear filter</el-button>
        </div>
      </div>
      <el-table v-loading="loading" :data="pendingReviews" :row-class-name="pendingReviewRowClassName">
        <el-table-column label="Type" width="110">
          <template #default="scope">
            <el-tag :type="isRecheckReview(scope.row as PendingReview) ? 'warning' : 'info'">
              {{ isRecheckReview(scope.row as PendingReview) ? 'Recheck' : 'Standard' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="examName" label="Exam Name" min-width="180" />
        <el-table-column prop="studentName" label="Student" width="120" />
        <el-table-column prop="pendingCount" label="Pending Answers" width="120" />
        <el-table-column label="Answer stats" min-width="180">
          <template #default="scope">{{ reviewAnswerStatsText(scope.row as PendingReview) }}</template>
        </el-table-column>
        <el-table-column label="Action" width="190" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="startReview(scope.row.attemptId)">Review</el-button>
            <el-button
              link
              type="info"
              @click="openScoreLogs(scope.row.attemptId, scope.row.examName, scope.row.studentName, scope.row.pendingCount)"
            >
              Logs
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && pendingReviews.length === 0" description="No pending reviews" :image-size="100" />
    </div>

    <div class="mp-table-card appeal-card">
      <div class="appeal-toolbar">
        <h3>Score Appeals</h3>
        <div class="appeal-filters">
          <el-radio-group v-model="appealStatusFilter" size="small" @change="loadAppeals">
            <el-radio-button :label="0">Pending</el-radio-button>
            <el-radio-button :label="1">Replied</el-radio-button>
            <el-radio-button :label="-1">All</el-radio-button>
          </el-radio-group>
          <el-select
            v-model="appealHandlingResultFilter"
            size="small"
            style="width: 150px"
            @change="loadAppeals"
          >
            <el-option label="All Results" value="" />
            <el-option label="Maintain Score" value="MAINTAINED" />
            <el-option label="Recheck Required" value="RECHECK_REQUIRED" />
            <el-option label="Adjusted Offline" value="ADJUSTED_OFFLINE" />
          </el-select>
          <el-tag v-if="focusedAppealExamId" type="warning">
            Exam #{{ focusedAppealExamId }}
          </el-tag>
          <el-button v-if="focusedAppealExamId" link type="primary" @click="clearAppealExamFilter">Clear exam</el-button>
        </div>
      </div>
      <el-table v-loading="appealLoading" :data="appeals" :row-class-name="appealRowClassName">
        <el-table-column prop="examName" label="Exam Name" min-width="180" />
        <el-table-column prop="studentName" label="Student" width="120" />
        <el-table-column label="Appeal Target" min-width="180">
          <template #default="scope">{{ scope.row.questionStem || 'Whole paper' }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="Reason" min-width="220" show-overflow-tooltip />
        <el-table-column label="Status" width="90">
          <template #default="scope">
            <el-tag :type="appealStatusType(scope.row.status)">{{ appealStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Handling Result" width="130">
          <template #default="scope">
            <el-tag v-if="scope.row.handlingResult" type="info">
              {{ appealHandlingResultText(scope.row.handlingResult) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="handlerName" label="Handler" width="110">
          <template #default="scope">{{ scope.row.handlerName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="handledAt" label="Handled At" min-width="150">
          <template #default="scope">{{ scope.row.handledAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="recheckedAt" label="Rechecked At" min-width="150">
          <template #default="scope">{{ scope.row.recheckedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="Action" width="220" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.status === 0" link type="primary" @click="openReply(scope.row as ScoreAppeal)">Handle</el-button>
            <el-button v-if="canCloseRecheck(scope.row as ScoreAppeal)" link type="primary" @click="openRecheckReview(scope.row as ScoreAppeal)">Recheck Review</el-button>
            <el-button v-if="canCloseRecheck(scope.row as ScoreAppeal)" link type="warning" @click="closeRecheck(scope.row as ScoreAppeal)">Complete Recheck</el-button>
            <el-button v-if="scope.row.status !== 0" link type="primary" @click="openReply(scope.row as ScoreAppeal)">View</el-button>
            <el-button link type="info" @click="openAppealLogs(scope.row as ScoreAppeal)">Logs</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!appealLoading && appeals.length === 0" description="No score appeals" :image-size="90" />
    </div>

    <el-drawer v-model="drawerVisible" :title="`Review Paper - ${reviewDetails?.examName || ''}`" size="60%">
      <div v-if="reviewDetails" v-loading="reviewing">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          <template #title>
            <span>
              Student: {{ reviewDetails.studentName }} | Pending answers: {{ reviewDetails.answers.length }}
              | Answer stats: {{ reviewAnswerStatsText(reviewDetails) }}
            </span>
          </template>
        </el-alert>

        <div v-for="(answer, index) in reviewDetails.answers" :key="answer.answerRecordId" class="answer-card">
          <el-card shadow="never">
            <template #header>
              <div class="answer-header">
                <span><strong>Question {{ index + 1 }}</strong></span>
                <el-tag v-if="reviewScores[answer.answerRecordId] !== undefined" type="success" size="small">
                  Scored: {{ reviewScores[answer.answerRecordId] }}
                </el-tag>
              </div>
            </template>

            <div class="answer-body">
              <div class="answer-section">
                <div class="section-label">Stem</div>
                <div class="section-content">{{ answer.stem }}</div>
              </div>

              <div class="answer-section">
                <div class="section-label">Student Answer</div>
                <div class="section-content student-answer">{{ answer.studentAnswer || 'Unanswered' }}</div>
              </div>

              <div v-if="answer.correctAnswer" class="answer-section">
                <div class="section-label">Reference Answer</div>
                <div class="section-content correct-answer">{{ answer.correctAnswer }}</div>
              </div>

              <div class="answer-section">
                <div class="section-label">Score</div>
                <el-input-number
                  v-model="reviewScores[answer.answerRecordId]"
                  :min="0"
                  :max="Number(answer.maxScore || 0)"
                  :precision="1"
                  size="large"
                  style="width: 160px;"
                />
                <span class="max-score-hint"> / {{ answer.maxScore || 0 }} points</span>
                <el-button
                  type="primary"
                  plain
                  size="small"
                  style="margin-left: 12px;"
                  :loading="aiLoading[answer.answerRecordId]"
                  @click="aiSuggestReview(answer)"
                >
                  AI Suggest
                </el-button>
              </div>

              <div class="answer-section">
                <div class="section-label">Comment (optional)</div>
                <el-input
                  v-model="reviewComments[answer.answerRecordId]"
                  type="textarea"
                  :rows="2"
                  placeholder="Enter review comments, such as partially correct answer or missing key points."
                  maxlength="200"
                  show-word-limit
                />
              </div>
            </div>
          </el-card>
        </div>

        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">Cancel</el-button>
          <el-button type="primary" :loading="submitting" @click="submitReview">Submit Review</el-button>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="appealDialogVisible" title="Handle Score Appeal" width="560px">
      <div v-if="activeAppeal" class="appeal-detail">
        <p><strong>Exam:</strong> {{ activeAppeal.examName }}</p>
        <p><strong>Student:</strong> {{ activeAppeal.studentName }}</p>
        <p><strong>Target:</strong> {{ activeAppeal.questionStem || 'Whole paper' }}</p>
        <p><strong>Reason:</strong> {{ activeAppeal.reason }}</p>
        <p v-if="activeAppeal.status !== 0"><strong>Handling Result:</strong> {{ appealHandlingResultText(activeAppeal.handlingResult) }}</p>
        <p v-if="activeAppeal.recheckNote"><strong>Recheck Note:</strong> {{ activeAppeal.recheckNote }}</p>
        <p v-if="activeAppeal.recheckerName || activeAppeal.recheckedAt"><strong>Recheck Record:</strong> {{ activeAppeal.recheckerName || '-' }} {{ activeAppeal.recheckedAt || '' }}</p>
        <el-form label-position="top">
          <el-form-item label="Handling Result" required>
            <el-select v-model="appealHandlingResult" :disabled="activeAppeal.status !== 0" style="width: 100%">
              <el-option label="Maintain Score" value="MAINTAINED" />
              <el-option label="Recheck Required" value="RECHECK_REQUIRED" />
              <el-option label="Adjusted Offline" value="ADJUSTED_OFFLINE" />
            </el-select>
          </el-form-item>
          <el-form-item label="Handling Opinion" required>
            <el-input
              v-model="appealReply"
              type="textarea"
              :rows="5"
              maxlength="1000"
              show-word-limit
              :disabled="activeAppeal.status !== 0"
            />
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="appealDialogVisible = false">Close</el-button>
        <el-button v-if="activeAppeal?.status === 0" type="primary" :loading="appealSubmitting" @click="submitAppealReply">Submit Handling</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="appealLogVisible" title="Score Appeal Logs" size="min(760px, 94vw)">
      <div v-if="activeLogAppeal" class="appeal-log-head">
        <strong>{{ activeLogAppeal.examName }}</strong>
        <span>{{ activeLogAppeal.studentName }} / {{ activeLogAppeal.questionStem || 'Whole paper' }}</span>
      </div>
      <div class="appeal-log-toolbar">
        <el-button type="success" plain :loading="appealLogExporting" @click="exportAppealLogs">Export</el-button>
      </div>
      <el-table v-loading="appealLogLoading" :data="appealLogs" border>
        <el-table-column label="Log ID" width="145">
          <template #default="scope">
            <div class="appeal-log-id-cell">
              <span>#{{ scope.row.id }}</span>
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy score appeal audit ID"
                aria-label="Copy score appeal audit ID"
                @click="copyScoreAppealAuditId(scope.row.id)"
              />
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy score appeal audit link"
                aria-label="Copy score appeal audit link"
                @click="copyScoreAppealAuditLink(scope.row.id)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Action" width="120">
          <template #default="scope">{{ appealLogActionText(scope.row.action) }}</template>
        </el-table-column>
        <el-table-column label="Status Change" width="120">
          <template #default="scope">
            {{ appealLogStatusText(scope.row.statusFrom) }} -> {{ appealLogStatusText(scope.row.statusTo) }}
          </template>
        </el-table-column>
        <el-table-column label="Handling Result" width="120">
          <template #default="scope">{{ appealHandlingResultText(scope.row.handlingResult) }}</template>
        </el-table-column>
        <el-table-column prop="note" label="Note" min-width="200" show-overflow-tooltip />
        <el-table-column prop="actorName" label="Actor" width="120" />
        <el-table-column prop="createdAt" label="Time" min-width="150" />
      </el-table>
      <el-empty v-if="!appealLogLoading && appealLogs.length === 0" description="No appeal logs" />
    </el-drawer>

    <el-drawer v-model="recheckCloseVisible" title="Close Recheck" size="min(920px, 96vw)">
      <div v-loading="recheckReadinessLoading">
        <template v-if="activeRecheckReadiness">
          <div class="recheck-readiness-head">
            <div>
              <strong>{{ activeRecheckReadiness.examName }}</strong>
              <span>{{ activeRecheckReadiness.studentName }} / {{ activeRecheckReadiness.questionStem || 'Whole paper' }}</span>
            </div>
            <el-tag :type="isRecheckCloseAllowed(activeRecheckReadiness) ? 'success' : 'warning'">
              {{ isRecheckCloseAllowed(activeRecheckReadiness) ? 'Ready to close' : 'Blocked' }}
            </el-tag>
          </div>

          <div class="recheck-readiness-stats">
            <div>
              <span>Required</span>
              <strong>{{ activeRecheckReadiness.requiredRecheckAnswerCount || 0 }}</strong>
            </div>
            <div>
              <span>Reviewed</span>
              <strong>{{ activeRecheckReadiness.reviewedRecheckAnswerCount || 0 }}</strong>
            </div>
            <div>
              <span>Pending</span>
              <strong>{{ activeRecheckReadiness.pendingRecheckAnswerCount || 0 }}</strong>
            </div>
            <div>
              <span>Score Logs</span>
              <strong>{{ activeRecheckReadiness.reviewScoreLogCount || 0 }}</strong>
            </div>
            <div>
              <span>Attempt</span>
              <strong>{{ recheckFinalizedText(activeRecheckReadiness) }}</strong>
            </div>
          </div>

          <el-alert
            v-if="!isRecheckCloseAllowed(activeRecheckReadiness)"
            type="warning"
            :closable="false"
            show-icon
            class="recheck-readiness-alert"
          >
            <template #title>
              <span>{{ (activeRecheckReadiness.closeBlockers || []).map(recheckCloseBlockerText).join(' ') || 'Recheck is not ready to close.' }}</span>
            </template>
          </el-alert>

          <el-table :data="activeRecheckReadiness.answers || []" border>
            <el-table-column prop="questionId" label="Question" width="100" />
            <el-table-column label="Status" width="100">
              <template #default="scope">{{ recheckAnswerStatusText(scope.row as ScoreAppealRecheckAnswer) }}</template>
            </el-table-column>
            <el-table-column label="Score" width="160">
              <template #default="scope">
                {{ scope.row.oldScore ?? '-' }} -> {{ scope.row.newScore ?? scope.row.currentScore ?? '-' }} / {{ scope.row.maxScore ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="Audit" width="110">
              <template #default="scope">
                <span v-if="scope.row.reviewScoreLogId">#{{ scope.row.reviewScoreLogId }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="reviewerName" label="Reviewer" width="130">
              <template #default="scope">{{ scope.row.reviewerName || scope.row.reviewerId || '-' }}</template>
            </el-table-column>
            <el-table-column prop="reviewedAt" label="Reviewed At" min-width="150">
              <template #default="scope">{{ scope.row.reviewedAt || '-' }}</template>
            </el-table-column>
            <el-table-column prop="stem" label="Stem" min-width="240" show-overflow-tooltip />
          </el-table>

          <el-form label-position="top" class="recheck-close-form">
            <el-form-item label="Close note" required>
              <el-input
                v-model="recheckCloseNote"
                type="textarea"
                :rows="4"
                maxlength="1000"
                show-word-limit
                :disabled="!isRecheckCloseAllowed(activeRecheckReadiness)"
                placeholder="Confirm the recheck result, scoring evidence, and final conclusion."
              />
            </el-form-item>
          </el-form>
        </template>
        <el-empty v-else-if="!recheckReadinessLoading" description="No recheck readiness data" :image-size="90" />
      </div>
      <template #footer>
        <el-button @click="recheckCloseVisible = false">Close</el-button>
        <el-button
          type="primary"
          :disabled="!isRecheckCloseAllowed(activeRecheckReadiness)"
          :loading="recheckCloseSubmitting"
          @click="submitCloseRecheck"
        >
          Close Recheck
        </el-button>
      </template>
    </el-drawer>

    <el-drawer v-model="scoreLogVisible" title="Review Score Logs" size="min(880px, 96vw)">
      <div v-if="activeScoreLogAttempt" class="appeal-log-head">
        <strong>{{ activeScoreLogAttempt.examName }}</strong>
        <span>{{ activeScoreLogAttempt.studentName }} / attempt {{ activeScoreLogAttempt.attemptId }}</span>
      </div>
      <div class="score-log-toolbar">
        <el-button type="success" plain :loading="scoreLogExporting" @click="exportScoreLogs">Export</el-button>
      </div>
      <el-table v-loading="scoreLogLoading" :data="scoreLogs" border>
        <el-table-column label="Log ID" width="145">
          <template #default="scope">
            <div class="review-score-log-id-cell">
              <span>#{{ scope.row.id }}</span>
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy review score audit ID"
                aria-label="Copy review score audit ID"
                @click="copyReviewScoreAuditId(scope.row.id)"
              />
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy review score audit link"
                aria-label="Copy review score audit link"
                @click="copyReviewScoreAuditLink(scope.row.id)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="Time" min-width="150" />
        <el-table-column prop="questionId" label="Question" width="100" />
        <el-table-column prop="answerRecordId" label="Answer" width="100" />
        <el-table-column label="Score" width="150">
          <template #default="scope">
            {{ scope.row.oldScore ?? 0 }} -> {{ scope.row.newScore }} / {{ scope.row.maxScore }}
          </template>
        </el-table-column>
        <el-table-column prop="reviewerName" label="Reviewer" width="120">
          <template #default="scope">{{ scope.row.reviewerName || scope.row.reviewerId || '-' }}</template>
        </el-table-column>
        <el-table-column prop="comment" label="Comment" min-width="220" show-overflow-tooltip />
      </el-table>
      <el-empty v-if="!scoreLogLoading && scoreLogs.length === 0" description="No review score logs" />
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, reactive, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { DocumentCopy, Refresh } from '@element-plus/icons-vue';
import {
  getPendingReviews,
  getReviewDetails,
  getScoreAppealRecheckReadiness,
  closeScoreAppealRecheck,
  exportScoreAppealLogs,
  exportReviewScoreLogs,
  listScoreAppealLogs,
  listScoreAppeals,
  listReviewProgress,
  listReviewScoreLogs,
  replyScoreAppeal,
  submitReview as apiSubmitReview,
  type PendingReview,
  type ReviewProgress,
  type ReviewDetail,
  type ReviewPayload,
  type ReviewTaskType,
  type ReviewScoreLog,
  type ScoreAppeal,
  type ScoreAppealLog,
  type ScoreAppealRecheckAnswer,
  type ScoreAppealRecheckReadiness
} from '../api/review';
import { suggestReview } from '../api/ai';
import {
  copyReviewScoreAuditIdToClipboard,
  copyReviewScoreAuditLinkToClipboard,
  copyScoreAppealAuditIdToClipboard,
  copyScoreAppealAuditLinkToClipboard
} from '../utils/clipboard';
import { ElMessage } from 'element-plus';

const route = useRoute();
const router = useRouter();
const pendingReviews = ref<PendingReview[]>([]);
const reviewProgress = ref<ReviewProgress[]>([]);
const loading = ref(false);
const progressLoading = ref(false);
const drawerVisible = ref(false);
const reviewing = ref(false);
const submitting = ref(false);
const reviewDetails = ref<ReviewDetail | null>(null);
const reviewScores = ref<Record<number, number>>({});
const reviewComments = ref<Record<number, string>>({});
const aiLoading = reactive<Record<number, boolean>>({});
const appeals = ref<ScoreAppeal[]>([]);
const appealLoading = ref(false);
const appealStatusFilter = ref(0);
const appealHandlingResultFilter = ref('');
const appealDialogVisible = ref(false);
const appealSubmitting = ref(false);
const activeAppeal = ref<ScoreAppeal | null>(null);
const appealReply = ref('');
const appealHandlingResult = ref('MAINTAINED');
const appealLogVisible = ref(false);
const appealLogLoading = ref(false);
const appealLogExporting = ref(false);
const appealLogs = ref<ScoreAppealLog[]>([]);
const activeLogAppeal = ref<ScoreAppeal | null>(null);
const recheckCloseVisible = ref(false);
const recheckReadinessLoading = ref(false);
const recheckCloseSubmitting = ref(false);
const activeRecheckAppeal = ref<ScoreAppeal | null>(null);
const activeRecheckReadiness = ref<ScoreAppealRecheckReadiness | null>(null);
const recheckCloseNote = ref('');
const scoreLogVisible = ref(false);
const scoreLogLoading = ref(false);
const scoreLogExporting = ref(false);
const scoreLogs = ref<ReviewScoreLog[]>([]);
const activeScoreLogAttempt = ref<PendingReview | null>(null);
const lastReviewScoreAudit = ref<{ action: string; reviewScoreLogIds: Array<number | string> } | null>(null);
const lastScoreAppealAudit = ref<{ action: string; scoreAppealLogIds: Array<number | string> } | null>(null);
const lastReviewReleaseHandoff = ref<{
  examId: number;
  examName: string;
  ready: boolean;
  pendingAttempts: number;
  pendingAnswers: number;
  activeAttempts: number;
  unscoredCompleted: number;
} | null>(null);
const reviewTaskTypeFilter = ref<'ALL' | ReviewTaskType>('ALL');
const focusedAppealId = computed(() => routeAppealId());
const focusedAppealExamId = computed(() => routeAppealExamId());
const focusedReviewExamId = computed(() => routeReviewExamId());
const reviewRouteFilterActive = computed(() => [
  'reviewExamId',
  'reviewTaskType',
  'appealExamId',
  'appealId',
  'appealStatus',
  'appealHandlingResult'
].some((key) => hasQueryKey(key)));
const reviewRouteFilterText = computed(() => {
  const parts: string[] = [];
  if (focusedReviewExamId.value) parts.push(`Review exam #${focusedReviewExamId.value}`);
  if (reviewTaskTypeFilter.value !== 'ALL') parts.push(`${reviewTaskTypeFilter.value.toLowerCase()} review`);
  if (focusedAppealExamId.value) parts.push(`Appeal exam #${focusedAppealExamId.value}`);
  if (focusedAppealId.value) parts.push(`Appeal #${focusedAppealId.value}`);
  if (hasQueryKey('appealStatus')) parts.push(`appeal ${appealStatusText(appealStatusFilter.value)}`);
  if (appealHandlingResultFilter.value) parts.push(appealHandlingResultText(appealHandlingResultFilter.value));
  return parts.length > 0 ? `Active queue filters: ${parts.join(' / ')}` : 'Active queue filters';
});
const reviewReleaseHandoffText = computed(() => {
  const handoff = lastReviewReleaseHandoff.value;
  if (!handoff) return '';
  if (handoff.ready) {
    return `Review complete for ${handoff.examName}. Scores can move to release readiness.`;
  }
  const blockers: string[] = [];
  if (handoff.pendingAttempts > 0 || handoff.pendingAnswers > 0) {
    blockers.push(`${handoff.pendingAttempts} pending attempt(s), ${handoff.pendingAnswers} pending answer(s)`);
  }
  if (handoff.activeAttempts > 0) blockers.push(`${handoff.activeAttempts} active attempt(s)`);
  if (handoff.unscoredCompleted > 0) blockers.push(`${handoff.unscoredCompleted} missing score(s)`);
  return `Review updated for ${handoff.examName}. Remaining release blockers: ${blockers.join('; ') || 'check readiness'}.`;
});

onMounted(() => {
  applyAppealRouteFilters();
  applyReviewRouteFilters();
  loadReviewProgress();
  loadPendingReviews();
  loadAppeals();
});

watch(
  () => route.query,
  () => {
    applyAppealRouteFilters();
    applyReviewRouteFilters();
    loadReviewProgress();
    loadPendingReviews();
    loadAppeals();
  }
);

async function loadPendingReviews() {
  loading.value = true;
  try {
    pendingReviews.value = (await getPendingReviews(focusedReviewExamId.value, activeReviewTaskType())).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Pending review list failed to load');
  } finally {
    loading.value = false;
  }
}

async function loadReviewProgress() {
  progressLoading.value = true;
  try {
    reviewProgress.value = (await listReviewProgress(focusedReviewExamId.value)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Review progress failed to load');
  } finally {
    progressLoading.value = false;
  }
}

function reviewAnswerStatsText(row: Pick<PendingReview, 'questionCount' | 'answeredCount' | 'unansweredCount'>) {
  const questionTotal = Number(row.questionCount);
  const answered = Number(row.answeredCount);
  const unanswered = Number(row.unansweredCount);
  if (![questionTotal, answered, unanswered].every(Number.isFinite)) return '-';
  return `${answered}/${questionTotal} answered, ${unanswered} unanswered`;
}

function numberText(value: number | string | null | undefined) {
  const parsed = Number(value || 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function activeReviewTaskType(): ReviewTaskType | null {
  return reviewTaskTypeFilter.value === 'ALL' ? null : reviewTaskTypeFilter.value;
}

function isRecheckReview(row: PendingReview) {
  return numberText(row.recheckTaskCount) > 0 || Number(row.recheckRequired || 0) === 1;
}

function reviewProgressPercent(row: ReviewProgress) {
  const raw = Number(row.progressPercent);
  if (Number.isFinite(raw)) {
    return Math.max(0, Math.min(100, Math.round(raw)));
  }
  const total = numberText(row.reviewableAnswerCount);
  if (total <= 0) return 100;
  return Math.max(0, Math.min(100, Math.round((numberText(row.reviewedAnswerCount) * 100) / total)));
}

function reviewProgressStatus(row: ReviewProgress): 'success' | undefined {
  return reviewBlocksRelease(row) ? undefined : 'success';
}

function reviewBlocksRelease(row: ReviewProgress) {
  return Number(row.blocksScoreRelease || 0) === 1
    || numberText(row.pendingAnswerCount) > 0
    || numberText(row.pendingAttemptCount) > 0;
}

function firstPendingAttemptId(row: ReviewProgress) {
  const parsed = Number(row.firstPendingAttemptId || 0);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function preferredReviewAttemptId(row: ReviewProgress) {
  if (reviewTaskTypeFilter.value === 'RECHECK') {
    const recheckAttempt = Number(row.firstRecheckAttemptId || 0);
    if (Number.isFinite(recheckAttempt) && recheckAttempt > 0) return recheckAttempt;
  }
  return firstPendingAttemptId(row);
}

async function focusReviewExam(row: ReviewProgress) {
  await router.replace({
    query: {
      ...route.query,
      reviewExamId: String(row.examId)
    }
  });
}

async function clearReviewExamFilter() {
  const nextQuery = { ...route.query };
  delete nextQuery.reviewExamId;
  await router.replace({ query: nextQuery });
}

async function clearAppealExamFilter() {
  const nextQuery = { ...route.query };
  delete nextQuery.appealExamId;
  await router.replace({ query: nextQuery });
}

async function clearReviewRouteFilters() {
  const nextQuery = { ...route.query };
  delete nextQuery.reviewExamId;
  delete nextQuery.reviewTaskType;
  delete nextQuery.appealExamId;
  delete nextQuery.appealId;
  delete nextQuery.appealStatus;
  delete nextQuery.appealHandlingResult;
  reviewTaskTypeFilter.value = 'ALL';
  appealStatusFilter.value = 0;
  appealHandlingResultFilter.value = '';
  await router.replace({ query: nextQuery });
}

async function updateReviewTaskTypeFilter() {
  const nextQuery = { ...route.query };
  if (reviewTaskTypeFilter.value === 'ALL') {
    delete nextQuery.reviewTaskType;
  } else {
    nextQuery.reviewTaskType = reviewTaskTypeFilter.value;
  }
  await router.replace({ query: nextQuery });
}

async function loadAppeals() {
  appealLoading.value = true;
  try {
    const appealId = focusedAppealId.value;
    const appealExamId = focusedAppealExamId.value;
    const status = appealId ? null : (appealStatusFilter.value === -1 ? null : appealStatusFilter.value);
    const handlingResult = appealId ? null : (appealHandlingResultFilter.value || null);
    if (appealExamId) {
      appeals.value = (await listScoreAppeals(status, handlingResult, appealId, appealExamId)).data;
    } else {
      appeals.value = (await listScoreAppeals(status, handlingResult, appealId)).data;
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Score appeal list failed to load');
  } finally {
    appealLoading.value = false;
  }
}

function appealRowClassName({ row }: { row: ScoreAppeal }) {
  return row.id === focusedAppealId.value ? 'appeal-row-focused' : '';
}

function reviewProgressRowClassName({ row }: { row: ReviewProgress }) {
  return row.examId === focusedReviewExamId.value ? 'review-row-focused' : '';
}

function pendingReviewRowClassName({ row }: { row: PendingReview }) {
  return row.examId === focusedReviewExamId.value ? 'review-row-focused' : '';
}

async function startReview(attemptId: number) {
  reviewing.value = true;
  try {
    reviewDetails.value = (await getReviewDetails(attemptId)).data;
    reviewScores.value = {};
    reviewComments.value = {};
    drawerVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Review details failed to load');
  } finally {
    reviewing.value = false;
  }
}

async function focusRecheckQueueForAppeal(row: ScoreAppeal) {
  const examId = positiveNumber(row.examId);
  const nextQuery: Record<string, any> = {
    ...route.query,
    appealId: String(row.id),
    reviewTaskType: 'RECHECK'
  };
  if (examId) {
    nextQuery.reviewExamId = String(examId);
  }
  await router.replace({ query: nextQuery });
}

async function openRecheckReview(row: ScoreAppeal) {
  await focusRecheckQueueForAppeal(row);
  await startReview(row.attemptId);
}

async function aiSuggestReview(answer: any) {
  if (!answer.stem || !answer.studentAnswer) {
    ElMessage.warning('Question or student answer is missing, so AI review cannot be requested');
    return;
  }
  aiLoading[answer.answerRecordId] = true;
  try {
    const response = await suggestReview({
      question: answer.stem,
      studentAnswer: answer.studentAnswer,
      correctAnswer: answer.correctAnswer || '',
    });
    ElMessage.success({
      message: `AI suggestion: ${response.data}`,
      duration: 5000,
      showClose: true
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI-assisted review failed');
  } finally {
    aiLoading[answer.answerRecordId] = false;
  }
}

async function submitReview() {
  if (!reviewDetails.value) return;

  const unanswered = reviewDetails.value.answers.filter(
    (a) => reviewScores.value[a.answerRecordId] === undefined
  );
  if (unanswered.length > 0) {
    ElMessage.warning(`${unanswered.length} answer(s) are still unscored. Please finish all reviews.`);
    return;
  }

  const payload: ReviewPayload[] = Object.keys(reviewScores.value).map((answerRecordId) => ({
    answerRecordId: Number(answerRecordId),
    score: reviewScores.value[Number(answerRecordId)],
    comment: reviewComments.value[Number(answerRecordId)] || '',
  }));

  submitting.value = true;
  try {
    const response = await apiSubmitReview(reviewDetails.value.attemptId, payload);
    rememberReviewScoreAudit('Submit review', response.data.reviewScoreLogIds || []);
    rememberReviewReleaseHandoff(response.data);
    ElMessage.success(`Review submitted${reviewScoreLogSuffix(response.data.reviewScoreLogIds)}`);
    drawerVisible.value = false;
    await loadReviewProgress();
    await loadPendingReviews();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Review submission failed');
  } finally {
    submitting.value = false;
  }
}

function rememberReviewReleaseHandoff(result: {
  examId?: number | string;
  examName?: string;
  examReviewComplete?: boolean | number | string;
  scoreReleaseHandoffReady?: boolean | number | string;
  examPendingReviewAttemptCount?: number | string;
  examPendingReviewAnswerCount?: number | string;
  examActiveAttemptCount?: number | string;
  examUnscoredCompletedAttemptCount?: number | string;
}) {
  const examId = positiveNumber(result.examId);
  if (!examId) return;
  const pendingAttempts = Number(result.examPendingReviewAttemptCount || 0);
  const pendingAnswers = Number(result.examPendingReviewAnswerCount || 0);
  const activeAttempts = Number(result.examActiveAttemptCount || 0);
  const unscoredCompleted = Number(result.examUnscoredCompletedAttemptCount || 0);
  if (Number(result.examReviewComplete ?? 0) !== 1 && pendingAttempts + pendingAnswers > 0) {
    lastReviewReleaseHandoff.value = null;
    return;
  }
  lastReviewReleaseHandoff.value = {
    examId,
    examName: result.examName || `Exam #${examId}`,
    ready: Number(result.scoreReleaseHandoffReady ?? 0) === 1,
    pendingAttempts: Number.isFinite(pendingAttempts) ? pendingAttempts : 0,
    pendingAnswers: Number.isFinite(pendingAnswers) ? pendingAnswers : 0,
    activeAttempts: Number.isFinite(activeAttempts) ? activeAttempts : 0,
    unscoredCompleted: Number.isFinite(unscoredCompleted) ? unscoredCompleted : 0
  };
}

function openReviewReleaseReadiness() {
  const handoff = lastReviewReleaseHandoff.value;
  if (!handoff) return;
  openExamScoreReadiness(handoff.examId);
}

function openReviewProgressReadiness(row: ReviewProgress) {
  const examId = positiveNumber(row.examId);
  if (!examId) return;
  openExamScoreReadiness(examId);
}

function openExamScoreReadiness(examId: number) {
  router.push({
    path: '/exam-tasks',
    query: {
      examId: String(examId),
      scoreReadiness: '1'
    }
  });
}

function reviewScoreLogSuffix(logIds?: number[]) {
  if (!logIds || logIds.length === 0) return '';
  const visibleIds = logIds.slice(0, 3).map((id) => `#${id}`).join(', ');
  return `; audit logs ${visibleIds}${logIds.length > 3 ? ` +${logIds.length - 3}` : ''}`;
}

function rememberReviewScoreAudit(action: string, ids: Array<number | string | null | undefined>) {
  const reviewScoreLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (reviewScoreLogIds.length === 0) return;
  lastReviewScoreAudit.value = { action, reviewScoreLogIds };
}

function reviewScoreAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestReviewScoreAuditId() {
  const ids = lastReviewScoreAudit.value?.reviewScoreLogIds;
  if (!ids?.length) return;
  try {
    await copyReviewScoreAuditIdToClipboard(ids.join(','));
    ElMessage.success('Review score audit ID copied');
  } catch {
    ElMessage.error('Failed to copy review score audit ID');
  }
}

async function copyLatestReviewScoreAuditLink() {
  const id = lastReviewScoreAudit.value?.reviewScoreLogIds[0];
  if (!id) return;
  try {
    await copyReviewScoreAuditLinkToClipboard(id);
    ElMessage.success('Review score audit link copied');
  } catch {
    ElMessage.error('Failed to copy review score audit link');
  }
}

function scoreAppealLogSuffix(logIds?: number[]) {
  if (!logIds || logIds.length === 0) return '';
  const visibleIds = logIds.slice(0, 3).map((id) => `#${id}`).join(', ');
  return `; appeal audit logs ${visibleIds}${logIds.length > 3 ? ` +${logIds.length - 3}` : ''}`;
}

function rememberScoreAppealAudit(action: string, ids: Array<number | string | null | undefined>) {
  const scoreAppealLogIds = ids.filter((id): id is number | string => id !== null && id !== undefined && id !== '');
  if (scoreAppealLogIds.length === 0) return;
  lastScoreAppealAudit.value = { action, scoreAppealLogIds };
}

function scoreAppealAuditText(ids: Array<number | string>) {
  return ids.map((id) => `#${id}`).join(', ');
}

async function copyLatestScoreAppealAuditId() {
  const ids = lastScoreAppealAudit.value?.scoreAppealLogIds;
  if (!ids?.length) return;
  try {
    await copyScoreAppealAuditIdToClipboard(ids.join(','));
    ElMessage.success('Score appeal audit ID copied');
  } catch {
    ElMessage.error('Failed to copy score appeal audit ID');
  }
}

async function copyLatestScoreAppealAuditLink() {
  const id = lastScoreAppealAudit.value?.scoreAppealLogIds[0];
  if (!id) return;
  try {
    await copyScoreAppealAuditLinkToClipboard(id);
    ElMessage.success('Score appeal audit link copied');
  } catch {
    ElMessage.error('Failed to copy score appeal audit link');
  }
}

function openReply(row: ScoreAppeal) {
  activeAppeal.value = row;
  appealReply.value = row.teacherReply || '';
  appealHandlingResult.value = row.handlingResult || 'MAINTAINED';
  appealDialogVisible.value = true;
}

async function submitAppealReply() {
  if (!activeAppeal.value) return;
  if (!appealReply.value.trim()) {
    ElMessage.warning('Please enter a handling opinion');
    return;
  }
  if (!appealHandlingResult.value) {
    ElMessage.warning('Please select a handling result');
    return;
  }
  appealSubmitting.value = true;
  try {
    const response = await replyScoreAppeal(activeAppeal.value.id, {
      reply: appealReply.value.trim(),
      handlingResult: appealHandlingResult.value
    });
    const reopened = response.data.reopenedReviewCount || 0;
    rememberScoreAppealAudit('Handle appeal', response.data.scoreAppealLogIds || []);
    ElMessage.success(
      `${reopened > 0 ? `Appeal handled; reopened ${reopened} recheck review task(s)` : 'Appeal handled'}${scoreAppealLogSuffix(response.data.scoreAppealLogIds)}`
    );
    appealDialogVisible.value = false;
    if (appealHandlingResult.value === 'RECHECK_REQUIRED') {
      await focusRecheckQueueForAppeal(response.data);
      await loadReviewProgress();
      await loadPendingReviews();
    }
    await loadAppeals();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Appeal handling failed');
  } finally {
    appealSubmitting.value = false;
  }
}

async function closeRecheck(row: ScoreAppeal) {
  activeRecheckAppeal.value = row;
  activeRecheckReadiness.value = null;
  recheckCloseNote.value = '';
  recheckCloseVisible.value = true;
  recheckReadinessLoading.value = true;
  try {
    activeRecheckReadiness.value = (await getScoreAppealRecheckReadiness(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Recheck readiness failed to load');
  } finally {
    recheckReadinessLoading.value = false;
  }
}

function canCloseRecheck(row: ScoreAppeal) {
  return row.status === 1 && row.handlingResult === 'RECHECK_REQUIRED';
}

async function submitCloseRecheck() {
  if (!activeRecheckAppeal.value || !activeRecheckReadiness.value) return;
  if (!isRecheckCloseAllowed(activeRecheckReadiness.value)) {
    ElMessage.warning('Recheck cannot be closed until all blockers are resolved.');
    return;
  }
  if (!recheckCloseNote.value.trim()) {
    ElMessage.warning('Please enter a recheck completion note.');
    return;
  }
  recheckCloseSubmitting.value = true;
  try {
    const response = await closeScoreAppealRecheck(activeRecheckAppeal.value.id, {
      recheckNote: recheckCloseNote.value.trim()
    });
    rememberScoreAppealAudit('Close recheck', response.data.scoreAppealLogIds || []);
    ElMessage.success(`Recheck closed${scoreAppealLogSuffix(response.data.scoreAppealLogIds)}`);
    recheckCloseVisible.value = false;
    await loadReviewProgress();
    await loadAppeals();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Recheck close failed');
  } finally {
    recheckCloseSubmitting.value = false;
  }
}

function isRecheckCloseAllowed(readiness?: ScoreAppealRecheckReadiness | null) {
  return Number(readiness?.closeAllowed || 0) === 1;
}

function recheckFinalizedText(readiness?: ScoreAppealRecheckReadiness | null) {
  return Number(readiness?.recheckAttemptFinalized || 0) === 1 ? 'Finalized' : 'Not finalized';
}

function recheckCloseBlockerText(blocker?: string | null) {
  const map: Record<string, string> = {
    APPEAL_NOT_OPEN_RECHECK: 'This appeal is not an open recheck task.',
    NO_RECHECK_ANSWERS: 'No answer is available for this recheck.',
    PENDING_RECHECK_ANSWERS: 'Some recheck answers are still pending review.',
    ATTEMPT_NOT_FINALIZED: 'The attempt is not finalized with a score.',
    NO_REVIEW_SCORE_LOGS: 'No review score audit log was recorded after recheck opened.'
  };
  return blocker ? map[blocker] || blocker : '-';
}

function recheckAnswerStatusText(answer: ScoreAppealRecheckAnswer) {
  return Number(answer.reviewStatus || 0) === 1 ? 'Reviewed' : 'Pending';
}

async function openAppealLogs(row: ScoreAppeal) {
  activeLogAppeal.value = row;
  appealLogs.value = [];
  appealLogVisible.value = true;
  appealLogLoading.value = true;
  try {
    appealLogs.value = (await listScoreAppealLogs(row.id)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Score appeal logs failed to load');
  } finally {
    appealLogLoading.value = false;
  }
}

async function openScoreLogs(attemptId: number, examName: string, studentName: string, pendingCount: number) {
  const row = { attemptId, examName, studentName, pendingCount };
  activeScoreLogAttempt.value = row;
  scoreLogs.value = [];
  scoreLogVisible.value = true;
  scoreLogLoading.value = true;
  try {
    scoreLogs.value = (await listReviewScoreLogs(row.attemptId)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Review score logs failed to load');
  } finally {
    scoreLogLoading.value = false;
  }
}

async function exportAppealLogs() {
  if (!activeLogAppeal.value) return;
  appealLogExporting.value = true;
  try {
    await exportScoreAppealLogs(
      activeLogAppeal.value.id,
      activeLogAppeal.value.examName,
      activeLogAppeal.value.studentName
    );
    ElMessage.success('Score appeal log export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Export failed');
  } finally {
    appealLogExporting.value = false;
  }
}

async function exportScoreLogs() {
  if (!activeScoreLogAttempt.value) return;
  scoreLogExporting.value = true;
  try {
    await exportReviewScoreLogs(
      activeScoreLogAttempt.value.attemptId,
      activeScoreLogAttempt.value.examName,
      activeScoreLogAttempt.value.studentName
    );
    ElMessage.success('Review score log export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Export failed');
  } finally {
    scoreLogExporting.value = false;
  }
}

async function copyScoreAppealAuditId(logId?: number | string | null) {
  try {
    const value = await copyScoreAppealAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`Score appeal audit ID copied: ${value}`);
  } catch {
    ElMessage.error('Failed to copy score appeal audit ID');
  }
}

async function copyScoreAppealAuditLink(logId?: number | string | null) {
  try {
    const link = await copyScoreAppealAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('Score appeal audit link copied');
  } catch {
    ElMessage.error('Failed to copy score appeal audit link');
  }
}

async function copyReviewScoreAuditId(logId?: number | string | null) {
  try {
    const value = await copyReviewScoreAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`Review score audit ID copied: ${value}`);
  } catch {
    ElMessage.error('Failed to copy review score audit ID');
  }
}

async function copyReviewScoreAuditLink(logId?: number | string | null) {
  try {
    const link = await copyReviewScoreAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('Review score audit link copied');
  } catch {
    ElMessage.error('Failed to copy review score audit link');
  }
}

function appealStatusText(status?: number) {
  if (status === 1) return 'Replied';
  if (status === 2) return 'Closed';
  return 'Pending';
}

function appealStatusType(status?: number) {
  if (status === 1) return 'success';
  if (status === 2) return 'info';
  return 'warning';
}

function appealHandlingResultText(value?: string | null) {
  const map: Record<string, string> = {
    MAINTAINED: 'Maintain Score',
    RECHECK_REQUIRED: 'Recheck Required',
    ADJUSTED_OFFLINE: 'Adjusted Offline'
  };
  return value ? map[value] || value : '-';
}

function appealLogActionText(value?: string | null) {
  const map: Record<string, string> = {
    SUBMIT: 'Submit Appeal',
    REPLY: 'Reply',
    RECHECK_OPEN: 'Open Recheck',
    CLOSE_RECHECK: 'Close Recheck'
  };
  return value ? map[value] || value : '-';
}

function appealLogStatusText(status?: number | null) {
  return status === null || status === undefined ? '-' : appealStatusText(status);
}

function applyAppealRouteFilters() {
  let changed = false;
  if (hasQueryKey('appealStatus')) {
    const nextStatus = normalizeAppealStatus(firstQueryValue(route.query.appealStatus));
    if (nextStatus !== null && appealStatusFilter.value !== nextStatus) {
      appealStatusFilter.value = nextStatus;
      changed = true;
    }
  }
  if (hasQueryKey('appealHandlingResult')) {
    const nextHandlingResult = normalizeAppealHandlingResult(firstQueryValue(route.query.appealHandlingResult));
    if (nextHandlingResult !== null && appealHandlingResultFilter.value !== nextHandlingResult) {
      appealHandlingResultFilter.value = nextHandlingResult;
      changed = true;
    }
  }
  return changed;
}

function applyReviewRouteFilters() {
  const nextType = normalizeReviewTaskType(firstQueryValue(route.query.reviewTaskType));
  if (reviewTaskTypeFilter.value !== nextType) {
    reviewTaskTypeFilter.value = nextType;
    return true;
  }
  return false;
}

function hasQueryKey(key: string) {
  return Object.prototype.hasOwnProperty.call(route.query, key);
}

function routeAppealId() {
  const raw = firstQueryValue(route.query.appealId);
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function routeAppealExamId() {
  const raw = firstQueryValue(route.query.appealExamId);
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function routeReviewExamId() {
  const raw = firstQueryValue(route.query.reviewExamId);
  const value = Number(raw);
  return Number.isFinite(value) && value > 0 ? value : null;
}

function positiveNumber(value: unknown) {
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

function firstQueryValue(value: unknown) {
  if (Array.isArray(value)) {
    return value[0] == null ? '' : String(value[0]);
  }
  return value == null ? '' : String(value);
}

function normalizeAppealStatus(value: string) {
  if (!value) return null;
  const status = Number(value);
  return [-1, 0, 1].includes(status) ? status : null;
}

function normalizeAppealHandlingResult(value: string) {
  const result = value.trim().toUpperCase();
  if (!result || result === 'ALL') return '';
  if (['MAINTAINED', 'RECHECK_REQUIRED', 'ADJUSTED_OFFLINE'].includes(result)) {
    return result;
  }
  return null;
}

function normalizeReviewTaskType(value: string): 'ALL' | ReviewTaskType {
  const result = value.trim().toUpperCase();
  return result === 'RECHECK' || result === 'STANDARD' ? result : 'ALL';
}
</script>

<style scoped>
.review-score-audit,
.score-appeal-audit,
.review-route-filter,
.review-release-handoff {
  margin-bottom: 14px;
}

.review-score-audit-content,
.score-appeal-audit-content,
.review-route-filter-content,
.review-release-handoff-content {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.appeal-card {
  margin-top: 16px;
}

.review-progress-card {
  margin-bottom: 16px;
}

.review-progress-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.review-progress-toolbar h3 {
  margin: 0;
  font-size: 16px;
}

.pending-review-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.pending-review-toolbar h3 {
  margin: 0;
  font-size: 16px;
}

.pending-review-filter {
  display: flex;
  align-items: center;
  gap: 8px;
}

.recheck-count {
  margin-top: 2px;
  color: #b45309;
  font-size: 12px;
}

:deep(.appeal-row-focused td) {
  background: #fffbeb !important;
}

:deep(.review-row-focused td) {
  background: #eff6ff !important;
}

.appeal-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.appeal-toolbar h3 {
  margin: 0;
  font-size: 16px;
}

.appeal-filters {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.appeal-detail p {
  margin: 8px 0;
  line-height: 1.7;
}

.appeal-log-head {
  display: grid;
  gap: 4px;
  margin-bottom: 12px;
  color: #475569;
}

.appeal-log-head strong {
  color: #1f2937;
}

.appeal-log-toolbar,
.score-log-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.appeal-log-id-cell,
.review-score-log-id-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.appeal-log-id-cell span,
.review-score-log-id-cell span {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  color: #111827;
}

.appeal-log-id-cell .el-button,
.review-score-log-id-cell .el-button {
  padding: 0;
}

.recheck-readiness-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.recheck-readiness-head > div {
  display: grid;
  gap: 4px;
}

.recheck-readiness-head strong {
  color: #111827;
  font-size: 16px;
}

.recheck-readiness-head span {
  color: #64748b;
  font-size: 13px;
}

.recheck-readiness-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.recheck-readiness-stats > div {
  min-height: 64px;
  display: grid;
  align-content: center;
  gap: 4px;
  padding: 10px 12px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.recheck-readiness-stats span {
  color: #64748b;
  font-size: 12px;
}

.recheck-readiness-stats strong {
  color: #111827;
  font-size: 16px;
}

.recheck-readiness-alert,
.recheck-close-form {
  margin-top: 14px;
}

.answer-card {
  margin-bottom: 16px;
}
.answer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.answer-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.answer-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.section-label {
  font-size: 13px;
  color: #606266;
  font-weight: 500;
}
.section-content {
  padding: 10px 12px;
  background: #f5f7fa;
  border-radius: 6px;
  line-height: 1.6;
  font-size: 14px;
  color: #303133;
}
.student-answer {
  background: #fef0f0;
  border-left: 3px solid #f56c6c;
}
.correct-answer {
  background: #f0f9ff;
  border-left: 3px solid #409eff;
}
.max-score-hint {
  color: #909399;
  font-size: 14px;
  margin-left: 8px;
}
.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #dcdfe6;
}
</style>
