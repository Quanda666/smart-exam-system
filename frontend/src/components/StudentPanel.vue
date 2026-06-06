<template>
  <div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="成绩查询" name="grades">
        <el-table :data="grades" border>
          <el-table-column prop="examName" label="考试名称" />
          <el-table-column prop="subjectName" label="科目" />
          <el-table-column prop="score" label="分数" />
          <el-table-column prop="submitTime" label="提交时间" />
          <el-table-column label="操作">
            <template #default="scope">
              <el-button @click="viewResult(scope.row.attemptId)">查看详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="错题本" name="wrong-questions">
        <el-table :data="wrongQuestions" border>
          <el-table-column prop="stem" label="题干" />
          <el-table-column prop="questionType" label="题型" />
          <el-table-column prop="wrongCount" label="错误次数" />
          <el-table-column prop="lastWrongTime" label="最近错误时间" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="知识点掌握" name="mastery">
        <div ref="masteryChart" style="width: 600px; height: 400px;"></div>
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="drawerVisible" title="考试结果详情">
      <div v-if="examResult">
        <h2>{{ examResult.gradeInfo.examName }}</h2>
        <p>总分: {{ examResult.gradeInfo.score }}</p>
        <div v-for="(answer, index) in examResult.answers" :key="index">
          <p><strong>题目 {{ index + 1 }}: {{ answer.stem }}</strong></p>
          <p>你的答案: {{ answer.studentAnswer }}</p>
          <p>正确答案: {{ answer.correctAnswer }}</p>
          <p>得分: {{ answer.score }}</p>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { getGrades, getExamResult, getWrongQuestions, getKnowledgePointMastery, GradeInfo, ExamResult, WrongQuestion } from '../api/student';
import * as echarts from 'echarts';

const activeTab = ref('grades');
const grades = ref<GradeInfo[]>([]);
const wrongQuestions = ref<WrongQuestion[]>([]);
const masteryData = ref<Record<string, number>>({});
const drawerVisible = ref(false);
const examResult = ref<ExamResult | null>(null);
const masteryChart = ref<HTMLElement | null>(null);

onMounted(async () => {
  grades.value = (await getGrades()).data;
  wrongQuestions.value = (await getWrongQuestions()).data;
  masteryData.value = (await getKnowledgePointMastery()).data;
  initMasteryChart();
});

const viewResult = async (attemptId: number) => {
  examResult.value = (await getExamResult(attemptId)).data;
  drawerVisible.value = true;
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
</script>
