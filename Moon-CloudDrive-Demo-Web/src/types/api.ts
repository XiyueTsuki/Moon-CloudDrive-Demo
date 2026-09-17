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

/** 修改密码请求参数 */
export interface ChangePasswordRequest {
  /** 用户当前密码 */
  oldPassword: string
  /** 用户新密码 */
  newPassword: string
}

/** 文件信息，用于文件列表展示 */
export interface FileInfo {
  /** 文件记录ID */
  id: number
  /** 原始文件名 */
  originalFilename: string
  /** 文件大小（字节） */
  fileSize: number
  /** 文件MIME类型 */
  contentType: string
  /** 文件SHA-256哈希值 */
  fileHash: string
  /** 上传时间 */
  uploadTime: string
  /** 软删除标记：0-正常，1-已删除（回收站中） */
  deleted: number
  /** 进入回收站的时间 */
  deleteTime: string
  /** 父文件夹ID，null表示根目录 */
  parentId: number | null
  /** 是否为文件夹：0-文件，1-文件夹 */
  isFolder: number
}

export interface UploadProgress {
  /** 进度百分比（0-100） */
  percent: number
  /** 状态：uploading / done / failed */
  status: string
  /** 状态描述信息 */
  message: string
}

/** 分片上传初始化请求参数 */
export interface ChunkInitRequest {
  /** 原始文件名 */
  fileName: string
  /** 文件总大小（字节） */
  fileSize: number
  /** 文件SHA-256哈希值，用于秒传去重 */
  fileHash: string
  /** 目标文件夹ID，null表示根目录 */
  parentId: number | null
  /** 文件MIME类型，如 image/png */
  contentType: string
}

/** 分片上传初始化响应 */
export interface ChunkInitResponse {
  /** 本次上传任务的唯一标识 */
  uploadId: string
  /** 总的分片数量 */
  chunkCount: number
  /** 每个分片的大小（字节） */
  chunkSize: number
  /** 是否秒传成功（文件已存在，无需上传） */
  instantComplete: boolean
  /** 秒传成功时的文件信息 */
  file: FileInfo | null
}

/** 分片上传进度响应，用于断点续传时判断哪些分片已上传 */
export interface ChunkProgressResponse {
  /** 本次上传任务的唯一标识 */
  uploadId: string
  /** 总的分片数量 */
  chunkCount: number
  /** 已完成的分片数量 */
  completedCount: number
  /** 已完成的分片序号集合（从1开始） */
  completedParts: number[]
  /** 完成百分比（0-100） */
  percent: number
}

/** 完成分片上传请求参数 */
export interface ChunkCompleteRequest {
  /** 上传任务唯一标识 */
  uploadId: string
  /** 文件MIME类型 */
  contentType: string
}

/** 上传任务状态 */
export type UploadTaskStatus =
  | 'pending'
  | 'hashing'
  | 'initializing'
  | 'uploading'
  | 'paused'
  | 'completing'
  | 'done'
  | 'failed'
  | 'cancelled'

/**
 * 上传任务
 * 由上传任务管理器统一管理，支持排队、暂停、续传、取消
 */
export interface UploadTask {
  /** 本地任务唯一标识（UUID） */
  id: string
  /** 服务端上传任务标识（init 后获取） */
  uploadId?: string
  /** 原始文件名 */
  fileName: string
  /** 文件总大小（字节） */
  fileSize: number
  /** 文件SHA-256哈希（init 后计算） */
  fileHash?: string
  /** 目标文件夹ID，null 表示根目录 */
  parentId: number | null
  /** 原始 File 对象引用 */
  file: File
  /** 当前状态 */
  status: UploadTaskStatus
  /** 上传进度 0-100 */
  progress: number
  /** 状态描述文字 */
  message: string
  /** 已上传字节数 */
  uploadedBytes: number
  /** 总分片数 */
  totalChunks: number
  /** 已完成分片数 */
  completedChunks: number
  /** 失败原因 */
  errorMessage?: string
  /** 任务创建时间戳 */
  createdAt: number
  /** 任务完成时间戳 */
  completedAt?: number
  /** AbortController，用于暂停时中止当前批次的网络请求 */
  abortController?: AbortController
  /** 上传完成的文件信息 */
  result?: FileInfo
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

// ==================== 文件预览相关类型 ====================

/**
 * 文件预览信息响应
 * 前端根据 previewType 选择不同的渲染组件：
 * - image → img 标签
 * - video → video 播放器
 * - audio → audio 播放器
 * - pdf   → iframe（pdf.js 或浏览器原生渲染）
 * - text  → highlight.js 代码高亮
 * - unsupported → 提示下载
 */
export interface PreviewInfo {
  /** 预览类型: image / video / audio / pdf / text / unsupported */
  previewType: string
  /** 预览 URL（图片/视频/音频/PDF 时有效，text 时为 null） */
  previewUrl: string | null
  /** 原始文件名 */
  fileName: string
  /** MIME 类型，用于浏览器 Content-Type 协商 */
  mimeType: string
  /** 文件大小（字节） */
  fileSize: number
  /** 文本文件语言标识（仅预览text类型时有效），如 "java", "json", "md" */
  language: string | null
}

/**
 * 文本文件预览内容响应
 * 后端读取文本文件内容并返回，前端使用 highlight.js 按 language 标识进行代码高亮渲染
 */
export interface TextPreview {
  /** 文本文件完整内容（UTF-8 解码后） */
  content: string
  /** 语言标识，供 highlight.js 使用，如 "java", "json", "xml", "python" */
  language: string
  /** 字符编码，固定为 "UTF-8" */
  encoding: string
}

/** PDF 服务端转图片预览信息 */
export interface PdfPreviewInfo {
  /** PDF 总页数 */
  totalPages: number
  /** 转换状态: converting / ready / failed */
  status: 'converting' | 'ready' | 'failed'
  /** 每页图片的 OSS 预签名 URL 列表 */
  pageUrls: string[]
  /** 错误信息（status=failed 时有效） */
  errorMessage?: string
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

/** 分页查询结果 */
export interface PageResult<T> {
  /** 当前页数据列表 */
  records: T[]
  /** 总记录数 */
  total: number
  /** 当前页码 */
  page: number
  /** 每页条数 */
  size: number
}