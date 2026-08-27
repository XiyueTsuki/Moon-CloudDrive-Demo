/**
 * 文件相关 API 接口封装
 * 包含文件上传、进度查询、列表、下载、删除、重命名等功能
 */
import http from './index'
import type { ApiResponse, FileInfo, UploadProgress } from '@/types/api'

/**
 * 上传文件
 * 将文件以 multipart/form-data 格式提交到后端，支持上传进度回调
 *
 * @param file       要上传的文件对象
 * @param onProgress 上传进度回调（可选），参数为 0-100 的百分比
 * @returns 返回包含任务ID的响应
 */
export function uploadFile(file: File, onProgress?: (percent: number) => void) {
  const formData = new FormData()
  formData.append('file', file)

  return http.post<ApiResponse<string>>('/api/file/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        onProgress(percent)
      }
    },
  })
}

/**
 * 查询文件上传进度
 * 前端轮询此接口获取异步上传的实时进度
 *
 * @param taskId 上传任务ID
 * @returns 包含百分比、状态、消息的进度信息
 */
export function getProgress(taskId: string) {
  return http.get<ApiResponse<UploadProgress>>('/api/file/progress', {
    params: { taskId },
  })
}

/**
 * 获取当前登录用户的文件列表
 * 返回按上传时间倒序排列的文件信息
 *
 * @returns 文件信息列表
 */
export function getFileList() {
  return http.get<ApiResponse<FileInfo[]>>('/api/file/list')
}

/**
 * 获取文件下载链接
 * 返回OSS预签名URL，有效期1小时
 *
 * @param fileId 文件ID
 * @returns 包含预签名下载URL的响应
 */
export function getDownloadUrl(fileId: number) {
  return http.get<ApiResponse<string>>('/api/file/download', {
    params: { fileId },
  })
}

/**
 * 删除文件
 * 仅允许删除自己上传的文件
 *
 * @param fileId 文件ID
 * @returns 操作结果
 */
export function deleteFile(fileId: number) {
  return http.delete<ApiResponse<null>>('/api/file/delete', {
    params: { fileId },
  })
}

/**
 * 重命名文件
 * 仅允许重命名自己上传的文件
 *
 * @param fileId  文件ID
 * @param newName 新文件名
 * @returns 操作结果
 */
export function renameFile(fileId: number, newName: string) {
  return http.put<ApiResponse<null>>('/api/file/rename', null, {
    params: { fileId, newName },
  })
}