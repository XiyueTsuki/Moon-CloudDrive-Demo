import http from './index'
import type { ApiResponse, UploadProgress } from '@/types/api'

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

export function getProgress(taskId: string) {
  return http.get<ApiResponse<UploadProgress>>('/api/file/progress', {
    params: { taskId },
  })
}