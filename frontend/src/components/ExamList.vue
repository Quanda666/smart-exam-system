<template>
  <div class="exam-list-panel">
    <el-tabs v-model="activeList" type="card">
      <el-tab-pane label="待参加" name="pending">
        <el-table :data="pendingExams" border stripe>
          <el-table-column prop="examName" label="考试名称" min-width="200" />
          <el-table-column prop="subjectName" label="所属科目" width="150" />
          <el-table-column prop="startTime" label="开始时间" width="180" />
          <el-table-column prop="endTime" label="结束时间" width="180" />
          <el-table-column prop="durationMinutes" label="考试时长（分钟）" width="160" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button
                type="primary"
                :disabled="!isExamAvailable(scope.row as StudentExamInfo)"
                @click="startExam(scope.row as StudentExamInfo)"
              >
                进入考试
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="pendingExams.length === 0" description="暂无待参加的考试" />
      </el-tab-pane>
      <el-tab-pane label="进行中" name="active">
        <el-table :data="activeExams" border stripe>
          <el-table-column prop="examName" label="考试名称" min-width="200" />
          <el-table-column prop="subjectName" label="所属科目" width="150" />
          <el-table-column prop="endTime" label="结束时间" width="180" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button type="success" @click="startExam(scope.row as StudentExamInfo)">继续答题</el-button>
            </template>
          </el-table-column>
        </el-table>
         <el-empty v-if="activeExams.length === 0" description="暂无进行中的考试" />
      </el-tab-pane>
      <el-tab-pane label="已完成" name="finished">
        <el-table :data="finishedExams" border stripe>
          <el-table-column prop="examName" label="考试名称" min-width="200" />
          <el-table-column prop="subjectName" label="所属科目" width="150" />
          <el-table-column prop="submitTime" label="交卷时间" width="180" />
           <el-table-column prop="score" label="得分" width="100" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="scope">
              <el-button link type="primary" @click="viewResult((scope.row as StudentExamInfo).attemptId)">查看结果</el-button>
            </template>
          </el-table-column>
        </el-table>
         <el-empty v-if="finishedExams.length === 0" description="暂无已完成的考试" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { listStudentExams, type StudentExamInfo } from '../api/exam';
import { ElMessage } from 'element-plus';

const activeList = ref('pending');

const emits = defineEmits<{
  (e: 'view-result', attemptId: number): void;
  (e: 'start-exam', exam: StudentExamInfo): void;
}>();

const exams = ref<StudentExamInfo[]>([]);

onMounted(async () => {
  try {
    const response = await listStudentExams(1, 10000);
    exams.value = response.data.list;
  } catch (error) {
    ElMessage.error('加载考试列表失败');
  }
});

const pendingExams = computed(() => exams.value.filter(e => e.status === 0));
const activeExams = computed(() => exams.value.filter(e => e.status === 1));
const finishedExams = computed(() => exams.value.filter(e => e.status >= 2));

const isExamAvailable = (exam: StudentExamInfo) => {
  const now = new Date();
  const start = new Date(exam.startTime);
  const end = new Date(exam.endTime);
  return now >= start && now <= end;
};

const startExam = (exam: StudentExamInfo) => {
  emits('start-exam', exam);
};

const viewResult = (attemptId: number) => {
  emits('view-result', attemptId);
};

</script>

<style scoped>
.exam-list-panel {
  padding: 20px;
}
</style>
