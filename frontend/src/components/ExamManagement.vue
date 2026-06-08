<template>
  <section class="mp-page">
    <div class="mp-toolbar">
      <el-input v-model="query.keyword" placeholder="按考试或试卷名称搜索" clearable style="width: 240px" @keyup.enter="loadExams" />
      <el-button type="primary" :icon="Search" @click="loadExams">查询</el-button>
      <span class="mp-toolbar-spacer"></span>
      <el-button type="success" :icon="Plus" @click="openCreate">发布考试</el-button>
    </div>

    <div class="mp-table-card">
      <div v-if="selectedExams.length > 0" class="mp-batch-bar">
        已选择 <span class="mp-batch-count">{{ selectedExams.length }}</span> 场
        <span class="mp-batch-bar-spacer"></span>
        <el-button size="small" type="danger" plain @click="batchDelete">批量删除</el-button>
        <el-button size="small" text @click="clearSelection">取消选择</el-button>
      </div>

      <el-table ref="tableRef" v-loading="loading" :data="exams" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="examName" label="考试名称" min-width="170" />
      <el-table-column prop="subjectName" label="科目" width="120" />
      <el-table-column prop="paperName" label="试卷" min-width="140" />
      <el-table-column label="开始时间" width="180">
        <template #default="scope">{{ formatDateTime(scope.row.startTime) }}</template>
      </el-table-column>
      <el-table-column label="结束时间" width="180">
        <template #default="scope">{{ formatDateTime(scope.row.endTime) }}</template>
      </el-table-column>
      <el-table-column label="时长" width="90">
        <template #default="scope">{{ scope.row.durationMinutes }} 分钟</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="scope">
          <el-tag :type="phaseType(scope.row as ExamInfo)">{{ phaseText(scope.row as ExamInfo) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="openEdit(scope.row as ExamInfo)">编辑</el-button>
          <el-button link type="success" @click="exportScores(scope.row as ExamInfo)">成绩单</el-button>
          <el-button
            link
            type="warning"
            :disabled="phaseText(scope.row as ExamInfo) === '已结束'"
            @click="close(scope.row as ExamInfo)"
          >
            提前结束
          </el-button>
          <el-button link type="danger" @click="remove(scope.row as ExamInfo)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑考试' : '发布考试'" width="560px">
      <el-form :model="form" label-position="top">
        <el-form-item label="考试名称" required>
          <el-input v-model="form.examName" placeholder="例如：Java程序设计期中考试" />
        </el-form-item>
        <el-form-item label="选择试卷" required>
          <el-input v-if="editingId" :model-value="editPaperName" disabled />
          <el-select v-else v-model="form.paperId" placeholder="请选择已发布的试卷" style="width: 100%" filterable>
            <el-option
              v-for="paper in papers"
              :key="paper.id"
              :label="`${paper.paperName}（${paper.subjectName} · ${paper.questionCount}题 · ${paper.totalScore}分）`"
              :value="paper.id"
            />
          </el-select>
          <div v-if="!editingId && papers.length === 0" class="form-hint">暂无已发布的试卷，请先到「试卷管理」创建并发布试卷。</div>
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
        <el-form-item v-if="!editingId" label="参考班级" required>
          <el-select v-model="form.classIds" multiple placeholder="请选择参加考试的班级" style="width: 100%">
            <el-option v-for="cls in classes" :key="cls.id" :label="cls.className" :value="cls.id" />
          </el-select>
          <div v-if="classes.length === 0" class="form-hint">暂无可用班级，请先到「班级管理」创建班级。</div>
        </el-form-item>
        <el-alert v-else type="info" :closable="false" show-icon class="edit-tip">
          编辑仅调整考试安排（名称/说明/时间/时长），不修改试卷与参考班级；如需更换试卷或班级，请删除后重新发布。
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">{{ editingId ? '保存修改' : '确认发布' }}</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search, Plus } from '@element-plus/icons-vue';
import { closeExam, createExam, deleteExam, exportExamScores, listTeacherExams, updateExam, type ExamInfo } from '../api/exam';
import { listPapers, type PaperInfo } from '../api/paper';
import { listClasses, type ClassInfo } from '../api/basic';
import { formatDateTime } from '../utils/dateFormat';

const exams = ref<ExamInfo[]>([]);
const currentPage = ref(1);
const pageSize = ref(10);
const totalExams = ref(0);
const papers = ref<PaperInfo[]>([]);
const classes = ref<ClassInfo[]>([]);
const loading = ref(false);
const submitting = ref(false);
const dialogVisible = ref(false);
const editingId = ref<number | null>(null);
const editPaperName = ref('');
const query = reactive({ keyword: '' });

const tableRef = ref<{ clearSelection: () => void }>();
const selectedExams = ref<ExamInfo[]>([]);

function onSelectionChange(rows: ExamInfo[]) {
  selectedExams.value = rows;
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

async function batchDelete() {
  const rows = selectedExams.value;
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${rows.length} 场考试吗？未开始的答卷会一并移除，该操作不可恢复。`, '批量删除', {
      type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    await Promise.all(rows.map((r) => deleteExam(r.id)));
    ElMessage.success(`已删除 ${rows.length} 场考试`);
    clearSelection();
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败');
  }
}

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
    const response = await listTeacherExams({ keyword: query.keyword, page: currentPage.value, size: pageSize.value });
    exams.value = response.data.list;
    totalExams.value = response.data.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '考试列表加载失败');
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  currentPage.value = page;
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
  form.classIds = [];
}

async function openCreate() {
  editingId.value = null;
  editPaperName.value = '';
  resetForm();
  dialogVisible.value = true;
  try {
    const [paperResponse, classResponse] = await Promise.all([
      listPapers({ status: 1 }),
      listClasses({ status: 1 })
    ]);
    papers.value = paperResponse.data.list;
    classes.value = classResponse.data;
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '试卷或班级加载失败');
  }
}

function openEdit(row: ExamInfo) {
  editingId.value = row.id;
  editPaperName.value = row.paperName || '';
  form.paperId = row.paperId;
  form.examName = row.examName;
  form.description = row.description || '';
  form.startTime = row.startTime;
  form.endTime = row.endTime;
  form.durationMinutes = row.durationMinutes;
  form.classIds = [];
  dialogVisible.value = true;
}

async function submit() {
  if (!form.examName.trim()) {
    ElMessage.warning('请填写考试名称');
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

  submitting.value = true;
  try {
    if (editingId.value) {
      await updateExam(editingId.value, {
        examName: form.examName.trim(),
        description: form.description.trim(),
        startTime: form.startTime,
        endTime: form.endTime,
        durationMinutes: form.durationMinutes
      });
      ElMessage.success('考试已更新');
    } else {
      if (!form.paperId) {
        ElMessage.warning('请选择试卷');
        submitting.value = false;
        return;
      }
      if (form.classIds.length === 0) {
        ElMessage.warning('请至少选择一个参考班级');
        submitting.value = false;
        return;
      }
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
    }
    dialogVisible.value = false;
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    submitting.value = false;
  }
}

async function close(row: ExamInfo) {
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
    await closeExam(row.id);
    ElMessage.success('考试已结束');
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败');
  }
}

async function remove(row: ExamInfo) {
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
    await deleteExam(row.id);
    ElMessage.success('考试已删除');
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败');
  }
}

async function exportScores(row: ExamInfo) {
  try {
    await exportExamScores(row.id, row.examName);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
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
.edit-tip {
  margin-top: 4px;
}
.exam-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
