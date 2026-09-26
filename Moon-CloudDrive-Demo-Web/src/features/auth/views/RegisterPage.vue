<script setup lang="ts">
/**
 * 注册页面
 * 使用 useRegister composable 管理状态和逻辑
 */
import { useRegister } from '../composables/useRegister'

const {
  registerFormRef,
  loading,
  sendingCode,
  countdown,
  registerForm,
  rules,
  handleSendCode,
  handleRegister,
} = useRegister()
void registerFormRef
</script>

<template>
  <div class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <h1>Moon 云盘</h1>
        <p>创建新账号</p>
      </div>

      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="rules"
        label-position="top"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="邮箱" prop="email">
          <el-input v-model="registerForm.email" placeholder="请输入邮箱" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="请输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            show-password
          />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="code-row">
            <el-input v-model="registerForm.code" placeholder="请输入验证码" class="code-input" />
            <el-button
              :disabled="countdown > 0"
              :loading="sendingCode"
              class="code-btn"
              @click="handleSendCode"
            >
              {{ countdown > 0 ? `${countdown}秒后重试` : '发送验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="loading" class="auth-btn" @click="handleRegister">
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        已有账号？
        <router-link to="/login">立即登录</router-link>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.auth-card {
  width: 420px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.auth-header h1 {
  font-size: 28px;
  color: #303133;
  margin: 0 0 8px 0;
}

.auth-header p {
  color: #909399;
  margin: 0;
  font-size: 14px;
}

.auth-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
}

.auth-footer {
  text-align: center;
  color: #909399;
  font-size: 14px;
}

.auth-footer a {
  color: #409eff;
  text-decoration: none;
  margin-left: 4px;
}

.code-row {
  display: flex;
  gap: 8px;
}

.code-input {
  flex: 1;
}

.code-btn {
  flex-shrink: 0;
}
</style>