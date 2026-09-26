/**
 * 认证模块 - API 接口封装
 * 包含登录、注册、发送验证码、修改密码等后端接口调用
 */
import http from '@/shared/http'
import type { ApiResponse } from '@/shared/types'
import type { ChangePasswordRequest, LoginRequest, LoginResponse, RegisterRequest, SendCodeRequest } from '../types/auth'

/** 发送邮箱验证码 */
export function sendCode(data: SendCodeRequest) {
  return http.post<ApiResponse<null>>('/api/user/send-code', data)
}

/** 用户注册 */
export function register(data: RegisterRequest) {
  return http.post<ApiResponse<null>>('/api/user/register', data)
}

/** 用户登录 */
export function login(data: LoginRequest) {
  return http.post<ApiResponse<LoginResponse>>('/api/user/login', data)
}

/** 修改当前登录用户密码 */
export function changePassword(data: ChangePasswordRequest) {
  return http.post<ApiResponse<null>>('/api/user/change-password', data)
}