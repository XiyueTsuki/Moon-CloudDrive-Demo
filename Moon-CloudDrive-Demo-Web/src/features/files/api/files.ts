/**
 * 文件管理模块 - API 接口封装
 * 包含文件列表、下载、删除、重命名、新建文件夹、移动等接口
 */
import http from '@/shared/http'
import type { ApiResponse } from '@/shared/types'
import type { FileInfo, BatchOperationResult, PageResult } from '../types/files'

/** 分页查询指定文件夹下的文件/文件夹列表，支持排序和搜索 */
export function getFileList(params: {
  parentId?: number | null
  page?: number
  size?: number
  sortBy?: string
  sortOrder?: string
  keyword?: string
}) {
  return http.get<ApiResponse<PageResult<FileInfo>>>('/api/file/list', { params })
}

/** 获取文件下载链接 */
export function getDownloadUrl(fileId: number) {
  return http.get<ApiResponse<string>>('/api/file/download', {
    params: { fileId },
  })
}

/** 删除文件/文件夹（移入回收站） */
export function deleteFile(fileId: number) {
  return http.delete<ApiResponse<null>>('/api/file/delete', {
    params: { fileId },
  })
}

/** 重命名文件 */
export function renameFile(fileId: number, newName: string) {
  return http.put<ApiResponse<null>>('/api/file/rename', null, {
    params: { fileId, newName },
  })
}

/** 创建新文件夹 */
export function createFolder(folderName: string, parentId?: number | null) {
  return http.post<ApiResponse<FileInfo>>('/api/file/folder/create', null, {
    params: {
      folderName,
      ...(parentId != null ? { parentId: String(parentId) } : {}),
    },
  })
}

/** 移动文件或文件夹到目标目录 */
export function moveFile(fileId: number, targetParentId?: number | null) {
  return http.put<ApiResponse<null>>('/api/file/folder/move', null, {
    params: {
      fileId,
      ...(targetParentId != null ? { targetParentId: String(targetParentId) } : {}),
    },
  })
}

/** 获取文件夹路径（面包屑导航） */
export function getFolderPath(folderId?: number | null) {
  return http.get<ApiResponse<FileInfo[]>>('/api/file/folder/path', {
    params: folderId != null ? { folderId } : {},
  })
}

/** 批量删除文件/文件夹（移入回收站） */
export function batchDelete(fileIds: number[]) {
  return http.post<ApiResponse<BatchOperationResult>>('/api/file/batch/delete', { fileIds })
}

/** 批量移动文件/文件夹到目标目录 */
export function batchMove(fileIds: number[], targetParentId?: number | null) {
  return http.post<ApiResponse<BatchOperationResult>>('/api/file/batch/move', {
    fileIds,
    ...(targetParentId != null ? { targetParentId } : {}),
  })
}

/** 批量重命名 */
export function batchRename(fileIds: number[], mode: string, value: string) {
  return http.post<ApiResponse<BatchOperationResult>>('/api/file/batch/rename', {
    fileIds,
    mode,
    value,
  })
}