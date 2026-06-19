<template>
  <section class="system-config-page mp-page">
    <div class="mp-page-header">
      <div>
        <h3 class="mp-page-title">系统配置</h3>
      </div>
      <div class="mp-page-actions">
        <el-select v-model="category" clearable placeholder="配置分类" style="width: 160px" @change="loadConfigs">
          <el-option v-for="item in categories" :key="item" :label="categoryText(item)" :value="item" />
        </el-select>
        <el-button :icon="Clock" @click="openAuditDrawer">Audit</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="loadConfigs">刷新</el-button>
      </div>
    </div>

    <div class="mp-stat-grid">
      <div v-for="card in summaryCards" :key="card.label" class="mp-stat-card">
        <div class="mp-stat-header">{{ card.label }}</div>
        <div class="mp-stat-value">{{ card.value }}</div>
        <div class="mp-stat-label">{{ card.remark }}</div>
      </div>
      <div class="mp-stat-card draft-cache-card">
        <div class="mp-stat-header">草稿缓存</div>
        <div class="mp-stat-value">
          <el-tag :type="draftCacheAlertTagType" effect="dark">{{ draftCacheStatusText }}</el-tag>
        </div>
        <div class="mp-stat-label">
          dirty {{ draftCache?.dirtyCount ?? 0 }} / high {{ draftCache?.dirtyHighThreshold ?? 0 }} / errors {{ draftCache?.errors ?? 0 }}
        </div>
        <div class="mp-stat-label">
          last {{ draftCacheLastFlushText }} / batch {{ draftCache?.lastFlushFlushed ?? 0 }}/{{ draftCache?.lastFlushChecked ?? 0 }}
        </div>
      </div>
    </div>

    <div class="mp-table-card">
      <el-table v-loading="loading" :data="configs" border>
        <el-table-column prop="configKey" label="配置项" min-width="220" />
        <el-table-column label="分类" width="110">
          <template #default="scope">
            <el-tag type="info">{{ categoryText(scope.row.category) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="scope">{{ typeText(scope.row.valueType) }}</template>
        </el-table-column>
        <el-table-column label="当前值" min-width="180">
          <template #default="scope">
            <el-tag v-if="scope.row.valueType === 'BOOLEAN'" :type="scope.row.configValue === 'true' ? 'success' : 'info'">
              {{ scope.row.configValue === 'true' ? '开启' : '关闭' }}
            </el-tag>
            <span v-else>{{ scope.row.configValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="更新时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="scope">
            <el-button link type="primary" :disabled="scope.row.editable !== 1" @click="openEdit(scope.row as SystemConfigItem)">
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && configs.length === 0" description="暂无系统配置" />
    </div>

    <el-dialog v-model="dialogVisible" title="编辑系统配置" width="520px">
      <el-form v-if="editing" label-position="top">
        <el-form-item label="配置项">
          <el-input :model-value="editing.configKey" disabled />
        </el-form-item>
        <el-form-item label="说明">
          <el-input :model-value="editing.description || ''" disabled />
        </el-form-item>
        <el-form-item label="配置值" required>
          <el-switch
            v-if="editing.valueType === 'BOOLEAN'"
            v-model="booleanValue"
            active-text="开启"
            inactive-text="关闭"
          />
          <el-input-number
            v-else-if="editing.valueType === 'NUMBER'"
            v-model="numberValue"
            :min="0"
            :precision="0"
            controls-position="right"
            style="width: 180px"
          />
          <el-input v-else v-model="stringValue" maxlength="1000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveConfig">保存</el-button>
      </template>
    </el-dialog>
    <el-drawer v-model="auditDrawerVisible" title="Config Audit" size="min(920px, 96vw)">
      <div class="audit-toolbar">
        <el-input v-model="auditQuery.keyword" placeholder="Key, value, actor" clearable @keyup.enter="searchAuditLogs" />
        <el-select v-model="auditQuery.category" clearable placeholder="Category">
          <el-option v-for="item in categories" :key="item" :label="categoryText(item)" :value="item" />
        </el-select>
        <el-input v-model="auditQuery.configKey" placeholder="Config key" clearable @keyup.enter="searchAuditLogs" />
        <el-date-picker
          v-model="auditDateRange"
          type="datetimerange"
          value-format="YYYY-MM-DDTHH:mm:ss"
          range-separator="to"
          start-placeholder="Start"
          end-placeholder="End"
          style="width: 100%"
        />
        <el-button type="primary" @click="searchAuditLogs">Search</el-button>
        <el-button @click="resetAuditFilters">Reset</el-button>
        <el-button type="success" plain :icon="Download" :loading="auditExporting" @click="exportAuditLogs">Export</el-button>
      </div>

      <el-table v-loading="auditLoading" :data="auditLogs" border max-height="600">
        <el-table-column label="Log ID" width="150">
          <template #default="scope">
            <div class="config-audit-id-cell">
              <span>#{{ scope.row.id }}</span>
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy system config audit ID"
                aria-label="Copy system config audit ID"
                @click="copyConfigAuditId(scope.row.id)"
              />
              <el-button
                link
                type="primary"
                :icon="DocumentCopy"
                title="Copy system config audit link"
                aria-label="Copy system config audit link"
                @click="copyConfigAuditLink(scope.row.id)"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="Time" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="configKey" label="Config key" min-width="210" show-overflow-tooltip />
        <el-table-column label="Category" width="110">
          <template #default="scope">{{ categoryText(scope.row.category) }}</template>
        </el-table-column>
        <el-table-column prop="oldValue" label="Old value" min-width="160" show-overflow-tooltip />
        <el-table-column prop="newValue" label="New value" min-width="160" show-overflow-tooltip />
        <el-table-column label="Actor" width="140">
          <template #default="scope">{{ scope.row.actorName || scope.row.actorUsername || scope.row.actorId || '-' }}</template>
        </el-table-column>
        <template #empty>
          <el-empty description="No config audit records" />
        </template>
      </el-table>

      <div class="audit-pagination">
        <el-pagination
          :current-page="auditPage"
          :page-size="auditSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="auditTotal"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="handleAuditPageChange"
          @size-change="handleAuditSizeChange"
        />
      </div>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Clock, DocumentCopy, Download, Refresh } from '@element-plus/icons-vue';
import {
  exportSystemConfigAuditLogs,
  listSystemConfigAuditLogs,
  listSystemConfigs,
  updateSystemConfig,
  type SystemConfigAuditLog,
  type SystemConfigItem
} from '../api/system';
import { getDraftCacheStatus, type DraftCacheStatus } from '../api/exam';
import {
  copySystemConfigAuditIdToClipboard,
  copySystemConfigAuditLinkToClipboard
} from '../utils/clipboard';
import { formatDateTime } from '../utils/dateFormat';

const loading = ref(false);
const saving = ref(false);
const auditLoading = ref(false);
const auditExporting = ref(false);
const configs = ref<SystemConfigItem[]>([]);
const auditLogs = ref<SystemConfigAuditLog[]>([]);
const category = ref('');
const dialogVisible = ref(false);
const auditDrawerVisible = ref(false);
const editing = ref<SystemConfigItem | null>(null);
const stringValue = ref('');
const numberValue = ref<number | null>(0);
const booleanValue = ref(false);
const draftCache = ref<DraftCacheStatus | null>(null);
const auditPage = ref(1);
const auditSize = ref(10);
const auditTotal = ref(0);
const auditQuery = ref({
  keyword: '',
  category: '',
  configKey: ''
});
const auditDateRange = ref<[string, string] | null>(null);

const categories = ['EXAM', 'APPROVAL', 'MONITOR', 'SCORE', 'SYSTEM', 'GENERAL'];
const summaryCards = computed(() => [
  { label: '配置项', value: configs.value.length, remark: '当前筛选范围' },
  { label: '可编辑', value: configs.value.filter((item) => item.editable === 1).length, remark: '管理员可维护' },
  { label: '考试配置', value: configs.value.filter((item) => item.category === 'EXAM').length, remark: '考试运行参数' },
  { label: '审批配置', value: configs.value.filter((item) => item.category === 'APPROVAL').length, remark: '发布审批 SLA' },
  { label: '成绩配置', value: configs.value.filter((item) => item.category === 'SCORE').length, remark: '发布与申诉参数' }
]);

const draftCacheStatusText = computed(() => {
  if (!draftCache.value) return '-';
  if (!draftCache.value.enabled) return '未启用';
  if (!draftCache.value.available) return '不可用';
  const mode = draftCache.value.writeBackEnabled ? '写回' : '写穿';
  const alert = draftCacheAlertText(draftCache.value.alertLevel);
  return `${mode} / ${alert}`;
});

const draftCacheAlertTagType = computed(() => {
  if (!draftCache.value) return 'info';
  const level = draftCache.value.alertLevel;
  if (level === 'HIGH') return 'danger';
  if (level === 'WARN') return 'warning';
  if (level === 'OK') return 'success';
  return 'info';
});

const draftCacheLastFlushText = computed(() => {
  const value = draftCache.value?.lastFlushAtEpochMillis || 0;
  if (!value) return '-';
  return new Date(value).toLocaleString();
});

onMounted(async () => {
  await Promise.all([loadConfigs(), loadDraftCacheStatus()]);
});

async function loadConfigs() {
  loading.value = true;
  try {
    configs.value = (await listSystemConfigs(category.value || undefined)).data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '配置加载失败');
  } finally {
    loading.value = false;
  }
}

async function loadDraftCacheStatus() {
  try {
    draftCache.value = (await getDraftCacheStatus()).data;
  } catch {
    draftCache.value = null;
  }
}

async function openAuditDrawer() {
  auditDrawerVisible.value = true;
  await loadAuditLogs();
}

async function loadAuditLogs() {
  auditLoading.value = true;
  try {
    const response = await listSystemConfigAuditLogs(auditPage.value, auditSize.value, {
      keyword: auditQuery.value.keyword.trim() || undefined,
      category: auditQuery.value.category || undefined,
      configKey: auditQuery.value.configKey.trim() || undefined,
      startFrom: auditDateRange.value?.[0],
      startTo: auditDateRange.value?.[1]
    });
    auditLogs.value = response.data.list;
    auditTotal.value = response.data.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Audit logs failed to load');
  } finally {
    auditLoading.value = false;
  }
}

function searchAuditLogs() {
  auditPage.value = 1;
  loadAuditLogs();
}

function resetAuditFilters() {
  auditQuery.value.keyword = '';
  auditQuery.value.category = '';
  auditQuery.value.configKey = '';
  auditDateRange.value = null;
  auditPage.value = 1;
  loadAuditLogs();
}

async function exportAuditLogs() {
  auditExporting.value = true;
  try {
    await exportSystemConfigAuditLogs({
      keyword: auditQuery.value.keyword.trim() || undefined,
      category: auditQuery.value.category || undefined,
      configKey: auditQuery.value.configKey.trim() || undefined,
      startFrom: auditDateRange.value?.[0],
      startTo: auditDateRange.value?.[1]
    });
    ElMessage.success('Config audit export started');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Export failed');
  } finally {
    auditExporting.value = false;
  }
}

function handleAuditPageChange(page: number) {
  auditPage.value = page;
  loadAuditLogs();
}

function handleAuditSizeChange(size: number) {
  auditSize.value = size;
  auditPage.value = 1;
  loadAuditLogs();
}

async function copyConfigAuditId(logId?: number | string | null) {
  try {
    const value = await copySystemConfigAuditIdToClipboard(logId);
    if (!value) return;
    ElMessage.success(`System config audit ID copied: ${value}`);
  } catch {
    ElMessage.error('Failed to copy system config audit ID');
  }
}

async function copyConfigAuditLink(logId?: number | string | null) {
  try {
    const link = await copySystemConfigAuditLinkToClipboard(logId);
    if (!link) return;
    ElMessage.success('System config audit link copied');
  } catch {
    ElMessage.error('Failed to copy system config audit link');
  }
}

function openEdit(row: SystemConfigItem) {
  editing.value = row;
  stringValue.value = row.configValue;
  numberValue.value = Number(row.configValue || 0);
  booleanValue.value = row.configValue === 'true';
  dialogVisible.value = true;
}

async function saveConfig() {
  if (!editing.value) return;
  const nextValue = resolveNextValue(editing.value);
  if (!nextValue) {
    ElMessage.warning('配置值不能为空');
    return;
  }
  saving.value = true;
  try {
    await updateSystemConfig(editing.value.configKey, nextValue);
    ElMessage.success('配置已更新');
    dialogVisible.value = false;
    await loadConfigs();
    await loadDraftCacheStatus();
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}

function resolveNextValue(config: SystemConfigItem) {
  if (config.valueType === 'BOOLEAN') return booleanValue.value ? 'true' : 'false';
  if (config.valueType === 'NUMBER') return numberValue.value === null ? '' : String(numberValue.value);
  return stringValue.value.trim();
}

function categoryText(value?: string) {
  const map: Record<string, string> = {
    EXAM: '考试',
    APPROVAL: '审批',
    MONITOR: '监考',
    SCORE: '成绩',
    SYSTEM: '系统',
    GENERAL: '通用'
  };
  return value ? map[value] || value : '-';
}

function typeText(value?: string) {
  const map: Record<string, string> = {
    STRING: '文本',
    NUMBER: '数字',
    BOOLEAN: '开关'
  };
  return value ? map[value] || value : '-';
}

function draftCacheAlertText(value?: string) {
  const map: Record<string, string> = {
    OK: '正常',
    WARN: '预警',
    HIGH: '高风险',
    DISABLED: '关闭'
  };
  return value ? map[value] || value : '-';
}
</script>

<style scoped>
.system-config-page {
  display: grid;
  gap: 16px;
}

.audit-toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 240px) minmax(130px, 160px) minmax(180px, 240px) minmax(240px, 320px) auto auto auto minmax(0, 1fr);
  gap: 10px;
  margin-bottom: 14px;
}

.audit-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.config-audit-id-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.config-audit-id-cell span {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", monospace;
  color: #111827;
}

.config-audit-id-cell .el-button {
  padding: 0;
}

@media (max-width: 900px) {
  .audit-toolbar {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 560px) {
  .audit-toolbar {
    grid-template-columns: 1fr;
  }
}
</style>
