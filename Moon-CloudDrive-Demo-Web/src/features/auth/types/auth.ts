/**
 * 认证模块 - 类型定义
 * 包含登录、注册、修改密码等请求/响应类型
 */

/** 登录请求参数 */
export interface LoginRequest {
  email: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  token: string
  username: string
  email: string
}

/** 注册请求参数 */
export interface RegisterRequest {
  username: string
  password: string
  email: string
  code: string
}

/** 发送邮箱验证码请求参数 */
export interface SendCodeRequest {
  email: string
}

/** 修改密码请求参数 */
export interface ChangePasswordRequest {
  /** 用户当前密码 */
  oldPassword: string
  /** 用户新密码 */
  newPassword: string
}