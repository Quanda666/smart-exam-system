<template>
  <el-dropdown trigger="click" @command="handleCommand" @visible-change="handleDropdownVisibleChange">
    <div class="user-profile-trigger">
      <el-avatar :size="36" :src="avatarUrl" class="user-avatar">
        <el-icon><User /></el-icon>
      </el-avatar>
      <span v-if="!compact" class="user-name">{{ user?.realName }}</span>
      <el-icon v-if="!compact"><ArrowDown /></el-icon>
    </div>
    <template #dropdown>
      <el-dropdown-menu class="user-profile-menu">
        <!-- 用户信息卡片 -->
        <div class="profile-card">
          <el-avatar :size="56" :src="avatarUrl" class="profile-avatar">
            <el-icon :size="28"><User /></el-icon>
          </el-avatar>
          <div class="profile-info">
            <div class="profile-name">{{ user?.realName }}</div>
            <div class="profile-role">
              <el-tag :type="roleTagType" size="small" effect="light">{{ user?.roleLabel }}</el-tag>
            </div>
            <div class="profile-username">@{{ user?.username }}</div>
          </div>
        </div>

        <el-dropdown-item divided command="profile">
          <el-icon><User /></el-icon>
          <span>个人信息</span>
        </el-dropdown-item>
        <el-dropdown-item command="email">
          <el-icon><Message /></el-icon>
          <span>{{ userEmail ? '更换邮箱' : '绑定邮箱' }}</span>
        </el-dropdown-item>
        <el-dropdown-item command="password">
          <el-icon><Lock /></el-icon>
          <span>修改密码</span>
        </el-dropdown-item>
        <el-dropdown-item command="security">
          <el-icon><Connection /></el-icon>
          <span>安全中心</span>
        </el-dropdown-item>
        <el-dropdown-item command="preference">
          <el-icon><Setting /></el-icon>
          <span>偏好设置</span>
        </el-dropdown-item>
        <el-dropdown-item divided command="logout" class="logout-item">
          <el-icon><SwitchButton /></el-icon>
          <span>退出登录</span>
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>

  <!-- 个人信息弹窗 -->
  <el-dialog v-model="profileVisible" title="个人信息" width="520px" :close-on-click-modal="false">
    <!-- 可编辑区 -->
    <el-form :model="profileForm" label-width="90px" label-position="left">
      <el-form-item label="真实姓名">
        <el-input v-model="profileForm.realName" placeholder="请输入真实姓名" maxlength="64" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="profileForm.phone" placeholder="请输入手机号（选填）" maxlength="32" />
      </el-form-item>
    </el-form>

    <!-- 只读信息区 -->
    <el-descriptions :column="1" border size="small" class="profile-readonly">
      <el-descriptions-item label="用户名">{{ user?.username }}</el-descriptions-item>
      <el-descriptions-item label="角色身份">
        <el-tag :type="roleTagType" size="small">{{ user?.roleLabel }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item v-if="user?.primaryRole === 'STUDENT'" label="学号">
        {{ studentNo || '—' }}
      </el-descriptions-item>
      <el-descriptions-item v-if="user?.primaryRole === 'STUDENT'" label="班级">
        {{ classText || '—' }}
      </el-descriptions-item>
      <el-descriptions-item v-if="user?.primaryRole === 'TEACHER'" label="工号">
        {{ teacherNo || '—' }}
      </el-descriptions-item>
      <el-descriptions-item v-if="user?.primaryRole === 'TEACHER'" label="职称">
        {{ title || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="邮箱">
        <span style="margin-right: 8px;">{{ userEmail || '未绑定' }}</span>
        <el-button link type="primary" size="small" @click="handleCommand('email')">
          {{ userEmail ? '更换' : '绑定' }}
        </el-button>
      </el-descriptions-item>
    </el-descriptions>

    <template #footer>
      <el-button @click="profileVisible = false">取消</el-button>
      <el-button type="primary" :loading="profileSaving" @click="saveProfile">保存</el-button>
    </template>
  </el-dialog>

  <!-- 绑定/更换邮箱弹窗 -->
  <el-dialog v-model="emailVisible" :title="userEmail ? '更换邮箱' : '绑定邮箱'" width="450px" :close-on-click-modal="false">
    <el-form label-position="top">
      <el-form-item label="邮箱地址">
        <el-input v-model="emailForm.email" placeholder="请输入邮箱地址" />
      </el-form-item>
      <el-form-item label="验证码">
        <div class="code-input-group">
          <el-input v-model="emailForm.code" placeholder="请输入6位验证码" :maxlength="6" />
          <el-button :disabled="emailCountdown > 0" :loading="emailSending" @click="sendEmailCode">
            {{ emailCountdown > 0 ? `${emailCountdown}s` : '发送验证码' }}
          </el-button>
        </div>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emailVisible = false">取消</el-button>
      <el-button type="primary" :loading="emailBinding" @click="bindEmailConfirm">确认绑定</el-button>
    </template>
  </el-dialog>

  <!-- 修改密码弹窗 -->
  <el-dialog v-model="passwordVisible" title="修改密码" width="450px" :close-on-click-modal="false">
    <el-form label-position="top">
      <el-form-item label="当前密码">
        <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入当前密码" />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="请输入新密码（至少6位）" />
      </el-form-item>
      <el-form-item label="确认新密码">
        <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="passwordVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordChanging" @click="changePasswordConfirm">确认修改</el-button>
    </template>
  </el-dialog>

  <!-- 安全中心弹窗 -->
  <el-dialog v-model="securityVisible" title="安全中心" width="560px">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="账号状态">
        <el-tag type="success">正常</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="绑定邮箱">
        <span style="margin-right: 8px;">{{ userEmail || '未绑定' }}</span>
        <el-tag v-if="userEmail && emailVerified" type="success" size="small">已验证</el-tag>
        <el-button v-if="!userEmail" link type="primary" size="small" @click="handleCommand('email')">立即绑定</el-button>
      </el-descriptions-item>
      <el-descriptions-item label="绑定手机">
        <span style="margin-right: 8px;">{{ userPhone || '未绑定' }}</span>
        <el-button link type="primary" size="small" @click="handleCommand('profile')">
          {{ userPhone ? '修改' : '完善' }}
        </el-button>
      </el-descriptions-item>
      <el-descriptions-item label="登录密码">
        <span style="margin-right: 8px;">••••••••</span>
        <el-button link type="primary" size="small" @click="handleCommand('password')">修改密码</el-button>
      </el-descriptions-item>
    </el-descriptions>

    <!-- 登录日志 -->
    <div class="login-logs-section">
      <div class="section-title">最近登录记录</div>
      <el-table :data="loginLogs" v-loading="loginLogsLoading" size="small" max-height="220" empty-text="暂无登录记录">
        <el-table-column prop="action" label="方式" width="110" />
        <el-table-column label="IP 地址" min-width="120">
          <template #default="scope">{{ scope.row.ip || '—' }}</template>
        </el-table-column>
        <el-table-column label="登录时间" min-width="170">
          <template #default="scope">{{ formatDateTime(scope.row.created_at) }}</template>
        </el-table-column>
      </el-table>
    </div>

    <div class="security-tips">
      <el-alert title="安全提示" type="info" :closable="false" show-icon>
        <ul>
          <li>建议绑定邮箱，以便在忘记密码时通过验证码登录找回账号</li>
          <li>定期修改密码，密码长度至少 6 位，建议包含字母、数字组合</li>
          <li>不要在公共设备上保存登录状态，使用完毕请及时退出</li>
        </ul>
      </el-alert>
    </div>
    <template #footer>
      <el-button type="primary" @click="securityVisible = false">知道了</el-button>
    </template>
  </el-dialog>

  <!-- 偏好设置弹窗 -->
  <el-dialog v-model="preferenceVisible" title="偏好设置" width="460px">
    <el-form label-width="140px" label-position="left">
      <el-form-item label="侧边栏默认折叠">
        <el-switch v-model="sidebarCollapsedPref" />
      </el-form-item>
    </el-form>
    <div class="pref-tip">开启后，进入系统时侧边栏默认收起，为内容区腾出更多展示空间。设置保存后立即生效，并在下次登录时保持。</div>
    <template #footer>
      <el-button @click="preferenceVisible = false">取消</el-button>
      <el-button type="primary" @click="savePreference">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { ElMessage } from 'element-plus';
import {
  User, ArrowDown, Lock, Message, Connection, SwitchButton, Setting
} from '@element-plus/icons-vue';
import { bindEmail, changePassword, fetchLoginLogs, sendBindCode, updateProfile, type AuthUser, type LoginLog } from '../api/auth';
import { formatDateTime } from '../utils/dateFormat';

const SIDEBAR_PREF_KEY = 'pref_sidebar_collapsed';

const props = defineProps<{
  user: AuthUser | null;
  compact?: boolean;
}>();

const emit = defineEmits<{
  logout: [];
  profileUpdated: [updates: { realName?: string; email?: string; phone?: string; emailVerified?: boolean }];
  preferenceChanged: [prefs: { sidebarCollapsed: boolean }];
}>();

// ---- 从后端拼装的 profile map 读取展示字段（统一空值处理）----
function profileStr(key: string): string {
  const value = props.user?.profile?.[key];
  return value == null ? '' : String(value);
}

const avatarUrl = '';  // 头像功能已移除,保留空字符串让 el-avatar 显示默认图标
const userEmail = computed(() => profileStr('email'));
const userPhone = computed(() => profileStr('phone'));
const emailVerified = computed(() => props.user?.profile?.emailVerified === true);
const studentNo = computed(() => profileStr('student_no'));
const teacherNo = computed(() => profileStr('teacher_no'));
const title = computed(() => profileStr('title'));
const classText = computed(() => {
  const name = profileStr('class_name');
  const grade = profileStr('grade');
  if (!name) return '';
  return grade ? `${name}（${grade}）` : name;
});

const roleTagType = computed(() => {
  if (props.user?.primaryRole === 'ADMIN') return 'danger';
  if (props.user?.primaryRole === 'TEACHER') return 'warning';
  return 'success';
});

// ---- 弹窗可见性 ----
const profileVisible = ref(false);
const emailVisible = ref(false);
const passwordVisible = ref(false);
const securityVisible = ref(false);
const preferenceVisible = ref(false);

// ---- 表单 ----
const profileForm = reactive({ realName: '', phone: '' });
const emailForm = reactive({ email: '', code: '' });
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' });

// ---- 登录日志 ----
const loginLogs = ref<LoginLog[]>([]);
const loginLogsLoading = ref(false);

// ---- 个人偏好（持久化在 localStorage，App 启动时读取生效）----
const sidebarCollapsedPref = ref(localStorage.getItem(SIDEBAR_PREF_KEY) === '1');

// ---- 加载态 ----
const profileSaving = ref(false);
const emailSending = ref(false);
const emailBinding = ref(false);
const emailCountdown = ref(0);
const passwordChanging = ref(false);

let emailTimer: ReturnType<typeof setInterval> | null = null;

function handleDropdownVisibleChange(visible: boolean) {
  // 每次展开都用最新 profile 回填可编辑表单，避免编辑后状态残留
  if (visible && props.user) {
    profileForm.realName = props.user.realName;
    profileForm.phone = userPhone.value;
  }
}

function handleCommand(command: string) {
  switch (command) {
    case 'profile':
      profileForm.realName = props.user?.realName || '';
      profileForm.phone = userPhone.value;
      profileVisible.value = true;
      break;
    case 'email':
      emailForm.email = '';
      emailForm.code = '';
      emailVisible.value = true;
      break;
    case 'password':
      passwordForm.oldPassword = '';
      passwordForm.newPassword = '';
      passwordForm.confirmPassword = '';
      passwordVisible.value = true;
      break;
    case 'security':
      securityVisible.value = true;
      loadLoginLogs();
      break;
    case 'preference':
      sidebarCollapsedPref.value = localStorage.getItem(SIDEBAR_PREF_KEY) === '1';
      preferenceVisible.value = true;
      break;
    case 'logout':
      emit('logout');
      break;
  }
}

function openProfileDialog() {
  handleCommand('profile');
}

function openAccountPanel(panel?: string | null) {
  if (panel === 'security') {
    handleCommand('security');
    return;
  }
  if (panel === 'password') {
    handleCommand('password');
    return;
  }
  openProfileDialog();
}

defineExpose({ openProfileDialog, openAccountPanel });

async function loadLoginLogs() {
  loginLogsLoading.value = true;
  try {
    loginLogs.value = (await fetchLoginLogs()).data || [];
  } catch {
    loginLogs.value = [];
  } finally {
    loginLogsLoading.value = false;
  }
}

function savePreference() {
  localStorage.setItem(SIDEBAR_PREF_KEY, sidebarCollapsedPref.value ? '1' : '0');
  emit('preferenceChanged', { sidebarCollapsed: sidebarCollapsedPref.value });
  preferenceVisible.value = false;
  ElMessage.success('偏好设置已保存');
}

async function saveProfile() {
  if (!profileForm.realName.trim()) {
    ElMessage.warning('请输入真实姓名');
    return;
  }
  profileSaving.value = true;
  try {
    await updateProfile(profileForm.realName.trim(), profileForm.phone.trim());
    emit('profileUpdated', {
      realName: profileForm.realName.trim(),
      phone: profileForm.phone.trim()
    });
    profileVisible.value = false;
    ElMessage.success('个人信息已更新');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '保存失败');
  } finally {
    profileSaving.value = false;
  }
}

async function sendEmailCode() {
  if (!emailForm.email) {
    ElMessage.warning('请输入邮箱地址');
    return;
  }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailForm.email)) {
    ElMessage.warning('请输入正确的邮箱地址');
    return;
  }
  emailSending.value = true;
  try {
    await sendBindCode(emailForm.email);
    ElMessage.success('验证码已发送至邮箱');
    emailCountdown.value = 60;
    emailTimer = setInterval(() => {
      emailCountdown.value--;
      if (emailCountdown.value <= 0 && emailTimer) {
        clearInterval(emailTimer);
        emailTimer = null;
      }
    }, 1000);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '发送失败');
  } finally {
    emailSending.value = false;
  }
}

async function bindEmailConfirm() {
  if (!emailForm.email || !emailForm.code) {
    ElMessage.warning('请输入邮箱和验证码');
    return;
  }
  emailBinding.value = true;
  try {
    await bindEmail(emailForm.email, emailForm.code);
    emit('profileUpdated', { email: emailForm.email, emailVerified: true });
    emailVisible.value = false;
    ElMessage.success('邮箱绑定成功');
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '绑定失败');
  } finally {
    emailBinding.value = false;
  }
}

async function changePasswordConfirm() {
  if (!passwordForm.oldPassword) {
    ElMessage.warning('请输入当前密码');
    return;
  }
  if (!passwordForm.newPassword || passwordForm.newPassword.length < 6) {
    ElMessage.warning('新密码至少需要6位');
    return;
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致');
    return;
  }
  passwordChanging.value = true;
  try {
    await changePassword(passwordForm.oldPassword, passwordForm.newPassword);
    passwordVisible.value = false;
    ElMessage.success('密码修改成功，请重新登录');
    // 密码已变更，当前会话失效，退出后用新密码登录
    setTimeout(() => emit('logout'), 1200);
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '修改密码失败');
  } finally {
    passwordChanging.value = false;
  }
}
</script>

<style scoped>
.user-profile-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 12px 4px 4px;
  border-radius: 20px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.user-profile-trigger:hover {
  background-color: rgba(0, 0, 0, 0.04);
}

.user-avatar {
  border: 2px solid #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.12);
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #333;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-profile-menu {
  min-width: 240px;
}

.profile-card {
  padding: 20px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  margin: -5px -12px 4px;
}

.profile-avatar {
  border: 3px solid rgba(255, 255, 255, 0.35);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
}

.profile-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 5px;
}

.profile-name {
  font-size: 16px;
  font-weight: 600;
}

.profile-username {
  font-size: 12px;
  opacity: 0.85;
}

.logout-item {
  color: #f56c6c;
}

.profile-readonly {
  margin-top: 4px;
}

.avatar-upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 12px 20px;
}

.preview-avatar {
  border: 3px solid #ebeef5;
}

.avatar-tip {
  font-size: 12px;
  color: #909399;
  text-align: center;
  line-height: 1.6;
}

.code-input-group {
  display: flex;
  gap: 8px;
  width: 100%;
}

.code-input-group .el-input {
  flex: 1;
}

.security-tips {
  margin-top: 18px;
}

.security-tips ul {
  margin: 8px 0 0 0;
  padding-left: 20px;
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
}

.login-logs-section {
  margin-top: 18px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.pref-tip {
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin-top: 4px;
}
</style>
