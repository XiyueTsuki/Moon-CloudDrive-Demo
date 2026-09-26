/**
 * 跨模块共享的通用类型定义
 */

/** 后端统一响应体 */
export interface ApiResponse<T = unknown> {
  code: number
  data: T
  msg: string
}

/** 分页查询结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** 批量操作结果 */
export interface BatchOperationResult {
  successCount: number
  failCount: number
  failReasons: string[]
}