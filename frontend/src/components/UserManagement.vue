<template>
  <section class="user-mgmt">
    <div class="summary-grid">
      <div class="summary-card">
        <span>用户总数</span><strong>{{ summary.total || 0 }}</strong>
      </div>
      <div class="summary-card">
        <span>已启用</span><strong class="text-ok">{{ summary.active || 0 }}</strong>
      </div>
      <div class="summary-card">
        <span>已停用</span><strong class="text-muted">{{ summary.disabled || 0 }}</strong>
      </div>
    </div>

    <div class="toolbar-line">
      <el-input v-model="query.keyword" placeholder="按用户名或姓名搜索" clearable style="width: 220px" @keyup.enter="load" />
      <el-select v-model="query.role" placeholder="角色" clearable style="width: 140px">
        <el-option label="管理员" value="ADMIN" />
        <el-option label="教师" value="TEACHER" />
        <el-option label="学生" value="STUDENT" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      <el-button type="success" @click="openCreate">新建用户</el-button>
    </div>

    <el-table v-loading="loading" :data="users" border>
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
          <span v-if="scope.row.studentNo">学号：{{ scope.row.studentNo }}{{ scope.row.className ? ' / ' + scope.row.className : '' }}</span>
          <span v-else-if="scope.row.teacherNo">工号：{{ scope.row.teacherNo }}{{ scope.row.teacherTitle ? ' / ' + scope.row.teacherTitle : '' }}</span>
          <span v-else>—</span>
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
      class="pager"
      @current-change="load"
      @size-change="onSizeChange"
    />

    <el-dialog v-model="userDialogVisible" :title="editingUser ? '编辑用户' : '新建用户'" width="520px">
      <el-form :model="userForm" label-position="top">
        <el-form-item v-if="!editingUser" label="用户名" required>
          <el-input v-model="userForm.username" placeholder="字母、数字、下划线" />
        </el-form-item>
        <el-form-item v-if="!editingUser" label="密码" required>
          <el-input v-model="userForm.password" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="真实姓名" required>
          <el-input v-model="userForm.realName" placeholder="真实姓名" />
        </el-form-item>
        <el-form-item label="角色" required>
          <el-select v-model="userForm.roleType" style="width:100%">
            <el-option label="管理员" value="ADMIN" />
            <el-option label="教师" value="TEACHER" />
            <el-option label="学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'STUDENT'" label="学号">
          <el-input v-model="userForm.studentNo" placeholder="学号" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'STUDENT'" label="班级">
          <el-select v-model="userForm.classId" placeholder="选择班级" clearable style="width:100%">
            <el-option v-for="cls in classes" :key="cls.id" :label="cls.className" :value="cls.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'TEACHER'" label="工号">
          <el-input v-model="userForm.teacherNo" placeholder="工号" />
        </el-form-item>
        <el-form-item v-if="userForm.roleType === 'TEACHER'" label="职称">
          <el-input v-model="userForm.title" placeholder="职称" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="userForm.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
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
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
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

const users = ref<SystemUser[]>([]);
const summary = ref<Record<string, number>>({});
const loading = ref(false);
const query = reactive<{ keyword: string; role: string; status: number | null }>({ keyword: '', role: '', status: null });
const page = ref(1);
const size = ref(10);
const total = ref(0);

const resetVisible = ref(false);
const resetTarget = ref<SystemUser | null>(null);
const newPassword = ref('');

const userDialogVisible = ref(false);
const editingUser = ref<SystemUser | null>(null);
const userSubmitting = ref(false);
const classes = ref<ClassInfo[]>([]);
const userForm = reactive<CreateUserPayload>({
  username: '',
  password: '',
  realName: '',
  roleType: 'STUDENT',
  studentNo: '',
  classId: null,
  teacherNo: '',
  title: '',
  phone: '',
  email: ''
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    const [userResponse, summaryResponse] = await Promise.all([
      listUsers({ keyword: query.keyword, role: query.role || undefined, status: query.status, page: page.value, size: size.value }),
      fetchUserSummary()
    ]);
    users.value = userResponse.data.list;
    total.value = userResponse.data.total;
    summary.value = summaryResponse.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '用户列表加载失败');
  } finally {
    loading.value = false;
  }
}

function search() {
  page.value = 1;
  load();
}

function onSizeChange() {
  page.value = 1;
  load();
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
  if (classes.value.length === 0) {
    try {
      classes.value = (await listClasses({ status: 1 })).data;
    } catch { /* ignore */ }
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
  userForm.teacherNo = row.teacherNo || '';
  userForm.title = row.teacherTitle || '';
  userForm.phone = row.phone || '';
  userForm.email = row.email || '';
  if (classes.value.length === 0) {
    try {
      classes.value = (await listClasses({ status: 1 })).data;
    } catch { /* ignore */ }
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
  userForm.teacherNo = '';
  userForm.title = '';
  userForm.phone = '';
  userForm.email = '';
}

async function confirmUser() {
  if (!userForm.realName.trim()) {
    ElMessage.warning('请输入真实姓名');
    return;
  }
  if (!editingUser.value) {
    if (!userForm.username.trim() || userForm.username.trim().length < 3) {
      ElMessage.warning('用户名至少需要3位');
      return;
    }
    if (!userForm.password || userForm.password.length < 6) {
      ElMessage.warning('密码至少需要6位');
      return;
    }
  }
  userSubmitting.value = true;
  try {
    if (editingUser.value) {
      const payload: UpdateUserPayload = {
        realName: userForm.realName,
        roleType: userForm.roleType,
        studentNo: userForm.studentNo || undefined,
        classId: userForm.classId,
        teacherNo: userForm.teacherNo || undefined,
        title: userForm.title || undefined,
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
.user-mgmt {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.summary-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.summary-card span {
  color: #909399;
  font-size: 13px;
}
.summary-card strong {
  font-size: 24px;
  color: #303133;
}
.text-ok {
  color: #67c23a;
}
.text-muted {
  color: #909399;
}
.toolbar-line {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
