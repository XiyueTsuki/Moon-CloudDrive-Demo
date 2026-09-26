/**
 * 打包下载模块 - API 接口封装
 */
import http from '@/shared/http'
import type { ApiResponse } from '@/shared/types'
import type { PackProgress } from '../types/packDownload'

/** 提交打包下载任务 */
export function preparePackDownload(fileIds: number[]) {
  return http.post<ApiResponse<string>>('/api/file/pack/prepare', { fileIds })
}

/** 查询打包进度 */
export function getPackProgress(taskId: string) {
  return http.get<ApiResponse<PackProgress>>('/api/file/pack/progress', {
    params: { taskId },
  })
}

/** 触发浏览器下载打包完成的 ZIP 文件 */
export function downloadPackZip(taskId: string) {
  const token = localStorage.getItem('token')
  if (!token) return
  const a = document.createElement('a')
  a.href = `/api/file/pack/download?taskId=${taskId}&satoken=${token}`
  a.download = ''
  a.click()
}