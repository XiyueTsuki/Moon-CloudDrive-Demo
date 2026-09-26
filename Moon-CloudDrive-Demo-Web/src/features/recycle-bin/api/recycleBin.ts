/**
 * 回收站模块 - API 接口封装
 */
import http from '@/shared/http'
import type { ApiResponse } from '@/shared/types'
import type { FileInfo } from '@/features/files/types/files'

/** 获取回收站文件列表 */
export function getRecycleBinList() {
  return http.get<ApiResponse<FileInfo[]>>('/api/file/recycle-bin/list')
}

/** 从回收站恢复文件 */
export function restoreFile(fileId: number) {
  return http.put<ApiResponse<null>>('/api/file/recycle-bin/restore', null, {
    params: { fileId },
  })
}

/** 彻底删除回收站中的文件/文件夹 */
export function permanentDeleteFile(fileId: number) {
  return http.delete<ApiResponse<null>>('/api/file/recycle-bin/permanent-delete', {
    params: { fileId },
  })
}