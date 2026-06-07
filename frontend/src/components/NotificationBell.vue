<template>
  <el-popover placement="bottom" :width="380" trigger="click" @show="handleOpen">
    <template #reference>
      <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99">
        <el-button :icon="Bell" circle />
      </el-badge>
    </template>
    <div class="notification-panel">
      <div class="panel-header">
        <span>通知消息</span>
        <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="handleMarkAllRead">全部已读</el-button>
      </div>
      <el-scrollbar v-if="notifications.length > 0" max-height="360px">
        <div
          v-for="item in notifications"
          :key="item.id"
          :class="['notification-item', { unread: item.isRead === 0 }]"
          @click="handleClick(item)"
        >
          <div class="item-title">{{ item.title }}</div>
          <div class="item-content">{{ item.content }}</div>
          <div class="item-time">{{ item.createdAt }}</div>
        </div>
      </el-scrollbar>
      <el-empty v-else description="暂无通知" :image-size="60" />
      <div v-if="total > size" class="panel-footer">
        <el-pagination
          small
          layout="prev, pager, next"
          :total="total"
          :page-size="size"
          :current-page="page"
          @current-change="loadNotifications"
        />
      </div>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { Bell } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { getMyNotifications, getUnreadCount, markRead, markAllRead, type Notification } from '../api/notification';

const notifications = ref<Notification[]>([]);
const unreadCount = ref(0);
const page = ref(1);
const size = ref(10);
const total = ref(0);

onMounted(async () => {
  await loadNotifications(1);
  await loadUnreadCount();
  // 每 30 秒轮询未读数（可选，按需启用）
  setInterval(loadUnreadCount, 30000);
});

// 每次展开铃铛都重新拉取最新通知，避免只显示页面加载时的旧列表
async function handleOpen() {
  await loadNotifications(1);
  await loadUnreadCount();
}

async function loadNotifications(p: number) {
  page.value = p;
  try {
    const response = await getMyNotifications(p, size.value);
    notifications.value = response.data.list;
    total.value = response.data.total;
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '通知加载失败');
  }
}

async function loadUnreadCount() {
  try {
    const response = await getUnreadCount();
    unreadCount.value = response.data.count;
  } catch {
    // 未读数静默失败，不干扰用户
  }
}

async function handleClick(item: Notification) {
  if (item.isRead === 0) {
    try {
      await markRead(item.id);
      item.isRead = 1;
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    } catch {
      // 标记失败不阻断跳转
    }
  }
  if (item.link) {
    window.location.pathname = item.link;
  }
}

async function handleMarkAllRead() {
  try {
    await markAllRead();
    notifications.value.forEach((item) => (item.isRead = 1));
    unreadCount.value = 0;
    ElMessage.success('已全部标记为已读');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败');
  }
}
</script>

<style scoped>
.notification-panel {
  display: flex;
  flex-direction: column;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #e4e7ed;
  font-weight: 600;
}
.notification-item {
  padding: 12px;
  border-bottom: 1px solid #f5f7fa;
  cursor: pointer;
  transition: background 0.2s;
}
.notification-item:hover {
  background: #f5f7fa;
}
.notification-item.unread {
  background: #ecf5ff;
}
.item-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 4px;
}
.item-content {
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.item-time {
  font-size: 12px;
  color: #909399;
}
.panel-footer {
  padding: 8px 0;
  display: flex;
  justify-content: center;
  border-top: 1px solid #e4e7ed;
}
</style>
