export interface ApiResponse<T = unknown> {
  code: number
  data: T
  msg: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  token: string
  username: string
  email: string
}

export interface RegisterRequest {
  username: string
  password: string
  email: string
  code: string
}

export interface SendCodeRequest {
  email: string
}

export interface UploadProgress {
  percent: number
  status: string
  message: string
}

export interface CreateShareRequest {
  fileId: number
  password?: string
  expireHours?: number
  maxDownloads?: number
}

export interface ShareInfo {
  id: number
  shareCode: string
  fileId: number
  userId: number
  password: string | null
  expireTime: string
  maxDownloads: number
  downloadCount: number
  status: number
  createTime: string
}

export interface ShareInfoResponse {
  shareCode: string
  fileName: string
  fileSize: number
  needPassword: boolean
  downloadUrl: string | null
}

export interface VerifyCodeRequest {
  password: string
}