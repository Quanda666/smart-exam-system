<template>
  <ExamTaking v-if="takingExam" :attempt-id="takingExam.attemptId" @submit-success="finishExam" />
  <div v-else class="student-panel">
    <el-tabs v-model="activeTab" type="border-card">
       <el-tab-pane label="考试中心" name="exams">
        <ExamList @view-result="viewResult" @start-exam="startExam" />
      </el-tab-pane>
      <el-tab-pane label="成绩查询" name="grades">
        <el-table :data="grades" border stripe>
          <el-table-column prop="examName" label="考试名称" min-width="200"/>
          <el-table-column prop="subjectName" label="科目" />
          <el-table-column prop="score" label="分数" />
          <el-table-column label="提交时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.submitTime) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button link type="primary" @click="viewResult(scope.row.attemptId)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="grades.length === 0" description="暂无成绩记录" />
      </el-tab-pane>
      <el-tab-pane label="错题本" name="wrong-questions">
        <el-table :data="wrongQuestions" border stripe>
          <el-table-column prop="stem" label="题干" show-overflow-tooltip min-width="250" />
          <el-table-column prop="questionType" label="题型" />
          <el-table-column prop="wrongCount" label="错误次数" />
          <el-table-column label="最近错误时间" width="180">
            <template #default="scope">{{ formatDateTime(scope.row.lastWrongTime) }}</template>
          </el-table-column>
          <el-table-column label="操作">
            <template #default="scope">
              <el-button link type="primary" @click="aiExplainWrong(scope.row as WrongQuestion)">AI讲解</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="wrongQuestions.length === 0" description="恭喜你，暂无错题" />
      </el-tab-pane>
      <el-tab-pane label="知识点掌握" name="mastery">
        <div ref="masteryChart" style="width: 100%; height: 400px;"></div>
        <el-empty v-if="!masteryData || Object.keys(masteryData).length === 0" description="暂无知识点掌握度分析" />
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="drawerVisible" title="考试结果详情" direction="rtl" size="50%">
      <div v-if="examResult" class="result-drawer">
        <el-descriptions :title="examResult.gradeInfo.examName" :column="2" border>
          <el-descriptions-item label="总分">{{ examResult.gradeInfo.score }}</el-descriptions-item>
          <el-descriptions-item label="交卷时间">{{ formatDateTime(examResult.gradeInfo.submitTime) }}</el-descriptions-item>
        </el-descriptions>
        <el-divider />
        <div v-for="(answer, index) in examResult.answers" :key="index" class="answer-block">
          <p><strong>{{ index + 1 }}. {{ answer.stem }}</strong> <el-tag size="small">{{ answer.questionType }}</el-tag></p>
          <p :class="answer.isCorrect ? 'text-success' : 'text-danger'">你的答案: {{ answer.studentAnswer || '未作答' }}</p>
          <p>正确答案: {{ answer.correctAnswer }}</p>
          <p>得分: {{ answer.score }}</p>
          <p v-if="answer.analysis">解析: {{ answer.analysis }}</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { getGrades, getExamResult, getWrongQuestions, getKnowledgePointMastery, type GradeInfo, type ExamResult, type WrongQuestion } from '../api/student';
import { explainText } from '../api/ai';
import { ElMessage, ElMessageBox } from 'element-plus';
import * as echarts from 'echarts';
import ExamList from './ExamList.vue';
import ExamTaking from './ExamTaking.vue';
import { formatDateTime } from '../utils/dateFormat';

const props = defineProps<{ path?: string }>();

function tabFromPath(path?: string) {
  if (!path) return 'exams';
  if (path.includes('results')) return 'grades';
  if (path.includes('wrong-questions')) return 'wrong-questions';
  return 'exams';
}

const activeTab = ref(tabFromPath(props.path));
const grades = ref<GradeInfo[]>([]);
const wrongQuestions = ref<WrongQuestion[]>([]);
const masteryData = ref<Record<string, number>>({});
const drawerVisible = ref(false);
const examResult = ref<ExamResult | null>(null);
const masteryChart = ref<HTMLElement | null>(null);
const takingExam = ref<{ attemptId: number } | null>(null);

watch(activeTab, (newTab) => {
  switch (newTab) {
    case 'grades':
      loadGrades();
      break;
    case 'wrong-questions':
      loadWrongQuestions();
      break;
    case 'mastery':
      loadMastery();
      break;
  }
});

watch(
  () => props.path,
  (path) => {
    activeTab.value = tabFromPath(path);
  }
);

async function loadGrades() {
  if (grades.value.length > 0) return;
  try {
    grades.value = (await getGrades()).data;
  } catch (error) {
    ElMessage.error('成绩列表加载失败');
  }
}

async function loadWrongQuestions() {
    if (wrongQuestions.value.length > 0) return;
  try {
    wrongQuestions.value = (await getWrongQuestions()).data;
  } catch (error) {
    ElMessage.error('错题本加载失败');
  }
}

async function loadMastery() {
    if (Object.keys(masteryData.value).length > 0) return;
  try {
     masteryData.value = (await getKnowledgePointMastery()).data;
     initMasteryChart();
  } catch (error) {
    ElMessage.error('知识点掌握度加载失败');
  }
}

const finishExam = () => {
  takingExam.value = null;
  // Refresh the exam list by re-fetching the data
  // This is a simple approach. A better one would be to update the specific exam's status.
  const studentPanel = document.querySelector('.student-panel');
  if (studentPanel) {
    // A bit of a hack to re-trigger the exam list load
    activeTab.value = 'grades';
    setTimeout(() => {
        activeTab.value = 'exams';
    }, 100);
  }
}

const startExam = (exam: { attemptId: number }) => {
  takingExam.value = { attemptId: exam.attemptId };
};

const viewResult = async (attemptId: number) => {
  try {
    examResult.value = (await getExamResult(attemptId)).data;
    drawerVisible.value = true;
  } catch (error) {
    ElMessage.error('查看结果失败');
  }
};

const initMasteryChart = () => {
  if (masteryChart.value) {
    const chart = echarts.init(masteryChart.value);
    const option = {
      title: { text: '知识点掌握情况' },
      tooltip: {},
      xAxis: {
        type: 'category',
        data: Object.keys(masteryData.value)
      },
      yAxis: {
        type: 'value'
      },
      series: [{
        data: Object.values(masteryData.value),
        type: 'bar'
      }]
    };
    chart.setOption(option);
  }
};
const aiExplainWrong = async (item: WrongQuestion) => {
  try {
    const response = await explainText(item.stem + "\n正确答案: " + item.correctAnswer + "\n解析: " + (item.analysis || '暂无'));
    ElMessageBox.alert(response.data, 'AI 讲解', {
      confirmButtonText: '关闭',
    });
  } catch (error) {
    ElMessage.error('AI讲解请求失败');
  }
};
</script>

<style scoped>
.student-panel {
  height: 100%;
}

.result-drawer .answer-block {
  margin-bottom: 20px;
  padding: 10px;
  border-left: 3px solid #eee;
}

.result-drawer .text-success {
  color: #67c23a;
}

.result-drawer .text-danger {
  color: #f56c6c;
}

.el-tabs--border-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.el-tabs__content) {
  flex-grow: 1;
  overflow-y: auto;
}
</style>
