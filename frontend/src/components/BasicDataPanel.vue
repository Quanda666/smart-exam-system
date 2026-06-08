<template>
  <section class="basic-panel">
    <div class="basic-summary-grid">
      <div v-for="card in summaryCards" :key="card.label" class="basic-summary-card">
        <span>{{ card.label }}</span>
        <strong>{{ card.value }}</strong>
        <small>{{ card.remark }}</small>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="basic-tabs" @tab-change="handleTabChange">
      <el-tab-pane v-if="canViewClasses" label="班级管理" name="classes">
        <div class="toolbar-line">
          <el-input v-model="query.keyword" placeholder="按班级、专业、年级搜索" clearable @keyup.enter="loadActiveData" />
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="loadActiveData">查询</el-button>
        </div>

        <el-form v-if="canManageClasses" class="inline-editor" :model="classForm" label-position="top">
          <el-form-item label="班级名称">
            <el-input v-model="classForm.className" placeholder="例如：23本科计科1班" />
          </el-form-item>
          <el-form-item label="专业">
            <el-input v-model="classForm.major" placeholder="例如：计算机科学与技术" />
          </el-form-item>
          <el-form-item label="年级">
            <el-input v-model="classForm.grade" placeholder="例如：2023级" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="classForm.status">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="saveClass">{{ editingClassId ? '保存修改' : '新增班级' }}</el-button>
            <el-button @click="resetClassForm">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="classes" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="className" label="班级名称" min-width="160" />
          <el-table-column prop="major" label="专业" min-width="160" />
          <el-table-column prop="grade" label="年级" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ statusText(scope.row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="canManageClasses" label="操作" width="160">
            <template #default="scope">
              <el-button link type="primary" @click="editClass(scope.row as ClassInfo)">编辑</el-button>
              <el-button link type="danger" @click="removeClass(Number(scope.row.id))">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canViewSubjects" label="科目管理" name="subjects">
        <div class="toolbar-line">
          <el-input v-model="query.keyword" placeholder="按科目或描述搜索" clearable @keyup.enter="loadActiveData" />
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="loadActiveData">查询</el-button>
        </div>

        <el-form v-if="canManageTeachingBase" class="inline-editor" :model="subjectForm" label-position="top">
          <el-form-item label="科目名称">
            <el-input v-model="subjectForm.subjectName" placeholder="例如：Java程序设计" />
          </el-form-item>
          <el-form-item label="科目描述">
            <el-input v-model="subjectForm.description" placeholder="科目说明" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="subjectForm.status">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="saveSubject">{{ editingSubjectId ? '保存修改' : '新增科目' }}</el-button>
            <el-button @click="resetSubjectForm">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="subjects" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="subjectName" label="科目名称" min-width="160" />
          <el-table-column prop="description" label="描述" min-width="240" />
          <el-table-column label="状态" width="100">
            <template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ statusText(scope.row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="canManageTeachingBase" label="操作" width="160">
            <template #default="scope">
              <el-button link type="primary" @click="editSubject(scope.row as SubjectInfo)">编辑</el-button>
              <el-button v-if="canDeleteTeachingBase" link type="danger" @click="removeSubject(Number(scope.row.id))">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane v-if="canViewSubjects" label="知识点管理" name="knowledge-points">
        <div class="toolbar-line">
          <el-input v-model="query.keyword" placeholder="按知识点或科目搜索" clearable @keyup.enter="loadActiveData" />
          <el-select v-model="query.subjectId" placeholder="科目" clearable>
            <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
          </el-select>
          <el-button type="primary" @click="loadActiveData">查询</el-button>
        </div>

        <el-form v-if="canManageTeachingBase" class="inline-editor" :model="knowledgeForm" label-position="top">
          <el-form-item label="所属科目">
            <el-select v-model="knowledgeForm.subjectId" placeholder="请选择科目">
              <el-option v-for="subject in subjects" :key="subject.id" :label="subject.subjectName" :value="subject.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="知识点名称">
            <el-input v-model="knowledgeForm.pointName" placeholder="例如：集合框架" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="knowledgeForm.sortOrder" :min="0" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="knowledgeForm.status">
              <el-option label="启用" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="saveKnowledgePoint">{{ editingKnowledgeId ? '保存修改' : '新增知识点' }}</el-button>
            <el-button @click="resetKnowledgeForm">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="knowledgePoints" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="subjectName" label="科目" min-width="140" />
          <el-table-column prop="pointName" label="知识点" min-width="160" />
          <el-table-column prop="sortOrder" label="排序" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ statusText(scope.row.status) }}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="canManageTeachingBase" label="操作" width="160">
            <template #default="scope">
              <el-button link type="primary" @click="editKnowledgePoint(scope.row as KnowledgePointInfo)">编辑</el-button>
              <el-button v-if="canDeleteTeachingBase" link type="danger" @click="removeKnowledgePoint(Number(scope.row.id))">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="公告管理" name="notices">
        <div class="toolbar-line">
          <el-input v-model="query.keyword" placeholder="按标题或内容搜索" clearable @keyup.enter="loadActiveData" />
          <el-select v-model="query.status" placeholder="状态" clearable>
            <el-option label="发布" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="loadActiveData">查询</el-button>
        </div>

        <el-form v-if="canManageTeachingBase" class="inline-editor notice-editor" :model="noticeForm" label-position="top">
          <el-form-item label="公告标题">
            <el-input v-model="noticeForm.title" placeholder="请输入公告标题" />
          </el-form-item>
          <el-form-item label="公告内容">
            <el-input v-model="noticeForm.content" type="textarea" :rows="2" placeholder="请输入公告内容" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="noticeForm.status">
              <el-option label="发布" :value="1" />
              <el-option label="停用" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="操作">
            <el-button type="primary" @click="saveNotice">{{ editingNoticeId ? '保存修改' : '发布公告' }}</el-button>
            <el-button @click="resetNoticeForm">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="notices" border>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
          <el-table-column prop="publisherName" label="发布人" width="120" />
          <el-table-column label="状态" width="100">
            <template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '发布' : '停用' }}</el-tag></template>
          </el-table-column>
          <el-table-column v-if="canManageTeachingBase" label="操作" width="160">
            <template #default="scope">
              <template v-if="canManageNotice(scope.row as NoticeInfo)">
                <el-button link type="primary" @click="editNotice(scope.row as NoticeInfo)">编辑</el-button>
                <el-button link type="danger" @click="removeNotice(Number(scope.row.id))">删除</el-button>
              </template>
              <span v-else style="color:#c0c4cc;font-size:12px;">—</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createClass,
  createKnowledgePoint,
  createNotice,
  createSubject,
  deleteClass,
  deleteKnowledgePoint,
  deleteNotice,
  deleteSubject,
  fetchBasicSummary,
  listClasses,
  listKnowledgePoints,
  listNotices,
  listSubjects,
  updateClass,
  updateKnowledgePoint,
  updateNotice,
  updateSubject,
  type ClassInfo,
  type KnowledgePointInfo,
  type NoticeInfo,
  type SubjectInfo
} from '../api/basic';
import type { RoleCode } from '../api/auth';

const props = defineProps<{
  path: string;
  role: RoleCode;
  currentUserId?: number;
}>();

const activeTab = ref(tabFromPath(props.path));
const query = reactive<{ keyword: string; status: number | null; subjectId: number | null }>({
  keyword: '',
  status: null,
  subjectId: null
});

const classes = ref<ClassInfo[]>([]);
const subjects = ref<SubjectInfo[]>([]);
const knowledgePoints = ref<KnowledgePointInfo[]>([]);
const notices = ref<NoticeInfo[]>([]);
const summary = ref<Record<string, number>>({ classes: 0, subjects: 0, knowledgePoints: 0, notices: 0 });

const editingClassId = ref<number | null>(null);
const classForm = reactive({ className: '', major: '', grade: '', status: 1 });
const editingSubjectId = ref<number | null>(null);
const subjectForm = reactive({ subjectName: '', description: '', status: 1 });
const editingKnowledgeId = ref<number | null>(null);
const knowledgeForm = reactive<{ subjectId: number | null; parentId: number | null; pointName: string; sortOrder: number; status: number }>({
  subjectId: null,
  parentId: null,
  pointName: '',
  sortOrder: 0,
  status: 1
});
const editingNoticeId = ref<number | null>(null);
const noticeForm = reactive({ title: '', content: '', status: 1 });

const canManageClasses = computed(() => props.role === 'ADMIN');
const canManageTeachingBase = computed(() => props.role === 'ADMIN' || props.role === 'TEACHER');
// 科目/知识点删除为破坏性操作（影响题库/试卷引用），收紧为仅管理员，与班级一致
const canDeleteTeachingBase = computed(() => props.role === 'ADMIN');
// 公告仅发布者本人或管理员可编辑/删除
function canManageNotice(row: NoticeInfo) {
  return props.role === 'ADMIN' || (row.publisherId != null && row.publisherId === props.currentUserId);
}
const canViewClasses = computed(() => props.role === 'ADMIN' || props.role === 'TEACHER');
const canViewSubjects = computed(() => props.role === 'ADMIN' || props.role === 'TEACHER' || props.role === 'STUDENT');

const summaryCards = computed(() => [
  { label: '班级', value: summary.value.classes || 0, remark: '支撑考试任务发布' },
  { label: '科目', value: summary.value.subjects || 0, remark: '支撑题库归类' },
  { label: '知识点', value: summary.value.knowledgePoints || 0, remark: '支撑组卷与错题分析' },
  { label: '公告', value: summary.value.notices || 0, remark: '支撑考试通知' }
]);

watch(
  () => props.path,
  async (path) => {
    activeTab.value = tabFromPath(path);
    resetQuery();
    await loadActiveData();
  }
);

onMounted(async () => {
  await loadBootstrapData();
});

async function loadBootstrapData() {
  await Promise.all([loadSubjects(), loadSummary()]);
  await loadActiveData();
}

async function loadSummary() {
  try {
    const response = await fetchBasicSummary();
    summary.value = response.data;
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '基础资料统计加载失败');
  }
}

async function loadActiveData() {
  try {
    if (activeTab.value === 'classes') await loadClasses();
    if (activeTab.value === 'subjects') await loadSubjects();
    if (activeTab.value === 'knowledge-points') await loadKnowledgePoints();
    if (activeTab.value === 'notices') await loadNotices();
    await loadSummary();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '基础资料加载失败');
  }
}

async function loadClasses() {
  const response = await listClasses({ keyword: query.keyword, status: query.status });
  classes.value = response.data;
}

async function loadSubjects() {
  const response = await listSubjects({ keyword: activeTab.value === 'subjects' ? query.keyword : undefined, status: activeTab.value === 'subjects' ? query.status : null });
  subjects.value = response.data;
  if (!knowledgeForm.subjectId && subjects.value.length > 0) {
    knowledgeForm.subjectId = subjects.value[0].id;
  }
}

async function loadKnowledgePoints() {
  if (subjects.value.length === 0) await loadSubjects();
  const response = await listKnowledgePoints({ keyword: query.keyword, subjectId: query.subjectId });
  knowledgePoints.value = response.data;
}

async function loadNotices() {
  const response = await listNotices({ keyword: query.keyword, status: query.status });
  notices.value = response.data;
}

function handleTabChange() {
  resetQuery();
  loadActiveData();
}

function resetQuery() {
  query.keyword = '';
  query.status = null;
  query.subjectId = null;
}

function statusText(status: number) {
  return status === 1 ? '启用' : '停用';
}

async function saveClass() {
  const payload = { ...classForm };
  if (editingClassId.value) {
    await updateClass(editingClassId.value, payload);
    ElMessage.success('班级已更新');
  } else {
    await createClass(payload);
    ElMessage.success('班级已新增');
  }
  resetClassForm();
  await loadClasses();
  await loadSummary();
}

function editClass(row: ClassInfo) {
  editingClassId.value = row.id;
  classForm.className = row.className;
  classForm.major = row.major;
  classForm.grade = row.grade;
  classForm.status = row.status;
}

async function removeClass(id: number) {
  await confirmDelete('确认删除该班级吗？');
  await deleteClass(id);
  ElMessage.success('班级已删除');
  await loadClasses();
  await loadSummary();
}

function resetClassForm() {
  editingClassId.value = null;
  classForm.className = '';
  classForm.major = '';
  classForm.grade = '';
  classForm.status = 1;
}

async function saveSubject() {
  const payload = { ...subjectForm };
  if (editingSubjectId.value) {
    await updateSubject(editingSubjectId.value, payload);
    ElMessage.success('科目已更新');
  } else {
    await createSubject(payload);
    ElMessage.success('科目已新增');
  }
  resetSubjectForm();
  await loadSubjects();
  await loadSummary();
}

function editSubject(row: SubjectInfo) {
  editingSubjectId.value = row.id;
  subjectForm.subjectName = row.subjectName;
  subjectForm.description = row.description;
  subjectForm.status = row.status;
}

async function removeSubject(id: number) {
  await confirmDelete('确认删除该科目吗？相关知识点会同步移除。');
  await deleteSubject(id);
  ElMessage.success('科目已删除');
  await loadSubjects();
  await loadKnowledgePoints();
  await loadSummary();
}

function resetSubjectForm() {
  editingSubjectId.value = null;
  subjectForm.subjectName = '';
  subjectForm.description = '';
  subjectForm.status = 1;
}

async function saveKnowledgePoint() {
  if (!knowledgeForm.subjectId) {
    ElMessage.warning('请选择所属科目');
    return;
  }
  const payload = { ...knowledgeForm, subjectId: knowledgeForm.subjectId };
  if (editingKnowledgeId.value) {
    await updateKnowledgePoint(editingKnowledgeId.value, payload);
    ElMessage.success('知识点已更新');
  } else {
    await createKnowledgePoint(payload);
    ElMessage.success('知识点已新增');
  }
  resetKnowledgeForm();
  await loadKnowledgePoints();
  await loadSummary();
}

function editKnowledgePoint(row: KnowledgePointInfo) {
  editingKnowledgeId.value = row.id;
  knowledgeForm.subjectId = row.subjectId;
  knowledgeForm.parentId = row.parentId;
  knowledgeForm.pointName = row.pointName;
  knowledgeForm.sortOrder = row.sortOrder;
  knowledgeForm.status = row.status;
}

async function removeKnowledgePoint(id: number) {
  await confirmDelete('确认删除该知识点吗？');
  await deleteKnowledgePoint(id);
  ElMessage.success('知识点已删除');
  await loadKnowledgePoints();
  await loadSummary();
}

function resetKnowledgeForm() {
  editingKnowledgeId.value = null;
  knowledgeForm.subjectId = subjects.value[0]?.id || null;
  knowledgeForm.parentId = null;
  knowledgeForm.pointName = '';
  knowledgeForm.sortOrder = 0;
  knowledgeForm.status = 1;
}

async function saveNotice() {
  const payload = { ...noticeForm };
  if (editingNoticeId.value) {
    await updateNotice(editingNoticeId.value, payload);
    ElMessage.success('公告已更新');
  } else {
    await createNotice(payload);
    ElMessage.success('公告已发布');
  }
  resetNoticeForm();
  await loadNotices();
  await loadSummary();
}

function editNotice(row: NoticeInfo) {
  editingNoticeId.value = row.id;
  noticeForm.title = row.title;
  noticeForm.content = row.content;
  noticeForm.status = row.status;
}

async function removeNotice(id: number) {
  await confirmDelete('确认删除该公告吗？');
  await deleteNotice(id);
  ElMessage.success('公告已删除');
  await loadNotices();
  await loadSummary();
}

function resetNoticeForm() {
  editingNoticeId.value = null;
  noticeForm.title = '';
  noticeForm.content = '';
  noticeForm.status = 1;
}

async function confirmDelete(message: string) {
  await ElMessageBox.confirm(message, '删除确认', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  });
}

function tabFromPath(path: string) {
  if (path.includes('classes')) return 'classes';
  if (path.includes('subjects')) return 'subjects';
  if (path.includes('knowledge-points')) return 'knowledge-points';
  if (path.includes('notices')) return 'notices';
  // 默认根据角色显示不同的初始标签
  if (props.role === 'ADMIN') return 'classes';
  if (props.role === 'TEACHER') return 'subjects';
  return 'notices';
}
</script>

<style scoped>
.basic-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.basic-summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
  margin-bottom: 8px;
}

.basic-summary-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  padding: 20px;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  box-shadow: 0 2px 8px rgba(102, 126, 234, 0.2);
  transition: transform 0.2s, box-shadow 0.2s;
}

.basic-summary-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(102, 126, 234, 0.3);
}

.basic-summary-card span {
  font-size: 14px;
  opacity: 0.9;
}

.basic-summary-card strong {
  font-size: 32px;
  font-weight: 700;
  line-height: 1;
}

.basic-summary-card small {
  font-size: 12px;
  opacity: 0.8;
  margin-top: 4px;
}

.basic-tabs {
  background: white;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.basic-tabs :deep(.el-tabs__header) {
  margin-bottom: 24px;
}

.basic-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
  padding: 0 24px;
  height: 44px;
  line-height: 44px;
}

.basic-tabs :deep(.el-tabs__active-bar) {
  height: 3px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
}

.toolbar-line {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.toolbar-line .el-input {
  flex: 1;
  min-width: 200px;
}

.toolbar-line .el-select {
  width: 140px;
}

.inline-editor {
  background: #f8f9fa;
  border: 1px solid #e8e8e8;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  align-items: end;
}

.inline-editor :deep(.el-form-item) {
  margin-bottom: 0;
}

.inline-editor :deep(.el-form-item__label) {
  font-size: 13px;
  font-weight: 500;
  color: #606266;
  margin-bottom: 6px;
}

.notice-editor {
  grid-template-columns: 1fr 1fr 120px 1fr;
}

.notice-editor :deep(.el-form-item:first-child) {
  grid-column: 1 / -1;
}

.notice-editor :deep(.el-form-item:nth-child(2)) {
  grid-column: 1 / -1;
}

.el-table {
  border-radius: 8px;
  overflow: hidden;
}

.el-table :deep(.el-table__header) {
  font-weight: 600;
}

.el-table :deep(.el-table__row:hover) {
  background: #f5f7fa;
}
</style>
