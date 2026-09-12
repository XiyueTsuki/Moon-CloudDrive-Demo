import http from './index'
import type { ApiResponse, ChangePasswordRequest, LoginRequest, LoginResponse, RegisterRequest, SendCodeRequest } from '@/types/api'

export function sendCode(data: SendCodeRequest) {
  return http.post<ApiResponse<null>>('/api/user/send-code', data)
}

export function register(data: RegisterRequest) {
  return http.post<ApiResponse<null>>('/api/user/register', data)
}

export function login(data: LoginRequest) {
  return http.post<ApiResponse<LoginResponse>>('/api/user/login', data)
}

/**
 * 修改当前登录用户的密码
 * @param data 包含旧密码和新密码的请求体
 */
export function changePassword(data: ChangePasswordRequest) {
  return http.post<ApiResponse<null>>('/api/user/change-password', data)
}