<template>
  <section class="system-log">
    <div class="log-header">
      <div>
        <h3>系统操作日志</h3>
        <p>记录用户登录、用户管理等关键操作，最新操作排在最前。</p>
      </div>
      <el-button :icon="Refresh" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="logs" border max-height="600">
      <el-table-column prop="created_at" label="时间" width="180" />
      <el-table-column label="操作人" width="120">
        <template #default="scope">{{ scope.row.operator_name || '—' }}</template>
      </el-table-column>
      <el-table-column label="动作" width="140">
        <template #default="scope">
          <el-tag size="small" type="info">{{ scope.row.action || '—' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="对象" width="150">
        <template #default="scope">{{ scope.row.target || '—' }}</template>
      </el-table-column>
      <el-table-column label="详情" min-width="220">
        <template #default="scope">{{ scope.row.detail || '—' }}</template>
      </el-table-column>
      <el-table-column label="IP" width="140">
        <template #default="scope">{{ scope.row.ip || '—' }}</template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && logs.length === 0" description="暂无操作日志" />
    <div class="log-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        :total="totalLogs"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { listOperationLogs, type OperationLog } from '../api/admin';

const logs = ref<OperationLog[]>([]);
const loading = ref(false);
const currentPage = ref(1);
const pageSize = ref(10);
const totalLogs = ref(0);

onMounted(() => load());

async function load() {
  loading.value = true;
  try {
    const response = await listOperationLogs(currentPage.value, pageSize.value);
    logs.value = response.data.list;
    totalLogs.value = response.data.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作日志加载失败');
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  currentPage.value = page;
  load();
}

function handleSizeChange(size: number) {
  pageSize.value = size;
  currentPage.value = 1;
  load();
}
</script>

<style scoped>
.system-log {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.log-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.log-header h3 {
  margin: 0 0 4px;
}
.log-header p {
  margin: 0;
  color: #909399;
  font-size: 13px;
}
.log-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
