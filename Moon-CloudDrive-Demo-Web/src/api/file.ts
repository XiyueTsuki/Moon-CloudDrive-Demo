/**
 * 文件相关 API 接口封装
 * 包含文件上传、进度查询、列表、下载、删除、重命名等功能
 */
import http from './index'
import type { ApiResponse, FileInfo, UploadProgress, PageResult, ChunkInitRequest, ChunkInitResponse, ChunkProgressResponse, ChunkCompleteRequest, PreviewInfo, TextPreview } from '@/types/api'

/**
 * 上传文件
 * 将文件以 multipart/form-data 格式提交到后端，支持上传进度回调
 *
 * @param file       要上传的文件对象
 * @param parentId   父文件夹ID，null表示上传到根目录
 * @param onProgress 上传进度回调（可选），参数为 0-100 的百分比
 * @returns 返回包含任务ID的响应
 */
export function uploadFile(file: File, parentId?: number | null, onProgress?: (percent: number) => void) {
  const formData = new FormData()
  formData.append('file', file)
  if (parentId != null) {
    formData.append('parentId', String(parentId))
  }

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
 * 分页查询指定文件夹下的文件/文件夹列表，支持排序和搜索。
 *
 * @param parentId 父文件夹ID，null或不传则查询根目录
 * @param page      页码（从1开始，默认1）
 * @param size      每页条数（默认20）
 * @param sortBy    排序字段：name / size / uploadTime（默认 uploadTime）
 * @param sortOrder 排序方向：asc / desc（默认 desc）
 * @param keyword   按文件名搜索（可选）
 */
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

// ==================== 回收站相关 API ====================

/**
 * 获取回收站文件列表
 * 返回当前登录用户回收站中的所有文件
 */
export function getRecycleBinList() {
  return http.get<ApiResponse<FileInfo[]>>('/api/file/recycle-bin/list')
}

/**
 * 从回收站恢复文件
 * 将回收站中的文件恢复为正常状态
 *
 * @param fileId 文件ID
 */
export function restoreFile(fileId: number) {
  return http.put<ApiResponse<null>>('/api/file/recycle-bin/restore', null, {
    params: { fileId },
  })
}

/**
 * 彻底删除回收站中的文件/文件夹
 * 物理删除数据库记录并从OSS中删除实际文件，不可恢复
 *
 * @param fileId 文件/文件夹ID
 */
export function permanentDeleteFile(fileId: number) {
  return http.delete<ApiResponse<null>>('/api/file/recycle-bin/permanent-delete', {
    params: { fileId },
  })
}

// ==================== 文件夹相关 API ====================

/**
 * 创建新文件夹
 *
 * @param folderName 文件夹名称
 * @param parentId   父文件夹ID，null或不传则创建在根目录
 */
export function createFolder(folderName: string, parentId?: number | null) {
  return http.post<ApiResponse<FileInfo>>('/api/file/folder/create', null, {
    params: {
      folderName,
      ...(parentId != null ? { parentId: String(parentId) } : {}),
    },
  })
}

/**
 * 移动文件或文件夹到目标目录
 *
 * @param fileId         要移动的文件/文件夹ID
 * @param targetParentId 目标父文件夹ID，null或不传则移动到根目录
 */
export function moveFile(fileId: number, targetParentId?: number | null) {
  return http.put<ApiResponse<null>>('/api/file/folder/move', null, {
    params: {
      fileId,
      ...(targetParentId != null ? { targetParentId: String(targetParentId) } : {}),
    },
  })
}

/**
 * 获取文件夹路径（面包屑导航）
 * 返回从根目录到指定文件夹的完整路径链
 *
 * @param folderId 文件夹ID，null或不传返回空列表
 */
export function getFolderPath(folderId?: number | null) {
  return http.get<ApiResponse<FileInfo[]>>('/api/file/folder/path', {
    params: folderId != null ? { folderId } : {},
  })
}

// ==================== 分片上传（大文件）API ====================

/**
 * 初始化分片上传
 * 提交文件基本信息（名称、大小、哈希）获取分片上传参数和uploadId，
 * 如果文件哈希已存在则触发秒传，跳过实际上传流程
 *
 * @param data 包含文件名、大小、哈希、目标文件夹的请求参数
 */
export function initChunkUpload(data: ChunkInitRequest) {
  return http.post<ApiResponse<ChunkInitResponse>>('/api/file/chunk/init', data)
}

/**
 * 上传单个分片
 * 将文件分片以 multipart/form-data 格式提交，支持上传进度回调
 *
 * @param chunk      分片 Blob 数据
 * @param uploadId   上传任务标识
 * @param chunkIndex 分片序号（从0开始）
 * @param onProgress 分片上传进度回调（可选）
 */
export function uploadChunk(chunk: Blob, uploadId: string, chunkIndex: number, onProgress?: (percent: number) => void) {
  const formData = new FormData()
  formData.append('chunk', chunk)
  formData.append('uploadId', uploadId)
  formData.append('chunkIndex', String(chunkIndex))

  return http.post<ApiResponse<null>>('/api/file/chunk/upload', formData, {
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
 * 完成分片上传
 * 所有分片上传完毕后调用，通知后端合并OSS碎片并创建文件记录
 *
 * @param data 包含uploadId和文件MIME类型
 */
export function completeChunkUpload(data: ChunkCompleteRequest) {
  return http.post<ApiResponse<FileInfo>>('/api/file/chunk/complete', data)
}

/**
 * 查询分片上传进度
 * 用于断点续传时判断哪些分片已上传，客户端跳过已完成分片继续上传剩余部分
 *
 * @param uploadId 上传任务标识
 */
export function getChunkProgress(uploadId: string) {
  return http.get<ApiResponse<ChunkProgressResponse>>('/api/file/chunk/progress', {
    params: { uploadId },
  })
}

/**
 * 取消分片上传
 * 中止OSS端的碎片并清理Redis缓存中的上传信息
 *
 * @param uploadId 上传任务标识
 */
export function abortChunkUpload(uploadId: string) {
  return http.delete<ApiResponse<null>>('/api/file/chunk/abort', {
    params: { uploadId },
  })
}

// ==================== 多文件打包下载 API ====================

export interface PackProgress {
  status: string
  percent: number
  message: string
  zipFilename: string | null
}

/**
 * 提交打包下载任务
 * 接收文件ID列表，返回taskId供前端轮询进度
 *
 * @param fileIds 要打包下载的文件ID列表
 */
export function preparePackDownload(fileIds: number[]) {
  return http.post<ApiResponse<string>>('/api/file/pack/prepare', { fileIds })
}

/**
 * 查询打包进度
 * 前端轮询此接口获取打包的实时进度
 *
 * @param taskId 打包任务ID
 */
export function getPackProgress(taskId: string) {
  return http.get<ApiResponse<PackProgress>>('/api/file/pack/progress', {
    params: { taskId },
  })
}

/**
 * 触发浏览器下载打包完成的ZIP文件
 * 创建隐藏 <a> 标签携带 token 参数（Sa-Token 支持从 URL 读取 token）并触发下载
 *
 * @param taskId 打包任务ID
 */
export function downloadPackZip(taskId: string) {
  const token = localStorage.getItem('token')
  if (!token) return
  const a = document.createElement('a')
  a.href = `/api/file/pack/download?taskId=${taskId}&satoken=${token}`
  a.download = ''
  a.click()
}

// ==================== 文件在线预览 API ====================

/**
 * 获取文件预览信息
 * 根据文件扩展名返回对应的预览策略：
 * - 图片 → OSS 图片处理 URL
 * - 视频/音频/PDF → OSS 预签名 inline URL
 * - 文本/代码 → language 标识（前端再调 getTextContent 获取内容）
 * - 其他 → unsupported
 *
 * @param fileId 文件 ID
 */
export function getPreviewInfo(fileId: number) {
  return http.get<ApiResponse<PreviewInfo>>('/api/file/preview/info', {
    params: { fileId },
  })
}

/**
 * 获取文本文件内容（用于代码高亮预览）
 * 仅对文本/代码类文件开放
 *
 * @param fileId 文件 ID
 */
export function getTextContent(fileId: number) {
  return http.get<ApiResponse<TextPreview>>('/api/file/preview/text', {
    params: { fileId },
  })
}

// ==================== PDF 服务端转图片预览 API ====================

/** PDF 转为图片后的预览信息 */
export interface PdfPreview {
  totalPages: number
  status: 'converting' | 'ready' | 'failed'
  pageUrls: string[]
  errorMessage?: string
}

/**
 * 获取 PDF 预览信息（服务端转图片模式）
 * 返回总页数、转换状态和每页图片 URL。
 * 首次访问返回 status=converting，前端轮询直到 status=ready。
 *
 * @param fileId 文件 ID
 */
export function getPdfPreview(fileId: number) {
  return http.get<ApiResponse<PdfPreview>>('/api/file/preview/pdf', {
    params: { fileId },
  })
}

/**
 * 获取 PDF 单页图片流地址
 * 返回带认证 token 的图片流 URL，浏览器可直接作为 img src 使用。
 * 服务端设置了 24 小时 Cache-Control，浏览器会自动缓存。
 *
 * @param fileId  文件 ID
 * @param pageNum 页码（从 1 开始）
 */
export function getPdfPageUrl(fileId: number, pageNum: number): string {
  const token = localStorage.getItem('token')
  return `/api/file/preview/pdf/page/${pageNum}?fileId=${fileId}&satoken=${token}`
}