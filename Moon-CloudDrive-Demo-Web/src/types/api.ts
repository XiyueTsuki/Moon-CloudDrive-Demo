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
  percent: number
  status: string
  message: string
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