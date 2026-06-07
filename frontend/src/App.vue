<template>
  <main class="app-shell">
    <section v-if="!user" class="login-page">
      <div class="login-hero">
        <div class="hero-badge">智慧在线考试系统</div>
        <h1>欢迎使用</h1>
        <p>
          在线考试与学习反馈系统，支持题库管理、智能组卷、在线答题、自动评分与错题分析。
        </p>
        <div class="status-strip">
          <span>后端：{{ healthState }}</span>
          <span>AI：{{ ai?.mode || '待检测' }}</span>
        </div>
      </div>

      <el-card class="login-card" shadow="hover">
        <template #header>
          <div class="card-header">
            <span>{{ isRegisterMode ? '用户注册' : '账号登录' }}</span>
            <el-button link @click="toggleMode">{{ isRegisterMode ? '返回登录' : '注册账号' }}</el-button>
          </div>
        </template>

        <el-form v-if="!isRegisterMode" label-position="top" @submit.prevent="handleLogin">
          <el-form-item label="登录账号">
            <el-input v-model="loginForm.username" placeholder="请输入用户名" size="large" />
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

        <el-form v-else label-position="top" @submit.prevent="handleRegister">
          <el-form-item label="用户名">
            <el-input v-model="registerForm.username" placeholder="请输入用户名（字母或数字）" size="large" />
          </el-form-item>
          <el-form-item label="真实姓名">
            <el-input v-model="registerForm.realName" placeholder="请输入真实姓名" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input
              v-model="registerForm.password"
              placeholder="请输入密码（至少6位）"
              show-password
              size="large"
              type="password"
            />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input
              v-model="registerForm.confirmPassword"
              placeholder="请再次输入密码"
              show-password
              size="large"
              type="password"
            />
          </el-form-item>
          <el-form-item label="角色类型">
            <el-radio-group v-model="registerForm.roleType" size="large">
              <el-radio value="STUDENT">学生</el-radio>
              <el-radio value="TEACHER">教师</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="registerForm.roleType === 'STUDENT'" label="所属班级">
            <el-select v-model="registerForm.classId" placeholder="请选择班级" size="large" style="width: 100%">
              <el-option
                v-for="cls in availableClasses"
                :key="cls.id"
                :label="cls.className"
                :value="cls.id"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="registerForm.roleType === 'STUDENT'" label="学号">
            <el-input v-model="registerForm.studentNo" placeholder="请输入学号" size="large" />
          </el-form-item>
          <el-form-item v-if="registerForm.roleType === 'TEACHER'" label="工号">
            <el-input v-model="registerForm.teacherNo" placeholder="请输入工号" size="large" />
          </el-form-item>
          <el-form-item v-if="registerForm.roleType === 'TEACHER'" label="职称">
            <el-input v-model="registerForm.title" placeholder="请输入职称（如：讲师、副教授）" size="large" />
          </el-form-item>
          <el-button type="primary" size="large" class="full-button" :loading="registerLoading" @click="handleRegister">
            注册账号
          </el-button>
        </el-form>
      </el-card>
    </section>

    <ExamTaking v-else-if="takingExam" :attempt-id="takingExam.attemptId" @submit-success="finishExam" />
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
          </button>
        </nav>
      </aside>

      <div class="content-panel">
        <header class="topbar">
          <div>
            <div class="crumb">{{ user.roleLabel }} / {{ currentMenuTitle }}</div>
            <h2>{{ isManagedModulePath ? currentMenuTitle : overview?.title || '角色首页' }}</h2>
          </div>
          <div class="user-box">
            <NotificationBell />
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
        <QuestionBankPanel v-else-if="isQuestionBankPath && user" :role="user.primaryRole" />
        <PaperPanel v-else-if="isPaperPath && user" :role="user.primaryRole" />
        <ReviewPanel v-else-if="isReviewPath && user" :role="user.primaryRole" />
        <UserManagement v-else-if="isUserPath && user" />
        <RoleManagement v-else-if="isRolePath && user" />
        <SystemLog v-else-if="isLogPath && user" />
        <ExamAnalysis v-else-if="isAnalysisPath && user" />
        <ExamManagement v-else-if="isExamTaskPath && user" />
        <ExamAnalysis v-else-if="isTeacherAnalysisPath && user" scope="teacher" />
        <StudentInsight v-else-if="isTeacherStudentsPath && user" />
        <StudentPanel v-else-if="isStudentModulePath && user" :path="currentPath" />

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
                <span>系统状态</span>
              </div>
            </template>
             <el-descriptions :column="1" border>
              <el-descriptions-item label="后端服务">{{ healthState }}</el-descriptions-item>
              <el-descriptions-item label="AI 服务">{{ ai?.mode || '未知' }}</el-descriptions-item>
            </el-descriptions>
          </el-card>
        </section>
        </template>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
const BasicDataPanel = defineAsyncComponent(() => import('./components/BasicDataPanel.vue'));
const QuestionBankPanel = defineAsyncComponent(() => import('./components/QuestionBankPanel.vue'));
const PaperPanel = defineAsyncComponent(() => import('./components/PaperPanel.vue'));
const ReviewPanel = defineAsyncComponent(() => import('./components/ReviewPanel.vue'));
const ExamTaking = defineAsyncComponent(() => import('./components/ExamTaking.vue'));
const StudentPanel = defineAsyncComponent(() => import('./components/StudentPanel.vue'));
const UserManagement = defineAsyncComponent(() => import('./components/UserManagement.vue'));
const RoleManagement = defineAsyncComponent(() => import('./components/RoleManagement.vue'));
const SystemLog = defineAsyncComponent(() => import('./components/SystemLog.vue'));
const ExamAnalysis = defineAsyncComponent(() => import('./components/ExamAnalysis.vue'));
const ExamManagement = defineAsyncComponent(() => import('./components/ExamManagement.vue'));
const StudentInsight = defineAsyncComponent(() => import('./components/StudentInsight.vue'));
const NotificationBell = defineAsyncComponent(() => import('./components/NotificationBell.vue'));
import {
  fetchCurrentUser,
  fetchRegisterOptions,
  fetchRoleOverview,
  login,
  logout,
  register,
  type AuthUser,
  type MenuItem,
  type RegisterRequest,
  type RoleCode,
  type RoleOverview
} from './api/auth';
import { clearToken, getToken, setToken } from './api/request';
import { fetchAiStatus, fetchHealth, type AiStatusData, type HealthData } from './api/system';

const loginForm = reactive({
  username: '',
  password: ''
});

const registerForm = reactive({
  username: '',
  realName: '',
  password: '',
  confirmPassword: '',
  roleType: 'STUDENT' as 'STUDENT' | 'TEACHER',
  classId: null as number | null,
  studentNo: '',
  teacherNo: '',
  title: ''
});

const isRegisterMode = ref(false);
const takingExam = ref<{ attemptId: number } | null>(null);
const loginLoading = ref(false);
const registerLoading = ref(false);
const user = ref<AuthUser | null>(null);
const menus = ref<MenuItem[]>([]);
const overview = ref<RoleOverview | null>(null);
const currentPath = ref('/login');
const routeBlockedMessage = ref('');
const health = ref<HealthData | null>(null);
const ai = ref<AiStatusData | null>(null);
const availableClasses = ref<Array<{ id: number; className: string }>>([]);

const healthState = computed(() => {
  if (!health.value) return '待检测';
  return health.value.status === 'UP' ? '正常' : '异常';
});

const currentMenuTitle = computed(() => menus.value.find((item) => item.path === currentPath.value)?.title || '角色首页');

const isBasicPath = computed(() => currentPath.value.startsWith('/basic/'));

const isQuestionBankPath = computed(() => currentPath.value === '/question-bank');

const isPaperPath = computed(() => currentPath.value === '/papers');
const isReviewPath = computed(() => currentPath.value === '/reviews');
const isUserPath = computed(() => currentPath.value === '/system/users');
const isRolePath = computed(() => currentPath.value === '/system/roles');
const isLogPath = computed(() => currentPath.value === '/monitor/logs');
const isAnalysisPath = computed(() => currentPath.value === '/exam/analysis');
const isExamTaskPath = computed(() => currentPath.value === '/exam-tasks');
const isTeacherAnalysisPath = computed(() => currentPath.value === '/teacher/analysis');
const isStudentModulePath = computed(() => ['/student/exams', '/student/results', '/student/wrong-questions'].includes(currentPath.value));
const isTeacherStudentsPath = computed(() => currentPath.value === '/teacher/students');

const isManagedModulePath = computed(() => isBasicPath.value || isQuestionBankPath.value || isPaperPath.value || isReviewPath.value || isUserPath.value || isRolePath.value || isLogPath.value || isAnalysisPath.value || isExamTaskPath.value || isTeacherAnalysisPath.value || isStudentModulePath.value || isTeacherStudentsPath.value);

const roleTagType = computed(() => {
  if (user.value?.primaryRole === 'ADMIN') return 'danger';
  if (user.value?.primaryRole === 'TEACHER') return 'warning';
  return 'success';
});

async function loadPublicData() {
  const [healthResponse, aiResponse] = await Promise.all([fetchHealth(), fetchAiStatus()]);
  health.value = healthResponse.data;
  ai.value = aiResponse.data;
}

async function loadRegisterOptions() {
  const response = await fetchRegisterOptions();
  availableClasses.value = response.data.classes.map((item) => ({
    id: item.id,
    className: `${item.className}${item.grade ? `（${item.grade}）` : ''}`
  }));
}

async function toggleMode() {
  isRegisterMode.value = !isRegisterMode.value;
  if (isRegisterMode.value && availableClasses.value.length === 0) {
    try {
      await loadRegisterOptions();
    } catch (error) {
      ElMessage.warning(error instanceof Error ? error.message : '注册选项加载失败，请稍后重试');
    }
  }
}

async function restoreSession() {
  if (!getToken()) {
    return;
  }
  try {
    const response = await fetchCurrentUser();
    user.value = response.data.user;
    menus.value = response.data.menus;
    navigateTo(resolveLandingPath(response.data.defaultPath), 'replace');
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
    navigateTo(resolveLandingPath(response.data.defaultPath), 'replace');
    await loadOverview(response.data.user.primaryRole);
    ElMessage.success(`${response.data.user.roleLabel} ${response.data.user.realName} 登录成功`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败');
  } finally {
    loginLoading.value = false;
  }
}

async function handleRegister() {
  if (!registerForm.username || !registerForm.realName || !registerForm.password || !registerForm.confirmPassword) {
    ElMessage.warning('请完整填写注册信息');
    return;
  }
  if (registerForm.password !== registerForm.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致');
    return;
  }
  if (registerForm.roleType === 'STUDENT' && (!registerForm.classId || !registerForm.studentNo)) {
    ElMessage.warning('学生注册需要选择班级并填写学号');
    return;
  }
  if (registerForm.roleType === 'TEACHER' && !registerForm.teacherNo) {
    ElMessage.warning('教师注册需要填写工号');
    return;
  }

  const payload: RegisterRequest = {
    username: registerForm.username.trim(),
    password: registerForm.password,
    realName: registerForm.realName.trim(),
    roleType: registerForm.roleType,
    classId: registerForm.roleType === 'STUDENT' ? registerForm.classId : null,
    studentNo: registerForm.roleType === 'STUDENT' ? registerForm.studentNo.trim() : undefined,
    teacherNo: registerForm.roleType === 'TEACHER' ? registerForm.teacherNo.trim() : undefined,
    title: registerForm.roleType === 'TEACHER' ? registerForm.title.trim() : undefined
  };

  registerLoading.value = true;
  try {
    const response = await register(payload);
    if (!response.data.token) {
      // 教师账号需管理员审核启用，注册后不自动登录
      ElMessage.success('注册成功！教师账号需管理员审核启用后方可登录');
      isRegisterMode.value = false;
      return;
    }
    setToken(response.data.token);
    user.value = response.data.user;
    menus.value = response.data.menus;
    navigateTo(resolveLandingPath(response.data.defaultPath), 'replace');
    await loadOverview(response.data.user.primaryRole);
    ElMessage.success(`${response.data.user.roleLabel} ${response.data.user.realName} 注册并登录成功`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '注册失败');
  } finally {
    registerLoading.value = false;
  }
}

async function handleLogout() {
  try {
    await logout();
  } catch (error) {
    // 即使后端退出失败，前端也清除本地状态，避免残留 token 影响后续登录。
  }
  clearToken();
  user.value = null;
  menus.value = [];
  overview.value = null;
  currentPath.value = '/login';
  window.history.replaceState({}, '', '/');
  ElMessage.success('已退出登录');
}

function startExam(exam: { attemptId: number }) {
  takingExam.value = { attemptId: exam.attemptId };
}

function finishExam() {
  takingExam.value = null;
  // TODO: Refresh exam list
}

function resolveLandingPath(defaultPath: string) {
  const urlPath = window.location.pathname;
  return menus.value.some((item) => item.path === urlPath) ? urlPath : defaultPath;
}

type NavigateMode = 'push' | 'replace' | 'silent';

function navigateTo(path: string, mode: NavigateMode = 'push') {
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

  // push：用户主动跳转，写入历史以支持浏览器后退；replace：会话恢复/登录落地，不堆叠历史；
  // silent：由 popstate 触发，URL 已是目标值，不再回写 history，避免循环。
  if (mode === 'push') {
    window.history.pushState({}, '', currentPath.value);
  } else if (mode === 'replace') {
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

onMounted(async () => {
  try {
    await loadPublicData();
  } catch (error) {
    ElMessage.warning(error instanceof Error ? error.message : '公共状态加载失败');
  }
  await restoreSession();
  // 响应浏览器前进/后退：URL 已由浏览器更新，按当前 path 切换页面（silent 不再回写 history）
  window.addEventListener('popstate', () => {
    if (user.value) {
      navigateTo(window.location.pathname, 'silent');
    }
  });
});
</script>
