/**
 * 上传模块 - API 接口封装
 * 包含文件上传、分片上传、进度查询等后端接口调用
 */
import http from '@/shared/http'
import type { AxiosProgressEvent } from 'axios'
import type { ApiResponse } from '@/shared/types'
import type {
  ChunkCompleteRequest,
  ChunkInitRequest,
  ChunkInitResponse,
  ChunkProgressResponse,
  UploadProgress,
} from '../types/upload'

/** 常规小文件直传 */
export function uploadFile(file: File, parentId?: number | null, onProgress?: (percent: number) => void) {
  const formData = new FormData()
  formData.append('file', file)
  if (parentId != null) {
    formData.append('parentId', String(parentId))
  }
  return http.post<ApiResponse<string>>('/api/file/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 0,
    onUploadProgress: (progressEvent) => {
      if (onProgress && progressEvent.total) {
        const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
        onProgress(percent)
      }
    },
  })
}

/** 查询上传进度 */
export function getProgress(taskId: string) {
  return http.get<ApiResponse<UploadProgress>>('/api/file/progress', {
    params: { taskId },
  })
}

/** 初始化分片上传 */
export function initChunkUpload(data: ChunkInitRequest) {
  return http.post<ApiResponse<ChunkInitResponse>>('/api/file/chunk/init', data)
}

/** 上传单个分片 */
export function uploadChunk(
  formData: FormData,
  callbacks?: { onProgress?: (e: AxiosProgressEvent) => void; signal?: AbortSignal },
) {
  return http.post<ApiResponse<null>>('/api/file/chunk/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 0,
    signal: callbacks?.signal,
    onUploadProgress: callbacks?.onProgress,
  })
}

/** 查询分片上传进度（断点续传用） */
export function getChunkProgress(uploadId: string) {
  return http.get<ApiResponse<ChunkProgressResponse>>('/api/file/chunk/progress', {
    params: { uploadId },
  })
}

/** 通知服务端合并分片 */
export function completeChunkUpload(data: ChunkCompleteRequest) {
  return http.post<ApiResponse<import('../types/upload').FileInfo>>('/api/file/chunk/complete', data)
}

/** 取消分片上传 */
export function abortChunkUpload(uploadId: string) {
  return http.delete<ApiResponse<null>>('/api/file/chunk/abort', {
    params: { uploadId },
  })
}