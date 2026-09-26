/**
 * 文件管理模块 - 类型定义
 * 包含文件/文件夹基本信息和分页相关类型
 */
import type { PageResult } from '@/shared/types'

/** 文件/文件夹信息 */
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

/** 批量操作结果 */
export interface BatchOperationResult {
  successCount: number
  failCount: number
  failReasons: string[]
}

export type { PageResult }