<template>
  <section class="system-log">
    <div class="log-topbar">
      <div>
        <h3>系统日志</h3>
        <p>{{ activeTab === 'operation' ? '后台操作记录' : 'AI 调用审计' }}</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="log-workbench">
      <el-tabs v-model="activeTab" class="log-tabs">
        <el-tab-pane label="操作日志" name="operation">
          <el-table v-loading="loading" :data="operationLogs" border max-height="620">
            <el-table-column prop="created_at" label="时间" width="180" />
            <el-table-column label="操作人" width="130">
              <template #default="scope">{{ scope.row.operator_name || '-' }}</template>
            </el-table-column>
            <el-table-column label="动作" width="150">
              <template #default="scope">
                <el-tag size="small" type="info">{{ scope.row.action || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="对象" width="160">
              <template #default="scope">{{ scope.row.target || '-' }}</template>
            </el-table-column>
            <el-table-column label="详情" min-width="260" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.detail || '-' }}</template>
            </el-table-column>
            <el-table-column label="IP" width="150">
              <template #default="scope">{{ scope.row.ip || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无操作日志" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="AI 调用日志" name="ai">
          <div class="ai-log-toolbar">
            <el-select v-model="aiQuery.scene" placeholder="场景" clearable>
              <el-option v-for="item in sceneOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-select v-model="aiQuery.success" placeholder="结果" clearable>
              <el-option label="成功" :value="true" />
              <el-option label="失败" :value="false" />
            </el-select>
            <el-button type="primary" @click="searchAiLogs">查询</el-button>
            <el-button @click="resetAiFilters">重置</el-button>
          </div>

          <el-table v-loading="loading" :data="aiLogs" border max-height="620">
            <el-table-column prop="createdAt" label="时间" width="180" />
            <el-table-column label="用户" width="130">
              <template #default="scope">{{ scope.row.userName || scope.row.userId || '-' }}</template>
            </el-table-column>
            <el-table-column label="场景" width="160">
              <template #default="scope">
                <el-tag size="small">{{ sceneText(scope.row.scene) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="scope">
                <el-tag size="small" :type="isSuccess(scope.row.success) ? 'success' : 'danger'">
                  {{ isSuccess(scope.row.success) ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="提示词" min-width="260" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.prompt || '-' }}</template>
            </el-table-column>
            <el-table-column label="响应" min-width="260" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.response || '-' }}</template>
            </el-table-column>
            <el-table-column label="错误" min-width="180" show-overflow-tooltip>
              <template #default="scope">{{ scope.row.errorMessage || '-' }}</template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无 AI 调用日志" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <div class="log-pagination">
        <el-pagination
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="totalLogs"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { Refresh } from '@element-plus/icons-vue';
import { listAiUsageLogs, listOperationLogs, type AiUsageLog, type OperationLog } from '../api/admin';

type LogTab = 'operation' | 'ai';

const sceneOptions = [
  { label: 'AI 出题', value: 'QUESTION_GENERATE' },
  { label: '题目文档识别', value: 'QUESTION_IMPORT' },
  { label: '课程材料生成', value: 'MATERIAL_GENERATE' },
  { label: '错题讲解', value: 'WRONG_QUESTION_EXPLAIN' },
  { label: '复习建议', value: 'SUGGEST_REVIEW' }
];

const activeTab = ref<LogTab>('operation');
const loading = ref(false);
const operationLogs = ref<OperationLog[]>([]);
const aiLogs = ref<AiUsageLog[]>([]);
const operationPage = ref(1);
const operationSize = ref(10);
const operationTotal = ref(0);
const aiPage = ref(1);
const aiSize = ref(10);
const aiTotal = ref(0);
const aiQuery = reactive<{ scene: string; success: boolean | null }>({
  scene: '',
  success: null
});

const currentPage = computed(() => (activeTab.value === 'operation' ? operationPage.value : aiPage.value));
const pageSize = computed(() => (activeTab.value === 'operation' ? operationSize.value : aiSize.value));
const totalLogs = computed(() => (activeTab.value === 'operation' ? operationTotal.value : aiTotal.value));

onMounted(() => load());
watch(activeTab, () => load());

async function load() {
  loading.value = true;
  try {
    if (activeTab.value === 'operation') {
      const response = await listOperationLogs(operationPage.value, operationSize.value);
      operationLogs.value = response.data.list;
      operationTotal.value = response.data.total;
    } else {
      const response = await listAiUsageLogs(aiPage.value, aiSize.value, {
        scene: aiQuery.scene || undefined,
        success: aiQuery.success
      });
      aiLogs.value = response.data.list;
      aiTotal.value = response.data.total;
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '日志加载失败');
  } finally {
    loading.value = false;
  }
}

function handlePageChange(page: number) {
  if (activeTab.value === 'operation') {
    operationPage.value = page;
  } else {
    aiPage.value = page;
  }
  load();
}

function handleSizeChange(size: number) {
  if (activeTab.value === 'operation') {
    operationSize.value = size;
    operationPage.value = 1;
  } else {
    aiSize.value = size;
    aiPage.value = 1;
  }
  load();
}

function searchAiLogs() {
  aiPage.value = 1;
  load();
}

function resetAiFilters() {
  aiQuery.scene = '';
  aiQuery.success = null;
  aiPage.value = 1;
  load();
}

function isSuccess(value: boolean | number) {
  return value === true || value === 1;
}

function sceneText(scene?: string) {
  return sceneOptions.find((item) => item.value === scene)?.label || scene || '-';
}
</script>

<style scoped>
.system-log {
  display: grid;
  gap: 16px;
}

.log-topbar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.log-topbar h3 {
  margin: 0 0 4px;
  color: #111827;
  font-size: 22px;
}

.log-topbar p {
  margin: 0;
  color: #6b7280;
  font-size: 13px;
}

.log-workbench {
  padding: 18px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.log-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.ai-log-toolbar {
  display: grid;
  grid-template-columns: minmax(160px, 220px) minmax(140px, 180px) auto auto minmax(0, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.log-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

@media (max-width: 900px) {
  .log-topbar,
  .log-pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .ai-log-toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 560px) {
  .ai-log-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
