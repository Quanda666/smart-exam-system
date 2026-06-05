<template>
  <main class="app-shell">
    <section v-if="!user" class="login-page">
      <div class="login-hero">
        <div class="hero-badge">第七组 · 阶段 2</div>
        <h1>登录认证与角色权限</h1>
        <p>
          本阶段实现管理员、教师、学生三类账号登录，登录后按角色展示不同首页、菜单和可访问接口。
        </p>
        <div class="status-strip">
          <span>后端：{{ healthState }}</span>
          <span>AI：{{ ai?.mode || '待检测' }}</span>
          <span>权限：后端 Token 校验</span>
        </div>
      </div>

      <el-card class="login-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>账号登录</span>
            <el-tag type="success">RBAC</el-tag>
          </div>
        </template>

        <el-form label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="登录账号">
            <el-input v-model="loginForm.username" placeholder="请输入 admin / teacher1 / student1" size="large" />
          </el-form-item>
          <el-form-item label="登录密码">
            <el-input
              v-model="loginForm.password"
              placeholder="请输入密码"
              show-password
              size="large"
              type="password"
              @keyup.enter="handleLogin"
            />
          </el-form-item>
          <el-button type="primary" size="large" class="full-button" :loading="loginLoading" @click="handleLogin">
            登录系统
          </el-button>
        </el-form>

        <div class="demo-users">
          <div class="section-title">演示账号</div>
          <button
            v-for="account in demoUsers"
            :key="account.username"
            class="demo-account"
            type="button"
            @click="useDemoUser(account)"
          >
            <strong>{{ account.roleLabel }}：{{ account.username }}</strong>
            <span>{{ account.description }}</span>
          </button>
        </div>
      </el-card>
    </section>

    <section v-else class="workspace">
      <aside class="sidebar">
        <div class="brand">
          <div class="brand-logo">考</div>
          <div>
            <strong>智慧在线考试</strong>
            <span>{{ user.roleLabel }}端</span>
          </div>
        </div>

        <nav class="menu-list">
          <button
            v-for="item in menus"
            :key="item.path"
            type="button"
            :class="['menu-item', { active: currentPath === item.path }]"
            @click="navigateTo(item.path)"
          >
            <span>{{ item.title }}</span>
            <small>{{ item.path }}</small>
          </button>
        </nav>
      </aside>

      <div class="content-panel">
        <header class="topbar">
          <div>
            <div class="crumb">{{ user.roleLabel }} / {{ currentMenuTitle }}</div>
            <h2>{{ isBasicPath ? currentMenuTitle : overview?.title || '角色首页' }}</h2>
          </div>
          <div class="user-box">
            <el-tag :type="roleTagType">{{ user.primaryRole }}</el-tag>
            <span>{{ user.realName }}</span>
            <el-button plain @click="handleLogout">退出</el-button>
          </div>
        </header>

        <el-alert
          v-if="routeBlockedMessage"
          :title="routeBlockedMessage"
          type="warning"
          show-icon
          :closable="false"
          class="mb-16"
        />

        <BasicDataPanel v-if="isBasicPath && user" :path="currentPath" :role="user.primaryRole" />

        <template v-else>
        <section class="overview-grid">
          <el-card class="overview-main" shadow="hover">
            <template #header>
              <div class="card-header">
                <span>{{ overview?.title || '工作台' }}</span>
                <el-tag :type="roleTagType">{{ user.roleLabel }}</el-tag>
              </div>
            </template>
            <p class="overview-desc">{{ overview?.description || '正在加载角色首页数据。' }}</p>
            <div class="metric-grid">
              <div v-for="card in overview?.cards || []" :key="card.label" class="metric-card">
                <span>{{ card.label }}</span>
                <strong>{{ card.value }}</strong>
                <small>{{ card.remark }}</small>
              </div>
            </div>
          </el-card>

          <el-card shadow="hover">
            <template #header>
              <div class="card-header">
                <span>登录状态</span>
                <el-tag type="success">已认证</el-tag>
              </div>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="账号">{{ user.username }}</el-descriptions-item>
              <el-descriptions-item label="姓名">{{ user.realName }}</el-descriptions-item>
              <el-descriptions-item label="角色">{{ user.roleLabel }}</el-descriptions-item>
              <el-descriptions-item label="默认入口">{{ user.defaultPath }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </section>

        <section class="panel-grid">
          <el-card shadow="hover">
            <template #header>
              <div class="card-header">
                <span>当前角色菜单</span>
                <el-tag>{{ menus.length }} 项</el-tag>
              </div>
            </template>
            <div class="access-list">
              <span v-for="item in menus" :key="item.path">{{ item.title }}</span>
            </div>
          </el-card>

          <el-card shadow="hover">
            <template #header>
              <div class="card-header">
                <span>下一阶段关联模块</span>
                <el-tag type="info">规划</el-tag>
              </div>
            </template>
            <ul class="next-list">
              <li v-for="item in overview?.nextModules || []" :key="item">{{ item }}</li>
            </ul>
          </el-card>
        </section>

        <section class="role-test-card">
          <div>
            <h3>权限隔离验证</h3>
            <p>点击下方按钮会请求三个角色专属后端接口，非当前角色接口应返回无权限提示。</p>
          </div>
          <div class="role-actions">
            <el-button @click="testRoleApi('ADMIN')">测管理员接口</el-button>
            <el-button @click="testRoleApi('TEACHER')">测教师接口</el-button>
            <el-button @click="testRoleApi('STUDENT')">测学生接口</el-button>
          </div>
        </section>
        </template>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import BasicDataPanel from './components/BasicDataPanel.vue';
import {
  fetchCurrentUser,
  fetchDemoUsers,
  fetchRoleOverview,
  login,
  logout,
  type AuthUser,
  type DemoUser,
  type MenuItem,
  type RoleCode,
  type RoleOverview
} from './api/auth';
import { clearToken, getToken, setToken } from './api/request';
import { fetchAiStatus, fetchHealth, type AiStatusData, type HealthData } from './api/system';

const loginForm = reactive({
  username: 'admin',
  password: 'admin123'
});

const loginLoading = ref(false);
const user = ref<AuthUser | null>(null);
const menus = ref<MenuItem[]>([]);
const demoUsers = ref<DemoUser[]>([]);
const overview = ref<RoleOverview | null>(null);
const currentPath = ref('/login');
const routeBlockedMessage = ref('');
const health = ref<HealthData | null>(null);
const ai = ref<AiStatusData | null>(null);

const healthState = computed(() => {
  if (!health.value) return '待检测';
  return health.value.status === 'UP' ? '正常' : '异常';
});

const currentMenuTitle = computed(() => menus.value.find((item) => item.path === currentPath.value)?.title || '角色首页');

const isBasicPath = computed(() => currentPath.value.startsWith('/basic/'));

const roleTagType = computed(() => {
  if (user.value?.primaryRole === 'ADMIN') return 'danger';
  if (user.value?.primaryRole === 'TEACHER') return 'warning';
  return 'success';
});

async function loadPublicData() {
  const [demoResponse, healthResponse, aiResponse] = await Promise.all([fetchDemoUsers(), fetchHealth(), fetchAiStatus()]);
  demoUsers.value = demoResponse.data;
  health.value = healthResponse.data;
  ai.value = aiResponse.data;
}

async function restoreSession() {
  if (!getToken()) {
    return;
  }
  try {
    const response = await fetchCurrentUser();
    user.value = response.data.user;
    menus.value = response.data.menus;
    navigateTo(response.data.defaultPath, false);
    await loadOverview(user.value.primaryRole);
  } catch (error) {
    clearToken();
    user.value = null;
    menus.value = [];
    currentPath.value = '/login';
  }
}

async function handleLogin() {
  if (!loginForm.username || !loginForm.password) {
    ElMessage.warning('请输入账号和密码');
    return;
  }

  loginLoading.value = true;
  try {
    const response = await login({ ...loginForm });
    if (response.data.token) {
      setToken(response.data.token);
    }
    user.value = response.data.user;
    menus.value = response.data.menus;
    navigateTo(response.data.defaultPath, false);
    await loadOverview(response.data.user.primaryRole);
    ElMessage.success(`${response.data.user.roleLabel} ${response.data.user.realName} 登录成功`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败');
  } finally {
    loginLoading.value = false;
  }
}

async function handleLogout() {
  try {
    await logout();
  } catch (error) {
    // 即使后端退出失败，前端也清除本地状态，避免残留 token 影响演示。
  }
  clearToken();
  user.value = null;
  menus.value = [];
  overview.value = null;
  currentPath.value = '/login';
  window.history.replaceState({}, '', '/');
  ElMessage.success('已退出登录');
}

function useDemoUser(account: DemoUser) {
  loginForm.username = account.username;
  loginForm.password = account.password;
}

function navigateTo(path: string, updateHistory = true) {
  routeBlockedMessage.value = '';
  if (!user.value) {
    currentPath.value = '/login';
    return;
  }

  const allowed = menus.value.some((item) => item.path === path);
  if (!allowed) {
    routeBlockedMessage.value = `当前${user.value.roleLabel}账号不能访问 ${path}，已回到默认首页。`;
    currentPath.value = user.value.defaultPath;
  } else {
    currentPath.value = path;
  }

  if (updateHistory) {
    window.history.replaceState({}, '', currentPath.value);
  }
}

async function loadOverview(role: RoleCode) {
  try {
    const response = await fetchRoleOverview(role);
    overview.value = response.data;
  } catch (error) {
    overview.value = null;
    ElMessage.error(error instanceof Error ? error.message : '角色首页加载失败');
  }
}

async function testRoleApi(role: RoleCode) {
  try {
    const response = await fetchRoleOverview(role);
    ElMessage.success(`${response.data.title} 接口访问成功`);
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '接口访问被拒绝');
  }
}

onMounted(async () => {
  try {
    await loadPublicData();
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '公共状态加载失败');
  }
  await restoreSession();
});
</script>
