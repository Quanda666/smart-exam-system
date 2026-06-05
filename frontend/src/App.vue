<template>
  <main class="page-shell">
    <section class="hero-card">
      <div class="hero-badge">第七组 · 在线考试系统</div>
      <h1>智慧在线考试与学习反馈系统</h1>
      <p>
        阶段 1 正在搭建前后端基础骨架、数据库初始化脚本和 OpenAI 兼容 AI 配置预留入口。
      </p>
      <div class="hero-actions">
        <el-button type="primary" @click="loadStatus">刷新系统状态</el-button>
        <el-tag type="success">Vue 3</el-tag>
        <el-tag>Spring Boot 3</el-tag>
        <el-tag type="warning">MySQL 8</el-tag>
        <el-tag type="info">OpenAI Compatible</el-tag>
      </div>
    </section>

    <section class="status-grid">
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>后端健康检查</span>
            <el-tag :type="healthTagType">{{ healthState }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="应用">{{ health?.application || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ health?.status || '-' }}</el-descriptions-item>
          <el-descriptions-item label="数据库">
            {{ health?.database?.message || '等待检测' }}
          </el-descriptions-item>
          <el-descriptions-item label="检测时间">{{ health?.time || '-' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>

      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>AI 辅助模块状态</span>
            <el-tag :type="aiTagType">{{ ai?.mode || '待检测' }}</el-tag>
          </div>
        </template>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="Base URL">{{ ai?.baseUrl || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模型">{{ ai?.model || '-' }}</el-descriptions-item>
          <el-descriptions-item label="模拟模式">{{ ai?.mockEnabled ? '已启用' : '未启用' }}</el-descriptions-item>
          <el-descriptions-item label="密钥状态">{{ ai?.apiKeyMasked || '-' }}</el-descriptions-item>
          <el-descriptions-item label="说明">{{ ai?.notice || '等待检测' }}</el-descriptions-item>
        </el-descriptions>
      </el-card>
    </section>

    <section class="module-card">
      <h2>阶段 1 完成后将进入的主线功能</h2>
      <div class="module-grid">
        <div v-for="module in modules" :key="module.title" class="module-item">
          <h3>{{ module.title }}</h3>
          <p>{{ module.description }}</p>
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { fetchAiStatus, fetchHealth, type AiStatusData, type HealthData } from './api/system';

const health = ref<HealthData | null>(null);
const ai = ref<AiStatusData | null>(null);

const modules = [
  { title: '登录与权限', description: '管理员、教师、学生三类角色登录与菜单控制。' },
  { title: '题库与试卷', description: '多题型题库、规则组卷、试卷预览与发布。' },
  { title: '在线考试', description: '待考列表、限时答题、答案暂存、提交与状态流转。' },
  { title: '评分与反馈', description: '客观题自动评分、主观题阅卷、错题本和知识点分析。' },
  { title: 'AI 辅助', description: 'AI 出题、解析生成、评分建议和错题讲解，全部保留人工确认。' },
  { title: '测试与报告', description: '每阶段完成即验证、验证即记录、记录即可写报告。' }
];

const healthState = computed(() => {
  if (!health.value) return '待检测';
  return health.value.status === 'UP' ? '正常' : '异常';
});

const healthTagType = computed(() => {
  if (!health.value) return 'info';
  return health.value.status === 'UP' ? 'success' : 'danger';
});

const aiTagType = computed(() => {
  if (!ai.value) return 'info';
  if (ai.value.mode === 'REMOTE') return 'success';
  if (ai.value.mode === 'MOCK') return 'warning';
  return 'info';
});

async function loadStatus() {
  try {
    const [healthResponse, aiResponse] = await Promise.all([fetchHealth(), fetchAiStatus()]);
    health.value = healthResponse.data;
    ai.value = aiResponse.data;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '系统状态加载失败');
  }
}

onMounted(loadStatus);
</script>
