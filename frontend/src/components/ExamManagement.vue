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
        <el-button v-if="!editingId && publishStep < 3" type="primary" :icon="ArrowRight" @click="nextPublishStep">下一步</el-button>
        <el-button v-else type="primary" :icon="Check" :loading="submitting" @click="submit">
          {{ editingId ? '保存修改' : '确认发布' }}
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { ArrowLeft, ArrowRight, Check, Plus, Search, Tickets, Timer, User } from '@element-plus/icons-vue';
import {
  closeExam,
  createExam,
  deleteExam,
  exportExamScores,
  listExamTargetStudents,
  listTeacherExams,
  updateExam,
  type ExamInfo,
  type ExamTargetStudentInfo
} from '../api/exam';
import { listPapers, type PaperInfo } from '../api/paper';
import { listClassCourses, type ClassCourseInfo } from '../api/basic';
import { formatDateTime } from '../utils/dateFormat';

type TargetMode = 'CLASS_COURSE' | 'USER';

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
const editingId = ref<number | null>(null);
const editPaperName = ref('');
const publishStep = ref(0);
const paperKeyword = ref('');
const query = reactive({ keyword: '' });

const tableRef = ref<{ clearSelection: () => void }>();
const selectedExams = ref<ExamInfo[]>([]);

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

const dialogTitle = computed(() => (editingId.value ? '编辑考试' : '发布考试'));
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

onMounted(loadExams);

function onSelectionChange(rows: ExamInfo[]) {
  selectedExams.value = rows;
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

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
  form.maxAttempts = 1;
  form.passScore = null;
  form.targetMode = 'CLASS_COURSE';
  form.classCourseIds = [];
  form.studentUserIds = [];
  publishStep.value = 0;
  paperKeyword.value = '';
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

function selectPaper(paper: PaperInfo) {
  form.paperId = paper.id;
  if (!form.examName.trim()) {
    form.examName = `${paper.paperName}考试`;
  }
  if (form.passScore === null && Number(paper.totalScore) > 0) {
    form.passScore = Math.round(Number(paper.totalScore) * 0.6 * 10) / 10;
  }
}

function previousPublishStep() {
  publishStep.value = Math.max(0, publishStep.value - 1);
}

function nextPublishStep() {
  if (!validatePublishStep(publishStep.value)) return;
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

async function submit() {
  if (editingId.value) {
    if (!validateSchedule()) return;
  } else if (!validateAll()) {
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
        durationMinutes: form.durationMinutes,
        maxAttempts: form.maxAttempts,
        passScore: form.passScore
      });
      ElMessage.success('考试已更新');
    } else {
      await createExam({
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
    await Promise.all(rows.map((row) => deleteExam(row.id)));
    ElMessage.success(`已删除 ${rows.length} 场考试`);
    clearSelection();
    await loadExams();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败');
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

function scoreText(value?: number | null) {
  return value === null || value === undefined ? '—' : `${value} 分`;
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

.edit-tip {
  margin-bottom: 14px;
}

:deep(.exam-publish-dialog .el-dialog__body) {
  max-height: min(76vh, 760px);
  overflow: auto;
}

@media (max-width: 760px) {
  .form-row,
  .form-row.three,
  .paper-step,
  .confirm-step {
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
