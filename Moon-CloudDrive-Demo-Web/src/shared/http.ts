/**
 * HTTP 请求客户端
 * 基于 axios 封装，统一处理 token 注入、错误拦截、401 自动跳转
 */
import axios from 'axios'
import type { ApiResponse } from './types'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

/** 请求拦截器：自动附加 Sa-Token 认证头 */
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers['satoken'] = token
  }
  return config
})

/** 响应拦截器：统一处理业务错误和 401 登录态失效 */
http.interceptors.response.use(
  (response) => {
    const data = response.data as ApiResponse
    if (data.code !== 200) {
      ElMessage.error(data.msg || '请求失败')
      return Promise.reject(new Error(data.msg || '请求失败'))
    }
    return response
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      localStorage.removeItem('email')
      window.location.href = '/login'
    }
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  },
)

export default http