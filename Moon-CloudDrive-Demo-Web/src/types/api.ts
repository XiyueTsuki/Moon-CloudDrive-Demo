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