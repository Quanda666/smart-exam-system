<template>
  <section class="mp-page">
    <!-- 统计卡片 -->
    <div class="mp-stat-grid">
      <div class="mp-stat-card">
        <div class="mp-stat-header"><el-icon><User /></el-icon> 用户总数</div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-blue"><el-icon><UserFilled /></el-icon></div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">全部账号</div>
            <div class="mp-stat-value">{{ summary.total || 0 }}</div>
          </div>
        </div>
      </div>
      <div class="mp-stat-card">
        <div class="mp-stat-header"><el-icon><CircleCheck /></el-icon> 已启用</div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-green"><el-icon><CircleCheck /></el-icon></div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">正常使用</div>
            <div class="mp-stat-value mp-val-ok">{{ summary.active || 0 }}</div>
          </div>
        </div>
      </div>
      <div class="mp-stat-card">
        <div class="mp-stat-header"><el-icon><CircleClose /></el-icon> 已停用</div>
        <div class="mp-stat-row">
          <div class="mp-stat-icon mp-icon-orange"><el-icon><CircleClose /></el-icon></div>
          <div class="mp-stat-content">
            <div class="mp-stat-label">禁止登录</div>
            <div class="mp-stat-value mp-val-muted">{{ summary.disabled || 0 }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="user-admin-layout">
      <aside class="user-scope-panel">
        <div class="scope-title">组织视图</div>
        <el-input v-model="scopeKeyword" placeholder="搜索班级" clearable :prefix-icon="Search" />
        <el-tree
          class="scope-tree"
          :data="scopeTree"
          node-key="key"
          :default-expanded-keys="['all', 'roles', 'classes']"
          :filter-node-method="filterScopeNode"
          :props="{ label: 'label', children: 'children' }"
          highlight-current
          @node-click="selectScope"
          ref="scopeTreeRef"
        />
      </aside>

      <main class="user-list-panel">
        <div class="scope-summary">
          <div>
            <strong>{{ selectedScopeLabel }}</strong>
            <span>{{ selectedScopeHint }}</span>
          </div>
          <el-button text @click="resetScope">查看全部</el-button>
        </div>

        <div class="mp-toolbar">
          <el-input v-model="query.keyword" placeholder="按用户名或姓名搜索" clearable style="width: 220px" @keyup.enter="search" />
          <el-select v-model="query.role" placeholder="角色" clearable style="width: 140px" @change="search">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="search">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" :icon="Search" @click="search">查询</el-button>
          <span class="mp-toolbar-spacer"></span>
          <el-button :icon="Download" :loading="exporting" @click="exportUsers">导出</el-button>
          <el-button type="success" :icon="Plus" @click="openCreate">新建用户</el-button>
        </div>

        <div class="mp-table-card">
      <!-- 批量操作浮条 -->
      <div v-if="selectedRows.length > 0" class="mp-batch-bar">
        已选择 <span class="mp-batch-count">{{ selectedRows.length }}</span> 项
        <span class="mp-batch-bar-spacer"></span>
        <el-button size="small" type="success" plain :icon="Check" @click="batchSetStatus(1)">批量启用</el-button>
        <el-button size="small" type="warning" plain :icon="Close" @click="batchSetStatus(0)">批量禁用</el-button>
        <el-button size="small" type="danger" plain :icon="Delete" @click="batchDelete">批量删除</el-button>
        <el-button size="small" text @click="clearSelection">取消选择</el-button>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="users"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="100" />
        <el-table-column label="角色" width="100">
          <template #default="scope">
            <el-tag :type="roleTagType(scope.row.roleCodes)">{{ roleLabel(scope.row.roleCodes) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="档案信息" min-width="180">
          <template #default="scope">
            <span v-if="scope.row.studentNo">学号：{{ scope.row.studentNo }}{{ scope.row.className ? ' / 主班：' + scope.row.className : '' }}</span>
            <span v-else-if="scope.row.teacherNo">工号：{{ scope.row.teacherNo }}{{ scope.row.teacherTitle ? ' / ' + scope.row.teacherTitle : '' }}</span>
            <span v-else class="mp-hint">—</span>
          </template>
        </el-table-column>
        <el-table-column label="关系摘要" min-width="220" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.classMemberships">{{ formatMemberships(scope.row.classMemberships) }}</span>
            <span v-else-if="scope.row.teachingAssignments">{{ formatAssignments(scope.row.teachingAssignments) }}</span>
            <span v-else class="mp-hint">—</span>
          </template>
        </el-table-column>
        <el-table-column label="手机" width="130">
          <template #default="scope">{{ scope.row.phone || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="180" />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="scope">
            <el-button link :type="scope.row.status === 1 ? 'warning' : 'success'" @click="toggleStatus(scope.row as SystemUser)">
              {{ scope.row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="primary" @click="openEdit(scope.row as SystemUser)">编辑</el-button>
            <el-button link type="primary" @click="openReset(scope.row as SystemUser)">重置密码</el-button>
            <el-button link type="danger" @click="remove(scope.row as SystemUser)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && users.length === 0" description="暂无用户" />

      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        class="mp-pager"
        @current-change="load"
        @size-change="onSizeChange"
      />
        </div>
      </main>
    </div>

    <el-dialog v-model="userDialogVisible" :title="editingUser ? '编辑用户' : '新建用户'" width="520px">
      <el-form ref="formRef" :model="userForm" :rules="userRules" label-position="top">
        <el-form-item v-if="!editingUser" label="用户名" prop="username">
          <el-input v-model="userForm.username" placeholder="字母、数字、下划线" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="密码" prop="password">
          <el-input v-model="userForm.password" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input v-model="userForm.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色" prop="roleType">
          <el-select v-model="userForm.roleType" style="width:100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'STUDENT'" label="学号" prop="studentNo">
          <el-input v-model="userForm.studentNo" placeholder="学号" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'STUDENT'" label="主班级">
          <el-select v-model="userForm.classId" placeholder="选择主班级" clearable filterable style="width:100%">
            <el-option v-for="cls in classes" :key="cls.id" :label="cls.className" :value="cls.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'STUDENT'" label="选修/临时班级">
          <el-select v-model="userForm.electiveClassIds" multiple placeholder="选择选修或临时班级" clearable filterable style="width:100%">
            <el-option v-for="cls in electiveClasses" :key="cls.id" :label="`${cls.className}${cls.classType ? ' / ' + classTypeText(cls.classType) : ''}`" :value="cls.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'STUDENT'" label="入学年份">
          <el-input v-model="userForm.enrollmentYear" placeholder="例如：2023" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'STUDENT'" label="学院">
          <el-input v-model="userForm.college" placeholder="学院" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'STUDENT'" label="专业">
          <el-input v-model="userForm.major" placeholder="专业" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'TEACHER'" label="工号" prop="teacherNo">
          <el-input v-model="userForm.teacherNo" placeholder="工号" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'TEACHER'" label="入职时间">
          <el-date-picker v-model="userForm.hireDate" type="date" value-format="YYYY-MM-DD" placeholder="选择入职时间" style="width:100%" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'TEACHER'" label="职称">
          <el-input v-model="userForm.title" placeholder="职称" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'TEACHER'" label="学院/部门">
          <el-input v-model="userForm.college" placeholder="学院或部门" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'TEACHER'" label="简介">
          <el-input v-model="userForm.introduction" type="textarea" :rows="2" placeholder="教师简介" />
        </el-form-item>
        <el-form-item label="手机" prop="phone">
          <el-input v-model="userForm.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="userForm.email" placeholder="邮箱" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="userDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="userSubmitting" @click="confirmUser">
          {{ editingUser ? '保存修改' : '创建用户' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="resetVisible" title="重置密码" width="400px">
      <el-form label-position="top">
        <el-form-item :label="`为「${resetTarget?.realName || ''}」设置新密码`">
          <el-input v-model="newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmReset">确认重置</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus';
import {
  User, UserFilled, CircleCheck, CircleClose, Search, Download, Plus,
  Check, Close, Delete
} from '@element-plus/icons-vue';
import {
  createUser,
  deleteUser,
  fetchUserSummary,
  listUsers,
  resetUserPassword,
  updateUser,
  updateUserStatus,
  type CreateUserPayload,
  type SystemUser,
  type UpdateUserPayload
} from '../api/admin';
import { listClasses, type ClassInfo } from '../api/basic';
import { exportToCsv } from '../utils/exportCsv';

const users = ref<SystemUser[]>([]);
const summary = ref<Record<string, number>>({});
const loading = ref(false);
const query = reactive<{ keyword: string; role: string; status: number | null }>({ keyword: '', role: '', status: null });
const page = ref(1);
const size = ref(10);
const total = ref(0);

const tableRef = ref<{ clearSelection: () => void }>();
const selectedRows = ref<SystemUser[]>([]);
const exporting = ref(false);

const resetVisible = ref(false);
const resetTarget = ref<SystemUser | null>(null);
const newPassword = ref('');

const userDialogVisible = ref(false);
const editingUser = ref<SystemUser | null>(null);
const userSubmitting = ref(false);
const classes = ref<ClassInfo[]>([]);
const scopeKeyword = ref('');
const selectedScope = ref<{ type: 'all' | 'role' | 'class'; id?: number; role?: string; label: string }>({ type: 'all', label: '全部用户' });
const scopeTreeRef = ref<{ filter: (keyword: string) => void; setCurrentKey: (key: string) => void }>();
const formRef = ref<FormInstance>();
const userForm = reactive<CreateUserPayload>({
  username: '',
  password: '',
  realName: '',
  roleType: 'STUDENT',
  studentNo: '',
  classId: null,
  electiveClassIds: [],
  enrollmentYear: '',
  college: '',
  major: '',
  teacherNo: '',
  hireDate: null,
  title: '',
  introduction: '',
  phone: '',
  email: ''
});

const electiveClasses = computed(() => classes.value.filter((cls) => cls.id !== userForm.classId));
const selectedScopeLabel = computed(() => selectedScope.value.label);
const selectedScopeHint = computed(() => {
  if (selectedScope.value.type === 'class') return '当前按班级查看学生，列表只显示该班学生';
  if (selectedScope.value.type === 'role') return `当前按${selectedScope.value.label}筛选`;
  return '当前显示全部用户，可从左侧选择班级或角色';
});
const scopeTree = computed(() => [
  { key: 'all', label: `全部用户（${summary.value.total || 0}）`, type: 'all' },
  {
    key: 'roles',
    label: '按角色',
    disabled: true,
    children: [
      { key: 'role:ADMIN', label: '管理员', type: 'role', role: 'ADMIN' },
      { key: 'role:TEACHER', label: '教师', type: 'role', role: 'TEACHER' },
      { key: 'role:STUDENT', label: '学生', type: 'role', role: 'STUDENT' }
    ]
  },
  {
    key: 'classes',
    label: '按班级',
    disabled: true,
    children: classes.value.map((cls) => ({
      key: `class:${cls.id}`,
      label: `${cls.className}${cls.grade ? ` / ${cls.grade}` : ''}`,
      type: 'class',
      id: cls.id
    }))
  }
]);

const userRules = computed<FormRules>(() => ({
  username: editingUser.value ? [] : [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度 3-64 位', trigger: 'blur' }
  ],
  password: editingUser.value ? [] : [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' }
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  roleType: [{ required: true, message: '请选择角色', trigger: 'change' }],
  studentNo: userForm.roleType === 'STUDENT'
    ? [{ required: true, message: '请输入学号', trigger: 'blur' }]
    : [],
  teacherNo: userForm.roleType === 'TEACHER'
    ? [{ required: true, message: '请输入工号', trigger: 'blur' }]
    : [],
  phone: [{ pattern: /^$|^1\d{10}$/, message: '请输入有效的手机号', trigger: 'blur' }],
  email: [{ type: 'email', message: '请输入有效的邮箱', trigger: 'blur' }]
}));

watch(scopeKeyword, (keyword) => {
  scopeTreeRef.value?.filter(keyword);
});

onMounted(async () => {
  await loadClasses();
  await load();
  await nextTick();
  scopeTreeRef.value?.setCurrentKey('all');
});

async function loadClasses() {
  try {
    classes.value = (await listClasses({ status: 1 })).data;
  } catch {
    classes.value = [];
  }
}

async function load() {
  loading.value = true;
  try {
    const role = selectedScope.value.type === 'role'
      ? selectedScope.value.role
      : (selectedScope.value.type === 'class' ? 'STUDENT' : (query.role || undefined));
    const [userResponse, summaryResponse] = await Promise.all([
      listUsers({
        keyword: query.keyword,
        role,
        status: query.status,
        page: selectedScope.value.type === 'class' ? 1 : page.value,
        size: selectedScope.value.type === 'class' ? 10000 : size.value
      }),
      fetchUserSummary()
    ]);
    const list = selectedScope.value.type === 'class'
      ? userResponse.data.list.filter((user) => isUserInClass(user, selectedScope.value.id))
      : userResponse.data.list;
    users.value = list;
    total.value = selectedScope.value.type === 'class' ? list.length : userResponse.data.total;
    summary.value = summaryResponse.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户列表加载失败');
  } finally {
    loading.value = false;
  }
}

function search() {
  if (query.role && selectedScope.value.type === 'role' && query.role !== selectedScope.value.role) {
    selectedScope.value = { type: 'all', label: '全部用户' };
  }
  page.value = 1;
  load();
}

function selectScope(node: any) {
  if (node.disabled) return;
  if (node.type === 'role') {
    selectedScope.value = { type: 'role', role: node.role, label: node.label };
    query.role = node.role;
  } else if (node.type === 'class') {
    selectedScope.value = { type: 'class', id: node.id, label: node.label };
    query.role = 'STUDENT';
  } else {
    selectedScope.value = { type: 'all', label: '全部用户' };
    query.role = '';
  }
  page.value = 1;
  load();
}

function resetScope() {
  selectedScope.value = { type: 'all', label: '全部用户' };
  query.role = '';
  page.value = 1;
  scopeTreeRef.value?.setCurrentKey('all');
  load();
}

function filterScopeNode(keyword: string, data: any) {
  if (!keyword) return true;
  return String(data.label || '').toLowerCase().includes(keyword.toLowerCase());
}

function isUserInClass(user: SystemUser, classId?: number) {
  if (!classId) return false;
  if (user.classId === classId) return true;
  return String(user.classMemberships || '').split(',')
    .some((item) => item.trim().startsWith(`${classId}:`));
}

function onSizeChange() {
  page.value = 1;
  load();
}

function onSelectionChange(rows: SystemUser[]) {
  selectedRows.value = rows;
}

function clearSelection() {
  tableRef.value?.clearSelection();
}

async function batchSetStatus(next: number) {
  const rows = selectedRows.value;
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认${next === 1 ? '启用' : '禁用'}选中的 ${rows.length} 个用户吗？`, '批量操作', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await Promise.all(rows.map((r) => updateUserStatus(r.id, next)));
    ElMessage.success(`已${next === 1 ? '启用' : '禁用'} ${rows.length} 个用户`);
    clearSelection();
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量操作失败');
  }
}

async function batchDelete() {
  const rows = selectedRows.value;
  if (rows.length === 0) return;
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${rows.length} 个用户吗？该操作不可恢复。`, '批量删除', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    await Promise.all(rows.map((r) => deleteUser(r.id)));
    ElMessage.success(`已删除 ${rows.length} 个用户`);
    clearSelection();
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '批量删除失败');
  }
}

async function exportUsers() {
  exporting.value = true;
  try {
    // 按当前筛选条件导出全部匹配项（而非仅当前页）
    const response = await listUsers({ keyword: query.keyword, role: query.role || undefined, status: query.status, page: 1, size: 10000 });
    const rows = response.data.list.map((u) => ({
      id: u.id,
      username: u.username,
      realName: u.realName,
      role: roleLabel(u.roleCodes),
      profile: u.studentNo
        ? `学号:${u.studentNo}${u.className ? '/主班:' + u.className : ''}${u.classMemberships ? '/归属:' + formatMemberships(u.classMemberships) : ''}`
        : (u.teacherNo ? `工号:${u.teacherNo}${u.teacherTitle ? '/' + u.teacherTitle : ''}${u.teachingAssignments ? '/授课:' + formatAssignments(u.teachingAssignments) : ''}` : ''),
      phone: u.phone || '',
      email: u.email || '',
      status: u.status === 1 ? '启用' : '停用',
      createdAt: u.createdAt || ''
    }));
    exportToCsv(`用户列表_${new Date().toISOString().slice(0, 10)}`, [
      { key: 'id', label: 'ID' },
      { key: 'username', label: '用户名' },
      { key: 'realName', label: '姓名' },
      { key: 'role', label: '角色' },
      { key: 'profile', label: '档案信息' },
      { key: 'phone', label: '手机' },
      { key: 'email', label: '邮箱' },
      { key: 'status', label: '状态' },
      { key: 'createdAt', label: '注册时间' }
    ], rows);
    ElMessage.success(`已导出 ${rows.length} 条记录`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '导出失败');
  } finally {
    exporting.value = false;
  }
}

function roleLabel(codes?: string) {
  const first = (codes || '').split(',')[0];
  if (first === 'ADMIN') return '管理员';
  if (first === 'TEACHER') return '教师';
  if (first === 'STUDENT') return '学生';
  return first || '—';
}

function roleTagType(codes?: string) {
  const first = (codes || '').split(',')[0];
  if (first === 'ADMIN') return 'danger';
  if (first === 'TEACHER') return 'warning';
  return 'success';
}

function classTypeText(type?: string) {
  if (type === 'MAJOR') return '主专业班';
  if (type === 'ELECTIVE') return '选修班';
  if (type === 'TEMPORARY') return '临时班';
  return type || '—';
}

function membershipTypeText(type?: string) {
  if (type === 'PRIMARY') return '主班';
  if (type === 'ELECTIVE') return '选修';
  if (type === 'TEMPORARY') return '临时';
  return type || '';
}

function teacherRoleText(role?: string) {
  if (role === 'LECTURER') return '主讲';
  if (role === 'ASSISTANT') return '助教';
  return role || '';
}

function formatMemberships(raw?: string) {
  if (!raw) return '';
  return raw.split(',')
    .map((item) => {
      const [, name, type] = item.split(':');
      return name ? `${name}${type ? `(${membershipTypeText(type)})` : ''}` : item;
    })
    .join('，');
}

function formatAssignments(raw?: string) {
  if (!raw) return '';
  return raw.split(',')
    .map((item) => {
      const [, name, role] = item.split(':');
      return name ? `${name}${role ? `(${teacherRoleText(role)})` : ''}` : item;
    })
    .join('，');
}

function parseElectiveClassIds(raw?: string) {
  if (!raw) return [];
  return raw.split(',')
    .map((item) => {
      const [id, , type] = item.split(':');
      return type === 'ELECTIVE' || type === 'TEMPORARY' ? Number(id) : null;
    })
    .filter((id): id is number => id != null && Number.isFinite(id));
}

async function toggleStatus(row: SystemUser) {
  const next = row.status === 1 ? 0 : 1;
  try {
    await updateUserStatus(row.id, next);
    ElMessage.success(next === 1 ? '已启用' : '已禁用');
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败');
  }
}

function openReset(row: SystemUser) {
  resetTarget.value = row;
  newPassword.value = '';
  resetVisible.value = true;
}

async function confirmReset() {
  if (!resetTarget.value) return;
  if (newPassword.value.length < 6) {
    ElMessage.warning('密码至少需要6位');
    return;
  }
  try {
    await resetUserPassword(resetTarget.value.id, newPassword.value);
    ElMessage.success('密码已重置');
    resetVisible.value = false;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重置失败');
  }
}

async function remove(row: SystemUser) {
  try {
    await ElMessageBox.confirm(`确认删除用户「${row.realName}」吗？该操作不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    await deleteUser(row.id);
    ElMessage.success('用户已删除');
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败');
  }
}

async function openCreate() {
  editingUser.value = null;
  resetUserForm();
  formRef.value?.clearValidate();
  if (classes.value.length === 0) {
    await loadClasses();
  }
  userDialogVisible.value = true;
}

async function openEdit(row: SystemUser) {
  editingUser.value = row;
  userForm.realName = row.realName || '';
  const roleCode = (row.roleCodes || '').split(',')[0];
  userForm.roleType = roleCode || 'STUDENT';
  userForm.studentNo = row.studentNo || '';
  userForm.classId = row.classId ?? null;
  userForm.electiveClassIds = parseElectiveClassIds(row.classMemberships);
  userForm.enrollmentYear = row.enrollmentYear || '';
  userForm.college = row.studentCollege || row.teacherCollege || '';
  userForm.major = row.studentMajor || '';
  userForm.teacherNo = row.teacherNo || '';
  userForm.hireDate = row.hireDate || null;
  userForm.title = row.teacherTitle || '';
  userForm.introduction = row.introduction || '';
  userForm.phone = row.phone || '';
  userForm.email = row.email || '';
  formRef.value?.clearValidate();
  if (classes.value.length === 0) {
    await loadClasses();
  }
  userDialogVisible.value = true;
}

function resetUserForm() {
  userForm.username = '';
  userForm.password = '';
  userForm.realName = '';
  userForm.roleType = 'STUDENT';
  userForm.studentNo = '';
  userForm.classId = null;
  userForm.electiveClassIds = [];
  userForm.enrollmentYear = '';
  userForm.college = '';
  userForm.major = '';
  userForm.teacherNo = '';
  userForm.hireDate = null;
  userForm.title = '';
  userForm.introduction = '';
  userForm.phone = '';
  userForm.email = '';
}

async function confirmUser() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  userSubmitting.value = true;
  try {
    if (editingUser.value) {
      const payload: UpdateUserPayload = {
        realName: userForm.realName,
        roleType: userForm.roleType,
        studentNo: userForm.studentNo || undefined,
        classId: userForm.classId,
        electiveClassIds: userForm.roleType === 'STUDENT' ? userForm.electiveClassIds : [],
        enrollmentYear: userForm.roleType === 'STUDENT' ? userForm.enrollmentYear || undefined : undefined,
        college: userForm.college || undefined,
        major: userForm.roleType === 'STUDENT' ? userForm.major || undefined : undefined,
        teacherNo: userForm.teacherNo || undefined,
        hireDate: userForm.roleType === 'TEACHER' ? userForm.hireDate || null : null,
        title: userForm.title || undefined,
        introduction: userForm.roleType === 'TEACHER' ? userForm.introduction || undefined : undefined,
        phone: userForm.phone || undefined,
        email: userForm.email || undefined
      };
      await updateUser(editingUser.value.id, payload);
      ElMessage.success('用户信息已更新');
    } else {
      await createUser(userForm);
      ElMessage.success('用户创建成功');
    }
    userDialogVisible.value = false;
    await load();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败');
  } finally {
    userSubmitting.value = false;
  }
}
</script>

<style scoped>
.user-admin-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.user-scope-panel,
.user-list-panel {
  min-width: 0;
}

.user-scope-panel {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.scope-title {
  color: #111827;
  font-weight: 700;
}

.scope-tree {
  min-height: 360px;
}

.scope-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 12px 14px;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  background: #f8fbff;
}

.scope-summary div {
  display: grid;
  gap: 4px;
}

.scope-summary span {
  color: #64748b;
  font-size: 12px;
}

@media (max-width: 960px) {
  .user-admin-layout {
    grid-template-columns: 1fr;
  }
}
</style>
