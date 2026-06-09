<template>
  <main class="app-shell">
    <section v-if="initializing" class="app-loading" style="display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:100vh;gap:14px;color:#909399;">
      <el-icon class="is-loading" :size="34"><Loading /></el-icon>
      <span style="font-size:14px;">正在加载…</span>
    </section>
    <section v-else-if="!user" class="login-page-v2">
      <div class="login-container">
        <!-- 左侧品牌区 -->
        <div class="login-brand">
          <div class="brand-overlay"></div>
          <div class="brand-content">
            <div class="brand-icon">
              <img src="/logo.png" alt="广理考试中心" style="width: 120px; height: 120px; border-radius: 20px;" />
            </div>
            <h1 class="brand-title">广理考试中心</h1>
            <p class="brand-desc">智慧在线考试与学习反馈系统</p>
            <div class="brand-features">
              <div class="feature-item">
                <span class="feature-icon">📝</span>
                <span>智能组卷 · 自动评分</span>
              </div>
              <div class="feature-item">
                <span class="feature-icon">📊</span>
                <span>学情分析 · 错题回溯</span>
              </div>
              <div class="feature-item">
                <span class="feature-icon">🔔</span>
                <span>通知触达 · 考试提醒</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧表单区 -->
        <div class="login-form-area">
          <div class="login-form-inner">
            <!-- Tab 切换 -->
            <div class="login-tabs">
              <button :class="['tab-btn', { active: loginMode === 'password' }]" @click="loginMode = 'password'">密码登录</button>
              <button :class="['tab-btn', { active: loginMode === 'code' }]" @click="loginMode = 'code'">验证码登录</button>
            </div>

            <!-- 密码登录 -->
            <el-form v-if="loginMode === 'password'" label-position="top" @submit.prevent="handleLogin">
              <el-form-item label="学号/工号">
                <el-input v-model="loginForm.username" placeholder="请输入学号/工号" size="large" :prefix-icon="User" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="loginForm.password" placeholder="请输入密码" type="password" show-password size="large" :prefix-icon="Lock" @keyup.enter="handleLogin" />
              </el-form-item>
              <el-button type="primary" size="large" class="full-button" :loading="loginLoading" @click="handleLogin">
                登 录
              </el-button>
            </el-form>

            <!-- 验证码登录 -->
            <el-form v-else label-position="top">
              <el-form-item label="邮箱">
                <el-input v-model="codeLoginForm.email" placeholder="请输入已绑定的邮箱" size="large" :prefix-icon="Message" />
              </el-form-item>
              <el-form-item label="验证码">
                <div class="code-row">
                  <el-input v-model="codeLoginForm.code" placeholder="6位验证码" size="large" :maxlength="6" />
                  <el-button :disabled="codeCountdown > 0" :loading="codeSending" size="large" class="code-btn" @click="handleSendLoginCode">
                    {{ codeCountdown > 0 ? `${codeCountdown}s` : '发送验证码' }}
                  </el-button>
                </div>
              </el-form-item>
              <el-button type="primary" size="large" class="full-button" :loading="codeLoginLoading" @click="handleCodeLogin">
                验证并登录
              </el-button>
            </el-form>

            <!-- 底部链接 -->
            <div class="login-links">
              <el-button link type="primary" @click="openRegister">注册账号</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 注册弹窗 -->
      <el-dialog v-model="showRegister" title="注册账号" width="480px" :close-on-click-modal="false">
        <el-form label-position="top" @submit.prevent="handleRegister">
          <el-form-item label="真实姓名">
            <el-input v-model="registerForm.realName" placeholder="请输入真实姓名" size="large" />
          </el-form-item>
          <el-form-item label="密码">
            <el-input v-model="registerForm.password" placeholder="至少6位" type="password" show-password size="large" />
          </el-form-item>
          <el-form-item label="确认密码">
            <el-input v-model="registerForm.confirmPassword" placeholder="请再次输入密码" type="password" show-password size="large" />
          </el-form-item>
          <el-form-item label="角色类型">
            <el-radio-group v-model="registerForm.roleType" size="large">
              <el-radio value="STUDENT">学生</el-radio>
              <el-radio value="TEACHER">教师</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="registerForm.roleType === 'STUDENT'" label="所属班级">
            <el-select v-model="registerForm.classId" placeholder="请选择班级" size="large" style="width:100%">
              <el-option v-for="cls in availableClasses" :key="cls.id" :label="cls.className" :value="cls.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="registerForm.roleType === 'STUDENT'" label="学号">
            <el-input v-model="registerForm.studentNo" placeholder="将作为登录账号，字母或数字至少3位" size="large" />
          </el-form-item>
          <el-form-item v-if="registerForm.roleType === 'TEACHER'" label="工号">
            <el-input v-model="registerForm.teacherNo" placeholder="将作为登录账号，字母或数字至少3位" size="large" />
          </el-form-item>
          <el-form-item v-if="registerForm.roleType === 'TEACHER'" label="职称（选填）">
            <el-input v-model="registerForm.title" placeholder="如：讲师、副教授" size="large" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showRegister = false">取消</el-button>
          <el-button type="primary" :loading="registerLoading" @click="handleRegister">注册</el-button>
        </template>
      </el-dialog>
    </section>

    <ExamTaking v-else-if="takingExam" :attempt-id="takingExam.attemptId" @submit-success="finishExam" />
    <section v-else class="workspace" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
      <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="brand">
          <img src="/logo.png" alt="广理考试中心" class="brand-logo" />
          <div v-show="!sidebarCollapsed">
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
            :title="sidebarCollapsed ? item.title : ''"
          >
            <el-icon v-if="item.icon && iconMap[item.icon]" :size="18">
              <component :is="iconMap[item.icon]" />
            </el-icon>
            <span>{{ item.title }}</span>
          </button>
        </nav>
      </aside>

      <div class="content-panel">
        <header class="topbar">
          <div class="topbar-left">
            <button class="hamburger-btn" @click="sidebarCollapsed = !sidebarCollapsed" :title="sidebarCollapsed ? '展开菜单' : '收起菜单'">
              <span class="hamburger-line"></span>
              <span class="hamburger-line"></span>
              <span class="hamburger-line"></span>
            </button>
            <div>
              <div class="crumb">
                <a class="crumb-link" @click="navigateTo(user.defaultPath)">{{ user.roleLabel }}</a>
                <span class="crumb-sep">/</span>
                <span class="crumb-current">{{ currentMenuTitle }}</span>
              </div>
              <h2>{{ currentMenuTitle }}</h2>
            </div>
          </div>
          <div class="user-box">
            <NotificationBell />
            <UserProfile :user="user" @logout="handleLogout" @profile-updated="handleProfileUpdated" @preference-changed="handlePreferenceChanged" />
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

        <div class="content-body">
        <BasicDataPanel v-if="isBasicPath && user" :path="currentPath" :role="user.primaryRole" :current-user-id="user.id" />
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

        <!-- V2 角色概况 -->
        <AdminDashboard v-else-if="currentPath === '/admin' && user" @navigate="navigateTo" />
        <TeacherDashboard v-else-if="currentPath === '/teacher' && user" @navigate="navigateTo" />
        <StudentDashboard v-else-if="currentPath === '/student' && user" @navigate="navigateTo" />

        <NotFoundPage v-else-if="user" :requested-path="requestedPath404 || currentPath" :default-path="user.defaultPath" @navigate="navigateTo" />
        </div>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import {
  Lock, Message, User, Loading,
  DataAnalysis, OfficeBuilding, Management, Connection, Bell, Collection,
  Document, PieChart, Notebook, Files, Calendar, EditPen, TrendCharts,
  DataLine, House, Clock, Tickets, Reading
} from '@element-plus/icons-vue';

// 侧边栏菜单图标映射（后端返回图标名 → Element Plus 组件）
const iconMap: Record<string, unknown> = {
  DataAnalysis,
  OfficeBuilding,
  Management,
  Connection,
  Bell,
  Collection,
  User,
  Lock,
  Document,
  PieChart,
  Notebook,
  Files,
  Calendar,
  EditPen,
  TrendCharts,
  DataLine,
  House,
  Clock,
  Tickets,
  Reading
};
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
const AdminDashboard = defineAsyncComponent(() => import('./components/AdminDashboard.vue'));
const TeacherDashboard = defineAsyncComponent(() => import('./components/TeacherDashboard.vue'));
const StudentDashboard = defineAsyncComponent(() => import('./components/StudentDashboard.vue'));
const NotificationBell = defineAsyncComponent(() => import('./components/NotificationBell.vue'));
const NotFoundPage = defineAsyncComponent(() => import('./components/NotFoundPage.vue'));
const UserProfile = defineAsyncComponent(() => import('./components/UserProfile.vue'));
import {
  fetchCurrentUser,
  fetchRegisterOptions,
  login,
  loginByCode,
  logout,
  register,
  sendLoginCode,
  type AuthUser,
  type MenuItem,
  type RegisterRequest,
  type RoleCode
} from './api/auth';
import { clearToken, getToken, setToken } from './api/request';

function handleProfileUpdated(updates: { realName?: string; email?: string; phone?: string; emailVerified?: boolean; profile?: Record<string, unknown> }) {
  if (!user.value) return;
  if (updates.realName !== undefined) user.value.realName = updates.realName;
  // 兼容两种回传：①整体 profile 快照；②离散字段（email/phone/emailVerified 均落在后端 profile map）
  if (updates.profile !== undefined) {
    user.value.profile = updates.profile;
  } else {
    const profile = { ...(user.value.profile as Record<string, unknown>) };
    let changed = false;
    if (updates.email !== undefined) { profile.email = updates.email; changed = true; }
    if (updates.phone !== undefined) { profile.phone = updates.phone; changed = true; }
    if (updates.emailVerified !== undefined) { profile.emailVerified = updates.emailVerified; changed = true; }
    if (changed) user.value.profile = profile;
  }
}

const loginForm = reactive({
  username: '',
  password: ''
});

const registerForm = reactive({
  realName: '',
  password: '',
  confirmPassword: '',
  roleType: 'STUDENT' as 'STUDENT' | 'TEACHER',
  classId: null as number | null,
  studentNo: '',
  teacherNo: '',
  title: ''
});

const loginMode = ref<'password' | 'code'>('password');
const showRegister = ref(false);
const codeLoginForm = reactive({ email: '', code: '' });
const codeCountdown = ref(0);
const codeSending = ref(false);
const codeLoginLoading = ref(false);
let codeTimer: ReturnType<typeof setInterval> | null = null;

const takingExam = ref<{ attemptId: number } | null>(null);
const loginLoading = ref(false);
const registerLoading = ref(false);
// 会话恢复进行中标记：首屏（含刷新）在恢复完成前显示加载态，避免误显登录页
const initializing = ref(true);
const user = ref<AuthUser | null>(null);
const menus = ref<MenuItem[]>([]);
const currentPath = ref('/login');
const show404 = ref(false);
const requestedPath404 = ref('');
const routeBlockedMessage = ref('');
const availableClasses = ref<Array<{ id: number; className: string }>>([]);

// 侧边栏（折叠状态持久化为个人偏好，刷新/重登保持）
const sidebarCollapsed = ref(localStorage.getItem('pref_sidebar_collapsed') === '1');
watch(sidebarCollapsed, (collapsed) => {
  localStorage.setItem('pref_sidebar_collapsed', collapsed ? '1' : '0');
});
function handlePreferenceChanged(prefs: { sidebarCollapsed: boolean }) {
  sidebarCollapsed.value = prefs.sidebarCollapsed;
}

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

async function loadRegisterOptions() {
  const response = await fetchRegisterOptions();
  availableClasses.value = response.data.classes.map((item) => ({
    id: item.id,
    className: `${item.className}${item.grade ? `（${item.grade}）` : ''}`
  }));
}

// 打开注册弹窗时再拉取班级列表，避免登录页空跑接口；失败仅提示不阻断
function openRegister() {
  showRegister.value = true;
  loadRegisterOptions().catch((error) => {
    ElMessage.warning(error instanceof Error ? error.message : '班级列表加载失败');
  });
}

async function handleSendLoginCode() {
  if (!codeLoginForm.email) {
    ElMessage.warning('请输入邮箱');
    return;
  }
  codeSending.value = true;
  try {
    await sendLoginCode(codeLoginForm.email);
    ElMessage.success('验证码已发送');
    codeCountdown.value = 60;
    codeTimer = setInterval(() => {
      codeCountdown.value--;
      if (codeCountdown.value <= 0) {
        if (codeTimer) { clearInterval(codeTimer); codeTimer = null; }
      }
    }, 1000);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败');
  } finally {
    codeSending.value = false;
  }
}

async function handleCodeLogin() {
  if (!codeLoginForm.email || !codeLoginForm.code) {
    ElMessage.warning('请输入邮箱和验证码');
    return;
  }
  codeLoginLoading.value = true;
  try {
    const response = await loginByCode(codeLoginForm.email, codeLoginForm.code);
    if (response.data.token) {
      setToken(response.data.token);
    }
    user.value = response.data.user;
    menus.value = response.data.menus;
    navigateTo(resolveLandingPath(response.data.defaultPath), 'replace');
    ElMessage.success(`${response.data.user.roleLabel} ${response.data.user.realName} 登录成功`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '验证码登录失败');
  } finally {
    codeLoginLoading.value = false;
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
    ElMessage.success(`${response.data.user.roleLabel} ${response.data.user.realName} 登录成功`);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败');
  } finally {
    loginLoading.value = false;
  }
}

async function handleRegister() {
  if (!registerForm.realName || !registerForm.password || !registerForm.confirmPassword) {
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

  // 学号/工号即登录账号，提交给后端作为 username
  const account = (registerForm.roleType === 'STUDENT' ? registerForm.studentNo : registerForm.teacherNo).trim();
  const payload: RegisterRequest = {
    username: account,
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
      showRegister.value = false;
      return;
    }
    setToken(response.data.token);
    showRegister.value = false;
    user.value = response.data.user;
    menus.value = response.data.menus;
    navigateTo(resolveLandingPath(response.data.defaultPath), 'replace');
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
  currentPath.value = '/login';
  window.history.replaceState({}, '', '/');
  ElMessage.success('已退出登录');
}

function finishExam() {
  takingExam.value = null;
}

function resolveLandingPath(defaultPath: string) {
  const urlPath = window.location.pathname;
  return menus.value.some((item) => item.path === urlPath) ? urlPath : defaultPath;
}

type NavigateMode = 'push' | 'replace' | 'silent';

function navigateTo(path: string, mode: NavigateMode = 'push') {
  routeBlockedMessage.value = '';
  show404.value = false;
  requestedPath404.value = '';

  if (!user.value) {
    currentPath.value = '/login';
    return;
  }

  const allowed = menus.value.some((item) => item.path === path);
  if (!allowed) {
    // 显示 404 页面而不是静默回首页
    show404.value = true;
    requestedPath404.value = path;
    currentPath.value = path; // 保持 URL 不变
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

onMounted(async () => {
  // 先恢复会话（若本地有 token），恢复期间显示加载态而非登录页，避免刷新后闪现登录页。
  try {
    await restoreSession();
  } finally {
    initializing.value = false;
  }

  // 响应浏览器前进/后退：URL 已由浏览器更新，按当前 path 切换页面（silent 不再回写 history）
  window.addEventListener('popstate', () => {
    if (user.value) {
      navigateTo(window.location.pathname, 'silent');
    }
  });
});
</script>
