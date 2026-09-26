/**
 * 上传任务快照持久化工具
 * 将未完成的上传任务序列化到 localStorage，页面刷新后恢复
 */
import type { UploadTask, UploadTaskStatus } from '../types/upload'

/** 可持久化的任务快照（去除不可序列化的属性） */
interface TaskSnapshot {
  id: string
  uploadId?: string
  fileName: string
  fileSize: number
  fileHash?: string
  parentId: number | null
  status: UploadTaskStatus
  progress: number
  message: string
  uploadedBytes: number
  totalChunks: number
  completedChunks: number
  errorMessage?: string
  createdAt: number
  completedAt?: number
}

const STORAGE_KEY = 'upload_task_snapshots'

/**
 * 将任务列表持久化到 localStorage
 * 过滤掉 File 对象等不可序列化属性
 */
export function persistSnapshot(tasks: UploadTask[]): void {
  const snapshots: TaskSnapshot[] = tasks
    .filter(
      (t) =>
        ['pending', 'hashing', 'initializing', 'uploading', 'paused', 'completing'].includes(
          t.status,
        ),
    )
    .map((t) => ({
      id: t.id,
      uploadId: t.uploadId,
      fileName: t.fileName,
      fileSize: t.fileSize,
      fileHash: t.fileHash,
      parentId: t.parentId,
      status: t.status,
      progress: t.progress,
      message: t.message,
      uploadedBytes: t.uploadedBytes,
      totalChunks: t.totalChunks,
      completedChunks: t.completedChunks,
      errorMessage: t.errorMessage,
      createdAt: t.createdAt,
      completedAt: t.completedAt,
    }))
  localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshots))
}

/** 清除持久化快照 */
export function clearSnapshot(): void {
  localStorage.removeItem(STORAGE_KEY)
}

/** 从 localStorage 恢复任务快照 */
export function restoreSnapshots(): TaskSnapshot[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? JSON.parse(raw) : []
  } catch {
    return []
  }
}

export type { TaskSnapshot }