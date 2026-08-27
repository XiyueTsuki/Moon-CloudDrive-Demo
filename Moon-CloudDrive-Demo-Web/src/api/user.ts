import http from './index'
import type { ApiResponse, LoginRequest, LoginResponse, RegisterRequest, SendCodeRequest } from '@/types/api'

export function sendCode(data: SendCodeRequest) {
  return http.post<ApiResponse<null>>('/api/user/send-code', data)
}

export function register(data: RegisterRequest) {
  return http.post<ApiResponse<null>>('/api/user/register', data)
}

export function login(data: LoginRequest) {
  return http.post<ApiResponse<LoginResponse>>('/api/user/login', data)
}