import { defineStore } from 'pinia'
import { ref } from 'vue'
import { login as loginApi, register as registerApi, sendCode as sendCodeApi } from '@/api/user'
import type { LoginRequest, RegisterRequest, SendCodeRequest } from '@/types/api'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem('token') || '')
  const username = ref(localStorage.getItem('username') || '')
  const email = ref(localStorage.getItem('email') || '')

  const isLoggedIn = ref(!!token.value)

  async function sendCode(data: SendCodeRequest) {
    await sendCodeApi(data)
  }

  async function register(data: RegisterRequest) {
    await registerApi(data)
  }

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