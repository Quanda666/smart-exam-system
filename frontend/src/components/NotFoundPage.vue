<template>
  <div class="not-found-page">
    <div class="not-found-content">
      <div class="not-found-icon">404</div>
      <h2>页面不存在</h2>
      <p>抱歉，您访问的页面 <code>{{ requestedPath }}</code> 不存在或已被移除。</p>
      <div class="not-found-actions">
        <el-button type="primary" @click="goHome">返回首页</el-button>
        <el-button @click="goBack">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
const props = defineProps<{
  requestedPath?: string;
  defaultPath?: string;
}>();

const emit = defineEmits<{
  navigate: [path: string];
}>();

function goHome() {
  emit('navigate', props.defaultPath || '/');
}

function goBack() {
  window.history.back();
}
</script>

<style scoped>
.not-found-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  padding: 40px 20px;
}

.not-found-content {
  text-align: center;
  max-width: 500px;
}

.not-found-icon {
  font-size: 120px;
  font-weight: 900;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  line-height: 1;
  margin-bottom: 24px;
}

.not-found-content h2 {
  font-size: 28px;
  color: #303133;
  margin-bottom: 12px;
}

.not-found-content p {
  font-size: 15px;
  color: #606266;
  line-height: 1.6;
  margin-bottom: 32px;
}

.not-found-content code {
  background: #f5f7fa;
  padding: 2px 8px;
  border-radius: 4px;
  color: #409eff;
  font-family: 'Monaco', 'Consolas', monospace;
  font-size: 14px;
}

.not-found-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
</style>
