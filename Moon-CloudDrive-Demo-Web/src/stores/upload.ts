/**
 * 上传任务管理 Store
 *
 * 核心职责：
 * - 管理所有上传任务的生命周期（创建、排队、执行、暂停、续传、取消）
 * - 全局并发控制（最多 maxConcurrent 个任务同时上传）
 * - 任务队列自动调度（一个完成 → 自动启动下一个）
 * - localStorage 持久化（页面刷新后可恢复未完成任务）
 *
 * 任务状态流转：
 *   pending → hashing → initializing → uploading ⇄ paused
 *                                          ↓
 *                                      completing
 *                                          ↓
 *                                         done
 *
 *   任意阶段出错 → failed
 *   任意阶段取消 → cancelled
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  chunkUpload,
  resumeChunkUpload,
  shouldUseChunkUpload,
  abortChunkUpload,
  ABORT_ERROR_NAME,
} from '@/utils/chunkUpload'
import type { UploadTask, UploadTaskStatus, FileInfo } from '@/types/api'

/** 全局最大并发上传数 */
const MAX_CONCURRENT = 2
/** localStorage 键名 */
const STORAGE_KEY = 'upload_tasks_snapshot'

/** 可序列化的任务快照（不包含 File 对象） */
interface TaskSnapshot {
  id: string
  uploadId?: string
  fileName: string
  fileSize: number
  fileHash?: string
  parentId: number | null
  status: UploadTaskStatus
  progress: number
  totalChunks: number
  completedChunks: number
  createdAt: number
}

export const useUploadStore = defineStore('upload', () => {
  const tasks = ref<UploadTask[]>([])

  // ==================== 计算属性 ====================

  /** 正在执行中的任务数 */
  const activeCount = computed(() =>
    tasks.value.filter(t =>
      ['hashing', 'initializing', 'uploading', 'completing'].includes(t.status)
    ).length
  )

  /** 所有任务数 */
  const totalCount = computed(() => tasks.value.length)

  /** 是否有进行中的任务 */
  const hasActiveTasks = computed(() => activeCount.value > 0)

  // ==================== 内部方法 ====================

  /** 查找任务 */
  function findTask(taskId: string): UploadTask | undefined {
    return tasks.value.find(t => t.id === taskId)
  }

  /** 更新任务字段 */
  function updateTask(taskId: string, updates: Partial<UploadTask>): void {
    const task = findTask(taskId)
    if (task) Object.assign(task, updates)
  }

  /** 保存可恢复的任务快照到 localStorage */
  function persistSnapshot(): void {
    const snapshots: TaskSnapshot[] = tasks.value
      .filter(t =>
        ['pending', 'hashing', 'initializing', 'uploading', 'paused'].includes(t.status)
        && t.uploadId
      )
      .map(t => ({
        id: t.id,
        uploadId: t.uploadId,
        fileName: t.fileName,
        fileSize: t.fileSize,
        fileHash: t.fileHash,
        parentId: t.parentId,
        status: t.status,
        progress: t.progress,
        totalChunks: t.totalChunks,
        completedChunks: t.completedChunks,
        createdAt: t.createdAt,
      }))
    localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshots))
  }

  /** 清除 localStorage 中的任务快照 */
  function clearSnapshot(): void {
    localStorage.removeItem(STORAGE_KEY)
  }

  /** 判断任务是否处于活跃状态 */
  function isActive(status: UploadTaskStatus): boolean {
    return ['hashing', 'initializing', 'uploading', 'completing'].includes(status)
  }

  // ==================== 队列调度 ====================

  /**
   * 调度下一个待上传任务
   * 每次任务状态变更时调用，确保始终有最多 MAX_CONCURRENT 个任务在并发执行
   */
  function scheduleNext(): void {
    if (activeCount.value >= MAX_CONCURRENT) return
    const next = tasks.value.find(t => t.status === 'pending')
    if (next) startTask(next)
  }

  /**
   * 启动单个上传任务
   * 根据文件大小自动选择分片上传或普通上传
   */
  async function startTask(task: UploadTask): Promise<void> {
    const taskId = task.id
    const file = task.file
    const parentId = task.parentId
    const abortController = new AbortController()

    updateTask(taskId, { abortController })

    if (shouldUseChunkUpload(file)) {
      await startChunkTask(task, abortController.signal)
    } else {
      await startSimpleTask(taskId, file, parentId)
    }
  }

  /** 启动分片上传任务 */
  async function startChunkTask(task: UploadTask, signal: AbortSignal): Promise<void> {
    const taskId = task.id

    try {
      const result = await chunkUpload(
        task.file,
        task.parentId,
        {
          onProgress(percent, message) {
            updateTask(taskId, {
              progress: percent,
              message,
              completedChunks: Math.round((percent - 10) * task.totalChunks / 85) || 0,
            })
          },
          onStatusChange(status) {
            const statusMap: Record<string, UploadTaskStatus> = {
              hashing: 'hashing',
              initializing: 'initializing',
              uploading: 'uploading',
              completing: 'completing',
              done: 'done',
              failed: 'failed',
            }
            updateTask(taskId, { status: statusMap[status] || task.status, message: '' })
            persistSnapshot()
          },
        },
        signal,
        (initResult) => {
          // init 完成后保存元数据，用于断点续传
          updateTask(taskId, {
            uploadId: initResult.uploadId,
            fileHash: initResult.fileHash,
            totalChunks: initResult.chunkCount,
          })
          persistSnapshot()
        },
      )

      // 上传成功
      updateTask(taskId, {
        status: 'done',
        progress: 100,
        message: '上传完成',
        completedAt: Date.now(),
        result,
      })
      persistSnapshot()
    } catch (e) {
      const err = e as Error
      if (err.name === ABORT_ERROR_NAME) {
        // 主动暂停，不视为错误
        updateTask(taskId, {
          status: 'paused',
          message: '已暂停',
        })
        persistSnapshot()
        return
      }
      // 真正的错误
      updateTask(taskId, {
        status: 'failed',
        message: err.message || '上传失败',
        errorMessage: err.message,
      })
    } finally {
      // 无论成功/失败/暂停，都调度下一个
      scheduleNext()
    }
  }

  /** 启动普通上传任务（小文件） */
  async function startSimpleTask(
    taskId: string,
    file: File,
    parentId: number | null,
  ): Promise<void> {
    // 小文件暂用简单轮询方式（保留原有逻辑）
    updateTask(taskId, { status: 'uploading', message: '正在上传...', progress: 0 })

    try {
      const { uploadFile, getProgress } = await import('@/api/file')
      const res = await uploadFile(file, parentId)
      const serverTaskId = res.data.data

      // 轮询进度
      await new Promise<void>((resolve, reject) => {
        const timer = setInterval(async () => {
          try {
            const progressRes = await getProgress(serverTaskId)
            const p = progressRes.data.data
            updateTask(taskId, { progress: p.percent, message: p.message })
            if (p.status === 'done') {
              clearInterval(timer)
              resolve()
            } else if (p.status === 'failed') {
              clearInterval(timer)
              reject(new Error(p.message || '上传失败'))
            }
          } catch {
            clearInterval(timer)
            reject(new Error('查询进度失败'))
          }
        }, 1000)
      })

      updateTask(taskId, {
        status: 'done',
        progress: 100,
        message: '上传完成',
        completedAt: Date.now(),
      })
    } catch (e) {
      const err = e as Error
      updateTask(taskId, {
        status: 'failed',
        message: err.message || '上传失败',
        errorMessage: err.message,
      })
    } finally {
      scheduleNext()
    }
  }

  // ==================== 公共 API ====================

  /**
   * 添加文件到上传队列
   * 创建任务 → 加入队列 → 自动调度
   *
   * @param file     要上传的文件
   * @param parentId 目标文件夹ID，null 表示根目录
   * @returns 任务ID
   */
  function addTask(file: File, parentId: number | null): string {
    const id = crypto.randomUUID()
    const task: UploadTask = {
      id,
      fileName: file.name,
      fileSize: file.size,
      parentId,
      file,
      status: 'pending',
      progress: 0,
      message: '排队中...',
      uploadedBytes: 0,
      totalChunks: 0,
      completedChunks: 0,
      createdAt: Date.now(),
    }
    tasks.value.push(task)
    scheduleNext()
    return id
  }

  /**
   * 暂停指定任务
   * 仅对 uploading/hashing/initializing 状态的任务有效
   *
   * @param taskId 任务ID
   */
  function pauseTask(taskId: string): void {
    const task = findTask(taskId)
    if (!task || !isActive(task.status)) return
    task.abortController?.abort()
  }

  /**
   * 续传已暂停的任务
   * 使用之前保存的 uploadId 调用 resumeChunkUpload 跳过已完成分片
   *
   * @param taskId 任务ID
   */
  async function resumeTask(taskId: string): Promise<void> {
    const task = findTask(taskId)
    if (!task || task.status !== 'paused' || !task.uploadId) return

    const abortController = new AbortController()
    updateTask(taskId, { abortController })

    try {
      const result = await resumeChunkUpload(
        task.uploadId,
        task.file,
        {
          onProgress(percent, message) {
            updateTask(taskId, { progress: percent, message })
          },
          onStatusChange(status) {
            const statusMap: Record<string, UploadTaskStatus> = {
              initializing: 'initializing',
              uploading: 'uploading',
              completing: 'completing',
              done: 'done',
              failed: 'failed',
            }
            updateTask(taskId, { status: statusMap[status] || task.status, message: '' })
            persistSnapshot()
          },
        },
        abortController.signal,
      )

      updateTask(taskId, {
        status: 'done',
        progress: 100,
        message: '上传完成',
        completedAt: Date.now(),
        result: result || undefined,
      })
    } catch (e) {
      const err = e as Error
      if (err.name === ABORT_ERROR_NAME) {
        updateTask(taskId, { status: 'paused', message: '已暂停' })
        persistSnapshot()
        return
      }
      updateTask(taskId, {
        status: 'failed',
        message: err.message || '续传失败',
        errorMessage: err.message,
      })
    } finally {
      scheduleNext()
    }
  }

  /**
   * 取消上传任务
   * 调 abort API 清除 OSS 碎片和 Redis 缓存
   *
   * @param taskId 任务ID
   */
  async function cancelTask(taskId: string): Promise<void> {
    const task = findTask(taskId)
    if (!task) return

    // 先中止前端上传
    task.abortController?.abort()

    // 如果有 uploadId，通知后端清除
    if (task.uploadId) {
      try {
        await abortChunkUpload(task.uploadId)
      } catch {
        // 忽略取消失败
      }
    }

    updateTask(taskId, { status: 'cancelled', message: '已取消' })
    persistSnapshot()
    scheduleNext()
  }

  /**
   * 重试失败的任务
   * 重置状态为 pending 后重新调度
   *
   * @param taskId 任务ID
   */
  function retryTask(taskId: string): void {
    const task = findTask(taskId)
    if (!task || task.status !== 'failed') return
    updateTask(taskId, {
      status: 'pending',
      progress: 0,
      message: '排队中...',
      errorMessage: undefined,
      completedChunks: 0,
    })
    scheduleNext()
  }

  /**
   * 清空所有已完成/已取消/已失败的任务
   */
  function clearCompleted(): void {
    tasks.value = tasks.value.filter(t =>
      ['pending', 'hashing', 'initializing', 'uploading', 'paused', 'completing'].includes(t.status)
    )
    if (tasks.value.length === 0) clearSnapshot()
  }

  /**
   * 移除单个任务
   *
   * @param taskId 任务ID
   */
  function removeTask(taskId: string): void {
    const task = findTask(taskId)
    if (task?.status === 'uploading' || task?.status === 'hashing') {
      task.abortController?.abort()
    }
    tasks.value = tasks.value.filter(t => t.id !== taskId)
    persistSnapshot()
  }

  /**
   * 从 localStorage 恢复上次未完成的任务快照
   * 页面加载时调用
   *
   * @returns 恢复的任务快照列表（不包含 File 对象，需要用户重新选文件）
   */
  function restoreSnapshots(): TaskSnapshot[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) return []
      const snapshots: TaskSnapshot[] = JSON.parse(raw)
      // 过滤掉过期或已完成的
      return snapshots.filter(s =>
        ['pending', 'hashing', 'initializing', 'uploading', 'paused'].includes(s.status)
      )
    } catch {
      return []
    }
  }

  return {
    tasks,
    activeCount,
    totalCount,
    hasActiveTasks,
    addTask,
    pauseTask,
    resumeTask,
    cancelTask,
    retryTask,
    clearCompleted,
    removeTask,
    restoreSnapshots,
  }
})