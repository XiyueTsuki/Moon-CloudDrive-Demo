/**
 * 分享模块 - 类型定义
 * 包含分享创建、列表查询、访问验证等类型
 */

/** 创建分享请求参数 */
export interface CreateShareRequest {
  fileId: number
  password?: string
  expireHours?: number
  maxDownloads?: number
}

/** 分享信息（列表项） */
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

/** 分享文件元信息响应（访问分享页时使用） */
export interface ShareInfoResponse {
  shareCode: string
  fileName: string
  fileSize: number
  needPassword: boolean
  isFolder: boolean
  downloadUrl: string | null
}

/** 验证提取码请求参数 */
export interface VerifyCodeRequest {
  password: string
}

/** 打包任务进度信息 */
export interface PackProgressResponse {
  status: string
  percent: number
  message: string
  zipFilename: string | null
}