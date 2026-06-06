<template>
  <section class="role-mgmt">
    <el-alert
      title="角色为系统预设（管理员 / 教师 / 学生），下表展示各角色的用户数量与可访问的功能页面（权限边界）。"
      type="info"
      :closable="false"
      show-icon
    />

    <el-table v-loading="loading" :data="roles" border>
      <el-table-column label="角色" width="120">
        <template #default="scope">
          <el-tag :type="tagType(scope.row.roleCode)">{{ scope.row.roleName }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="roleCode" label="角色编码" width="130" />
      <el-table-column prop="userCount" label="用户数" width="100" />
      <el-table-column label="可访问页面（权限边界）" min-width="340">
        <template #default="scope">
          <template v-if="scope.row.pages && scope.row.pages.length">
            <el-tag v-for="page in scope.row.pages" :key="page" size="small" class="page-tag">{{ page }}</el-tag>
          </template>
          <span v-else>—</span>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { listRoles, type SystemRole } from '../api/admin';

const roles = ref<SystemRole[]>([]);
const loading = ref(false);

onMounted(load);

async function load() {
  loading.value = true;
  try {
    roles.value = (await listRoles()).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '角色列表加载失败');
  } finally {
    loading.value = false;
  }
}

function tagType(code: string) {
  if (code === 'ADMIN') return 'danger';
  if (code === 'TEACHER') return 'warning';
  return 'success';
}
</script>

<style scoped>
.role-mgmt {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.page-tag {
  margin: 2px 6px 2px 0;
}
</style>
