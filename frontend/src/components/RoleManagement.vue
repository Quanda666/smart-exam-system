<template>
  <section class="role-mgmt">
    <aside class="role-list">
      <div class="role-list-head">
        <strong>角色</strong>
        <span>{{ roles.length }} 个预设角色</span>
      </div>
      <button
        v-for="role in roles"
        :key="role.roleCode"
        type="button"
        :class="['role-card', { active: role.roleCode === selectedRoleCode }]"
        @click="selectRole(role.roleCode)"
      >
        <span class="role-card-main">
          <el-tag :type="tagType(role.roleCode)">{{ role.roleName }}</el-tag>
          <small>{{ role.roleCode }}</small>
        </span>
        <span class="role-card-meta">
          <strong>{{ role.userCount || 0 }}</strong>
          <small>用户</small>
        </span>
      </button>
    </aside>

    <main class="permission-panel" v-loading="loading">
      <div v-if="selectedRole" class="permission-head">
        <div>
          <div class="permission-title">
            <el-tag :type="tagType(selectedRole.roleCode)">{{ selectedRole.roleName }}</el-tag>
            <strong>页面授权</strong>
          </div>
          <p>{{ selectedRole.roleCode }} · {{ checkedLeafCount }} / {{ selectedRole.availablePages?.length || 0 }} 个页面</p>
        </div>
        <el-button type="primary" :loading="saving" @click="savePermissions">保存授权</el-button>
      </div>

      <div v-if="selectedRole" class="permission-body">
        <div class="permission-tree-card">
          <el-tree
            ref="permissionTreeRef"
            :data="permissionTree"
            :props="treeProps"
            node-key="key"
            show-checkbox
            default-expand-all
            :expand-on-click-node="false"
            :render-after-expand="false"
            @check="onTreeCheck"
          >
            <template #default="{ data }">
              <span :class="['permission-node', { required: data.required }]">
                <span>{{ data.label }}</span>
                <small v-if="data.path">{{ data.path }}</small>
                <el-tag v-if="data.required" size="small" type="info">必选</el-tag>
              </span>
            </template>
          </el-tree>
        </div>

        <aside class="permission-summary">
          <div class="summary-card">
            <span>已授权页面</span>
            <strong>{{ checkedLeafCount }}</strong>
          </div>
          <div class="summary-card">
            <span>使用中账号</span>
            <strong>{{ selectedRole.userCount || 0 }}</strong>
          </div>
          <div class="permission-tags">
            <span>当前页面</span>
            <el-tag v-for="page in checkedPages" :key="page" size="small">{{ page }}</el-tag>
            <span v-if="checkedPages.length === 0" class="empty-text">暂无授权页面</span>
          </div>
        </aside>
      </div>
    </main>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { listRoles, updateRolePages, type RolePageOption, type SystemRole } from '../api/admin';

interface PermissionNode {
  key: string;
  label: string;
  path?: string;
  required?: boolean;
  disabled?: boolean;
  children?: PermissionNode[];
}

const roles = ref<SystemRole[]>([]);
const loading = ref(false);
const saving = ref(false);
const selectedRoleCode = ref('');
const checkedPages = ref<string[]>([]);
const permissionTreeRef = ref<{ setCheckedKeys: (keys: string[]) => void; getCheckedKeys: (leafOnly?: boolean) => string[] }>();
const treeProps = { label: 'label', children: 'children', disabled: 'disabled' };

const selectedRole = computed(() => roles.value.find((role) => role.roleCode === selectedRoleCode.value) || null);
const checkedLeafCount = computed(() => checkedPages.value.length);

const permissionTree = computed<PermissionNode[]>(() => {
  const role = selectedRole.value;
  if (!role) return [];
  const pages = role.availablePages || [];
  const required = new Set(requiredPages(role.roleCode));
  return permissionGroups(role.roleCode)
    .map((group) => {
      const children = group.paths
        .map((path) => pages.find((page) => page.path === path))
        .filter((page): page is RolePageOption => Boolean(page))
        .map((page) => ({
          key: page.path,
          label: page.title,
          path: page.path,
          required: required.has(page.path),
          disabled: required.has(page.path)
        }));
      return { key: `${role.roleCode}:${group.key}`, label: group.label, children };
    })
    .filter((group) => group.children.length > 0);
});

watch(selectedRoleCode, async () => {
  await syncCheckedPages();
});

onMounted(load);

async function load() {
  loading.value = true;
  try {
    roles.value = (await listRoles()).data;
    if (!selectedRoleCode.value && roles.value.length > 0) {
      selectedRoleCode.value = roles.value[0].roleCode;
    }
    await syncCheckedPages();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色列表加载失败');
  } finally {
    loading.value = false;
  }
}

function selectRole(roleCode: string) {
  selectedRoleCode.value = roleCode;
}

async function syncCheckedPages() {
  await nextTick();
  const role = selectedRole.value;
  checkedPages.value = role ? [...role.pages] : [];
  permissionTreeRef.value?.setCheckedKeys(checkedPages.value);
}

function onTreeCheck() {
  const role = selectedRole.value;
  if (!role) return;
  const required = requiredPages(role.roleCode);
  const keys = permissionTreeRef.value?.getCheckedKeys(true).map(String) || [];
  checkedPages.value = Array.from(new Set([...required, ...keys]));
  permissionTreeRef.value?.setCheckedKeys(checkedPages.value);
}

async function savePermissions() {
  const role = selectedRole.value;
  if (!role) return;
  saving.value = true;
  try {
    const response = await updateRolePages(role.roleCode, checkedPages.value);
    role.pages = response.data.pages;
    checkedPages.value = [...role.pages];
    permissionTreeRef.value?.setCheckedKeys(checkedPages.value);
    ElMessage.success('授权已保存');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '授权保存失败');
  } finally {
    saving.value = false;
  }
}

function permissionGroups(roleCode: string) {
  if (roleCode === 'ADMIN') {
    return [
      { key: 'workspace', label: '工作台', paths: ['/admin'] },
      { key: 'exam', label: '考试与题库', paths: ['/question-bank', '/papers', '/exam/analysis'] },
      { key: 'data', label: '基础数据', paths: ['/basic/data'] },
      { key: 'security', label: '用户与权限', paths: ['/system/users', '/system/roles'] },
      { key: 'monitor', label: '系统监控', paths: ['/monitor/logs'] }
    ];
  }
  if (roleCode === 'TEACHER') {
    return [
      { key: 'workspace', label: '工作台', paths: ['/teacher'] },
      { key: 'exam', label: '考试管理', paths: ['/exam-tasks', '/reviews', '/teacher/analysis'] },
      { key: 'paper', label: '试卷题库', paths: ['/papers', '/question-bank'] },
      { key: 'data', label: '教学数据', paths: ['/teacher/students', '/basic/data'] }
    ];
  }
  return [
    { key: 'workspace', label: '学习首页', paths: ['/student'] },
    { key: 'exam', label: '我的考试', paths: ['/student/exams', '/student/results', '/student/wrong-questions'] },
    { key: 'data', label: '基础数据', paths: ['/basic/data'] }
  ];
}

function requiredPages(roleCode: string) {
  if (roleCode === 'ADMIN') return ['/admin', '/system/users', '/system/roles'];
  if (roleCode === 'TEACHER') return ['/teacher'];
  if (roleCode === 'STUDENT') return ['/student'];
  return [];
}

function tagType(code: string) {
  if (code === 'ADMIN') return 'danger';
  if (code === 'TEACHER') return 'warning';
  return 'success';
}
</script>

<style scoped>
.role-mgmt {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
}

.role-list,
.permission-panel,
.permission-tree-card,
.permission-summary {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
}

.role-list {
  padding: 16px;
}

.role-list-head,
.permission-head,
.role-card,
.permission-title,
.permission-node {
  display: flex;
  align-items: center;
}

.role-list-head {
  justify-content: space-between;
  margin-bottom: 12px;
}

.role-list-head span,
.permission-head p,
.role-card small,
.permission-node small,
.summary-card span,
.permission-tags > span,
.empty-text {
  color: #6b7280;
}

.role-card {
  width: 100%;
  justify-content: space-between;
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 10px;
  cursor: pointer;
  text-align: left;
}

.role-card.active {
  border-color: #4f46e5;
  box-shadow: 0 8px 22px rgba(79, 70, 229, 0.12);
}

.role-card-main,
.role-card-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.role-card-meta {
  align-items: flex-end;
}

.permission-panel {
  padding: 18px;
  min-height: 560px;
}

.permission-head {
  justify-content: space-between;
  border-bottom: 1px solid #eef2f7;
  padding-bottom: 16px;
  margin-bottom: 16px;
}

.permission-title {
  gap: 10px;
  margin-bottom: 8px;
}

.permission-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
}

.permission-tree-card {
  padding: 12px;
  min-height: 420px;
}

.permission-node {
  gap: 8px;
  min-height: 30px;
}

.permission-node.required {
  font-weight: 600;
}

.permission-summary {
  padding: 14px;
}

.summary-card {
  border-bottom: 1px solid #eef2f7;
  padding: 12px 0;
}

.summary-card:first-child {
  padding-top: 0;
}

.summary-card strong {
  display: block;
  font-size: 28px;
  margin-top: 6px;
}

.permission-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.permission-tags > span {
  flex-basis: 100%;
}

@media (max-width: 960px) {
  .role-mgmt,
  .permission-body {
    grid-template-columns: 1fr;
  }
}
</style>
