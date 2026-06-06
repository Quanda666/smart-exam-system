<template>
  <div class="review-panel">
    <el-table :data="pendingReviews" border>
      <el-table-column prop="examName" label="考试名称" />
      <el-table-column prop="studentName" label="学生" />
      <el-table-column prop="pendingCount" label="待批阅数" />
      <el-table-column label="操作">
        <template #default="scope">
          <el-button @click="startReview(scope.row.attemptId)">开始批阅</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="drawerVisible" title="批阅试卷">
      <div v-if="reviewDetails">
        <h3>{{ reviewDetails.examName }} - {{ reviewDetails.studentName }}</h3>
        <div v-for="(answer, index) in reviewDetails.answers" :key="answer.answerRecordId">
          <p><strong>题目 {{ index + 1 }}: {{ answer.stem }}</strong></p>
          <p>学生答案: {{ answer.studentAnswer }}</p>
          <el-input-number v-model="reviewScores[answer.answerRecordId]" :min="0" />
          <el-input v-model="reviewComments[answer.answerRecordId]" placeholder="评语" />
          <el-button @click="aiSuggestReview(answer)">AI辅助评分</el-button>
        </div>
        <el-button @click="submitReview" type="primary">提交批阅</el-button>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getPendingReviews, getReviewDetails, submitReview as apiSubmitReview, ReviewDetail, ReviewPayload } from '../api/review';
import { suggestReview } from '../api/ai';
import { ElMessage } from 'element-plus';

const pendingReviews = ref<any[]>([]);
const drawerVisible = ref(false);
const reviewDetails = ref<ReviewDetail | null>(null);
const reviewScores = ref<Record<number, number>>({});
const reviewComments = ref<Record<number, string>>({});

onMounted(async () => {
  pendingReviews.value = (await getPendingReviews()).data;
});

const startReview = async (attemptId: number) => {
  reviewDetails.value = (await getReviewDetails(attemptId)).data;
  drawerVisible.value = true;
};

const aiSuggestReview = async (answer: any) => {
  try {
    const response = await suggestReview({
      question: answer.stem,
      studentAnswer: answer.studentAnswer,
      correctAnswer: answer.correctAnswer,
    });
    ElMessage.info(`AI建议: ${response.data}`);
  } catch (error) {
    ElMessage.error('AI辅助评分失败');
  }
};

const submitReview = async () => {
  if (!reviewDetails.value) return;
  
  const payload: ReviewPayload[] = Object.keys(reviewScores.value).map(answerRecordId => ({
    answerRecordId: Number(answerRecordId),
    score: reviewScores.value[Number(answerRecordId)],
    comment: reviewComments.value[Number(answerRecordId)] || '',
  }));

  try {
    await apiSubmitReview(reviewDetails.value.attemptId, payload);
    ElMessage.success('批阅成功');
    drawerVisible.value = false;
    pendingReviews.value = (await getPendingReviews()).data;
  } catch (error) {
    ElMessage.error('提交失败');
  }
};
</script>
