<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { changePassword } from '@/api/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)

/** 退出登录 */
function handleLogout() {
  userStore.logout()
  router.push('/login')
}

/** 修改密码相关 */
const passwordDialogVisible = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const passwordLoading = ref(false)

function openChangePasswordDialog() {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  passwordDialogVisible.value = true
}

async function submitChangePassword() {
  if (!oldPassword.value) { ElMessage.warning('请输入旧密码'); return }
  if (!newPassword.value) { ElMessage.warning('请输入新密码'); return }
  if (newPassword.value.length < 6) { ElMessage.warning('新密码长度不能少于6位'); return }
  if (newPassword.value !== confirmPassword.value) { ElMessage.warning('两次输入的新密码不一致'); return }

  passwordLoading.value = true
  try {
    await changePassword({ oldPassword: oldPassword.value, newPassword: newPassword.value })
    ElMessage.success('密码修改成功，请使用新密码重新登录')
    passwordDialogVisible.value = false
    userStore.logout()
    router.push('/login')
  } catch {
    /* 统一拦截处理 */
  } finally {
    passwordLoading.value = false
  }
}
</script>

<template>
  <div class="layout-container">
    <!-- 顶部导航栏 -->
    <header class="layout-header">
      <div class="header-brand" @click="router.push('/')">
        <h1>Moon-CloudDrive云盘</h1>
      </div>

      <el-menu
        :default-active="activeMenu"
        mode="horizontal"
        :ellipsis="false"
        router
        class="header-nav"
      >
        <el-menu-item index="/">我的文件</el-menu-item>
        <el-menu-item index="/shares">分享链接</el-menu-item>
        <el-menu-item index="/recycle-bin">回收站</el-menu-item>
      </el-menu>

      <div class="header-user">
        <el-dropdown trigger="click">
          <span class="user-info">
            <el-avatar :size="32" icon="UserFilled" />
            <span class="username">{{ userStore.username }}</span>
            <el-icon class="arrow-icon"><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>
                <el-icon><Message /></el-icon>
                <span>{{ userStore.email }}</span>
              </el-dropdown-item>
              <el-dropdown-item @click="openChangePasswordDialog">
                <el-icon><Lock /></el-icon>
                <span>修改密码</span>
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <span style="color: #f56c6c">退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 子页面内容 -->
    <main class="layout-main">
      <router-view />
    </main>

    <!-- 修改密码对话框 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="420px" :close-on-click-modal="false">
      <el-form label-width="80px" label-position="left">
        <el-form-item label="旧密码">
          <el-input v-model="oldPassword" type="password" placeholder="请输入旧密码" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" placeholder="请输入新密码（至少6位）" show-password />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="confirmPassword" type="password" placeholder="请再次输入新密码" show-password @keyup.enter="submitChangePassword" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="passwordLoading" @click="submitChangePassword">确认修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.layout-container {
  min-height: 100vh;
  background: #f5f7fa;
}

/* ==================== 顶部导航栏 ==================== */
.layout-header {
  display: flex;
  align-items: center;
  height: 60px;
  padding: 0 24px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-brand {
  cursor: pointer;
  flex-shrink: 0;
  margin-right: 32px;
}

.header-brand h1 {
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  margin: 0;
  white-space: nowrap;
}

.header-nav {
  flex: 1;
  border-bottom: none !important;
}

.header-nav .el-menu-item {
  height: 60px;
  line-height: 60px;
  border-bottom: 2px solid transparent;
}

.header-nav .el-menu-item.is-active {
  border-bottom-color: #409eff;
  color: #409eff;
}

.header-user {
  flex-shrink: 0;
  margin-left: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.user-info:hover {
  background: #f5f7fa;
}

.username {
  font-size: 14px;
  color: #303133;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow-icon {
  font-size: 12px;
  color: #909399;
}

/* ==================== 主内容区域 ==================== */
.layout-main {
  /* 子页面自行控制内边距 */
}
</style>