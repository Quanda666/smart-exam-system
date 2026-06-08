<template>
  <div class="mp-page">
    <div class="mp-table-card">
      <el-table v-loading="loading" :data="pendingReviews">
        <el-table-column prop="examName" label="考试名称" min-width="180" />
        <el-table-column prop="studentName" label="学生姓名" width="120" />
        <el-table-column prop="pendingCount" label="待批阅题数" width="120" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="scope">
            <el-button link type="primary" @click="startReview(scope.row.attemptId)">开始批阅</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && pendingReviews.length === 0" description="暂无待批阅的试卷" :image-size="100" />
    </div>

    <el-drawer v-model="drawerVisible" :title="`批阅试卷 - ${reviewDetails?.examName || ''}`" size="60%">
      <div v-if="reviewDetails" v-loading="reviewing">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          <template #title>
            <span>学生：{{ reviewDetails.studentName }} | 待批阅题数：{{ reviewDetails.answers.length }}</span>
          </template>
        </el-alert>

        <div v-for="(answer, index) in reviewDetails.answers" :key="answer.answerRecordId" class="answer-card">
          <el-card shadow="never">
            <template #header>
              <div class="answer-header">
                <span><strong>题目 {{ index + 1 }}</strong></span>
                <el-tag v-if="reviewScores[answer.answerRecordId] !== undefined" type="success" size="small">
                  已打分：{{ reviewScores[answer.answerRecordId] }}
                </el-tag>
              </div>
            </template>

            <div class="answer-body">
              <div class="answer-section">
                <div class="section-label">题干</div>
                <div class="section-content">{{ answer.stem }}</div>
              </div>

              <div class="answer-section">
                <div class="section-label">学生答案</div>
                <div class="section-content student-answer">{{ answer.studentAnswer || '未作答' }}</div>
              </div>

              <div v-if="answer.correctAnswer" class="answer-section">
                <div class="section-label">参考答案</div>
                <div class="section-content correct-answer">{{ answer.correctAnswer }}</div>
              </div>

              <div class="answer-section">
                <div class="section-label">得分</div>
                <el-input-number
                  v-model="reviewScores[answer.answerRecordId]"
                  :min="0"
                  :max="100"
                  :precision="1"
                  size="large"
                  style="width: 160px;"
                />
                <span class="max-score-hint"> / 100 分</span>
                <el-button
                  type="primary"
                  plain
                  size="small"
                  style="margin-left: 12px;"
                  :loading="aiLoading[answer.answerRecordId]"
                  @click="aiSuggestReview(answer)"
                >
                  AI 建议
                </el-button>
              </div>

              <div class="answer-section">
                <div class="section-label">评语（选填）</div>
                <el-input
                  v-model="reviewComments[answer.answerRecordId]"
                  type="textarea"
                  :rows="2"
                  placeholder="输入批阅评语，如：答案部分正确，缺少关键要点..."
                  maxlength="200"
                  show-word-limit
                />
              </div>
            </div>
          </el-card>
        </div>

        <div class="drawer-footer">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="submitReview">提交批阅</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue';
import { getPendingReviews, getReviewDetails, submitReview as apiSubmitReview, type ReviewDetail, type ReviewPayload } from '../api/review';
import { suggestReview } from '../api/ai';
import { ElMessage } from 'element-plus';

const pendingReviews = ref<any[]>([]);
const loading = ref(false);
const drawerVisible = ref(false);
const reviewing = ref(false);
const submitting = ref(false);
const reviewDetails = ref<ReviewDetail | null>(null);
const reviewScores = ref<Record<number, number>>({});
const reviewComments = ref<Record<number, string>>({});
const aiLoading = reactive<Record<number, boolean>>({});

onMounted(loadPendingReviews);

async function loadPendingReviews() {
  loading.value = true;
  try {
    pendingReviews.value = (await getPendingReviews()).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '待批阅列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function startReview(attemptId: number) {
  reviewing.value = true;
  try {
    reviewDetails.value = (await getReviewDetails(attemptId)).data;
    reviewScores.value = {};
    reviewComments.value = {};
    drawerVisible.value = true;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '试卷详情加载失败');
  } finally {
    reviewing.value = false;
  }
}

async function aiSuggestReview(answer: any) {
  if (!answer.stem || !answer.studentAnswer) {
    ElMessage.warning('缺少题目或学生答案，无法调用 AI');
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
      message: `AI 建议：${response.data}`,
      duration: 5000,
      showClose: true
    });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'AI 辅助评分失败');
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
    ElMessage.warning(`还有 ${unanswered.length} 道题未打分，请完成所有题目的批阅`);
    return;
  }

  const payload: ReviewPayload[] = Object.keys(reviewScores.value).map((answerRecordId) => ({
    answerRecordId: Number(answerRecordId),
    score: reviewScores.value[Number(answerRecordId)],
    comment: reviewComments.value[Number(answerRecordId)] || '',
  }));

  submitting.value = true;
  try {
    await apiSubmitReview(reviewDetails.value.attemptId, payload);
    ElMessage.success('批阅已提交');
    drawerVisible.value = false;
    await loadPendingReviews();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '提交批阅失败');
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
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
