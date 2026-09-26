/**
 * 认证模块 - 用户状态管理 Store
 * 管理登录态 token、用户基本信息、登录/注册/退出操作
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, register as registerApi, sendCode as sendCodeApi } from '../api/auth'
import type { LoginRequest, RegisterRequest, SendCodeRequest } from '../types/auth'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const email = ref(localStorage.getItem('email') || '')
  const isLoggedIn = ref(!!token.value)

  /** 发送邮箱验证码 */
  async function sendCode(data: SendCodeRequest) {
    await sendCodeApi(data)
  }

  /** 用户注册 */
  async function register(data: RegisterRequest) {
    await registerApi(data)
  }

  /** 用户登录，成功后持久化 token 到 localStorage */
  async function login(data: LoginRequest) {
    const res = await loginApi(data)
    const { token: t, username: u, email: e } = res.data.data

    token.value = t
    username.value = u
    email.value = e
    isLoggedIn.value = true

    localStorage.setItem('token', t)
    localStorage.setItem('username', u)
    localStorage.setItem('email', e)
  }

  /** 退出登录，清除本地 token 和用户信息 */
  function logout() {
    token.value = ''
    username.value = ''
    email.value = ''
    isLoggedIn.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('username')
    localStorage.removeItem('email')
  }

  return {
    token,
    username,
    email,
    isLoggedIn,
    sendCode,
    register,
    login,
    logout,
  }
})