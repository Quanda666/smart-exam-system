<template>
  <section class="exam-mgmt">
    <div class="toolbar-line">
      <el-input v-model="query.keyword" placeholder="按考试或试卷名称搜索" clearable style="width: 240px" @keyup.enter="loadExams" />
      <el-button type="primary" @click="loadExams">查询</el-button>
      <el-button type="success" @click="openCreate">发布考试</el-button>
    </div>

    <el-table v-loading="loading" :data="exams" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="examName" label="考试名称" min-width="180" />
      <el-table-column prop="subjectName" label="科目" width="130" />
      <el-table-column prop="paperName" label="试卷" min-width="150" />
      <el-table-column prop="startTime" label="开始时间" width="170" />
      <el-table-column prop="endTime" label="结束时间" width="170" />
      <el-table-column label="时长" width="100">
        <template #default="scope">{{ scope.row.durationMinutes }} 分钟</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="scope">
          <el-tag :type="phaseType(scope.row as ExamInfo)">{{ phaseText(scope.row as ExamInfo) }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && exams.length === 0" description="还没有考试任务，点击右上角「发布考试」创建" />

    <el-dialog v-model="createVisible" title="发布考试" width="560px">
      <el-form :model="form" label-position="top">
        <el-form-item label="考试名称" required>
          <el-input v-model="form.examName" placeholder="例如：Java程序设计期中考试" />
        </el-form-item>
        <el-form-item label="选择试卷" required>
          <el-select v-model="form.paperId" placeholder="请选择已发布的试卷" style="width: 100%" filterable>
            <el-option
              v-for="paper in papers"
              :key="paper.id"
              :label="`${paper.paperName}（${paper.subjectName} · ${paper.questionCount}题 · ${paper.totalScore}分）`"
              :value="paper.id"
            />
          </el-select>
          <div v-if="papers.length === 0" class="form-hint">暂无已发布的试卷，请先到「试卷管理」创建并发布试卷。</div>
        </el-form-item>
        <el-form-item label="考试说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="开始时间" required>
            <el-date-picker v-model="form.startTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="开始时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="结束时间" required>
            <el-date-picker v-model="form.endTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="结束时间" style="width: 100%" />
          </el-form-item>
        </div>
        <el-form-item label="考试时长（分钟）" required>
          <el-input-number v-model="form.durationMinutes" :min="1" :max="600" />
        </el-form-item>
        <el-form-item label="参考班级" required>
          <el-select v-model="form.classIds" multiple placeholder="请选择参加考试的班级" style="width: 100%">
            <el-option v-for="cls in classes" :key="cls.id" :label="cls.className" :value="cls.id" />
          </el-select>
          <div v-if="classes.length === 0" class="form-hint">暂无可用班级，请先到「班级管理」创建班级。</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">确认发布</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { createExam, listTeacherExams, type ExamInfo } from '../api/exam';
import { listPapers, type PaperInfo } from '../api/paper';
import { listClasses, type ClassInfo } from '../api/basic';

const exams = ref<ExamInfo[]>([]);
const papers = ref<PaperInfo[]>([]);
const classes = ref<ClassInfo[]>([]);
const loading = ref(false);
const submitting = ref(false);
const createVisible = ref(false);
const query = reactive({ keyword: '' });

const form = reactive<{
  paperId: number | null;
  examName: string;
  description: string;
  startTime: string;
  endTime: string;
  durationMinutes: number;
  classIds: number[];
}>({
  paperId: null,
  examName: '',
  description: '',
  startTime: '',
  endTime: '',
  durationMinutes: 60,
  classIds: []
});

onMounted(loadExams);

async function loadExams() {
  loading.value = true;
  try {
    exams.value = (await listTeacherExams({ keyword: query.keyword })).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '考试列表加载失败');
  } finally {
    loading.value = false;
  }
}

async function openCreate() {
  resetForm();
  createVisible.value = true;
  try {
    const [paperResponse, classResponse] = await Promise.all([
      listPapers({ status: 1 }),
      listClasses({ status: 1 })
    ]);
    papers.value = paperResponse.data;
    classes.value = classResponse.data;
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '试卷或班级加载失败');
  }
}

function resetForm() {
  form.paperId = null;
  form.examName = '';
  form.description = '';
  form.startTime = '';
  form.endTime = '';
  form.durationMinutes = 60;
  form.classIds = [];
}

async function submit() {
  if (!form.examName.trim()) {
    ElMessage.warning('请填写考试名称');
    return;
  }
  if (!form.paperId) {
    ElMessage.warning('请选择试卷');
    return;
  }
  if (!form.startTime || !form.endTime) {
    ElMessage.warning('请选择开始和结束时间');
    return;
  }
  if (form.startTime >= form.endTime) {
    ElMessage.warning('开始时间必须早于结束时间');
    return;
  }
  if (form.classIds.length === 0) {
    ElMessage.warning('请至少选择一个参考班级');
    return;
  }

  submitting.value = true;
  try {
    await createExam({
      paperId: form.paperId,
      examName: form.examName.trim(),
      description: form.description.trim(),
      startTime: form.startTime,
      endTime: form.endTime,
      durationMinutes: form.durationMinutes,
      classIds: form.classIds
    });
    ElMessage.success('考试任务已发布');
    createVisible.value = false;
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发布失败');
  } finally {
    submitting.value = false;
  }
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

function phaseType(row: ExamInfo) {
  const text = phaseText(row);
  if (text === '进行中') return 'success';
  if (text === '已结束') return 'info';
  return 'warning';
}
</script>

<style scoped>
.exam-mgmt {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.toolbar-line {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.form-hint {
  color: #e6a23c;
  font-size: 12px;
  margin-top: 4px;
}
</style>
