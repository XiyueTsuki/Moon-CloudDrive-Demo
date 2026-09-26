/**
 * 上传任务管理 Store
 *
 * 核心职责：
 *   - 管理所有上传任务的生命周期（创建、排队、执行、暂停、续传、取消）
 *   - 全局并发控制（最多 MAX_CONCURRENT 个任务同时上传）
 *   - 任务队列自动调度（一个完成 → 自动启动下一个）
 *   - localStorage 持久化（页面刷新后可恢复未完成任务）
 *
 * 任务状态流转：
 *   pending → hashing → initializing → uploading ⇄ paused
 *                                          ↓
 *                                      completing
 *                                          ↓
 *                                         done
 *   任意阶段出错 → failed / 任意阶段取消 → cancelled
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  chunkUpload,
  resumeChunkUpload,
  shouldUseChunkUpload,
  abortChunkUpload,
  ABORT_ERROR_NAME,
} from '../utils/chunkUpload'
import { generateUUID } from '../utils/uuid'
import { persistSnapshot, clearSnapshot, restoreSnapshots } from '../utils/persistence'
import type { UploadTask, UploadTaskStatus } from '../types/upload'

/** 全局最大并发上传数 */
const MAX_CONCURRENT = 2

export const useUploadStore = defineStore('upload', () => {
  const tasks = ref<UploadTask[]>([])

  // ==================== 计算属性 ====================

  /** 正在执行中的任务数 */
  const activeCount = computed(() =>
    tasks.value.filter((t) =>
      ['hashing', 'initializing', 'uploading', 'completing'].includes(t.status),
    ).length,
  )

  /** 所有任务数 */
  const totalCount = computed(() => tasks.value.length)

  /** 是否有进行中的任务 */
  const hasActiveTasks = computed(() => activeCount.value > 0)

  // ==================== 内部方法 ====================

  /** 查找任务 */
  function findTask(taskId: string): UploadTask | undefined {
    return tasks.value.find((t) => t.id === taskId)
  }

  /** 更新任务字段 */
  function updateTask(taskId: string, updates: Partial<UploadTask>): void {
    const task = findTask(taskId)
    if (task) Object.assign(task, updates)
  }

  /** 保存任务快照到 localStorage */
  function persist(): void {
    persistSnapshot(tasks.value)
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
    const next = tasks.value.find((t) => t.status === 'pending')
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
              completedChunks: Math.round(((percent - 10) * task.totalChunks) / 85) || 0,
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
            persist()
          },
        },
        signal,
        (initResult) => {
          updateTask(taskId, {
            uploadId: initResult.uploadId,
            fileHash: initResult.fileHash,
            totalChunks: initResult.chunkCount,
          })
          persist()
        },
      )

      updateTask(taskId, {
        status: 'done',
        progress: 100,
        message: '上传完成',
        completedAt: Date.now(),
        result,
      })
      persist()
    } catch (e) {
      const err = e as Error
      if (err.name === ABORT_ERROR_NAME) {
        updateTask(taskId, { status: 'paused', message: '已暂停' })
        persist()
        return
      }
      updateTask(taskId, {
        status: 'failed',
        message: err.message || '上传失败',
        errorMessage: err.message,
      })
    } finally {
      scheduleNext()
    }
  }

  /** 启动普通上传任务（小文件） */
  async function startSimpleTask(
    taskId: string,
    file: File,
    parentId: number | null,
  ): Promise<void> {
    updateTask(taskId, { status: 'uploading', message: '正在上传...', progress: 0 })

    try {
      const { uploadFile, getProgress } = await import('../api/upload')
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

  /** 添加文件到上传队列 */
  function addTask(file: File, parentId: number | null): string {
    const id = generateUUID()
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

  /** 暂停指定任务 */
  function pauseTask(taskId: string): void {
    const task = findTask(taskId)
    if (!task || !isActive(task.status)) return
    task.abortController?.abort()
  }

  /** 续传已暂停的任务 */
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
            persist()
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
        persist()
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

  /** 取消上传任务 */
  async function cancelTask(taskId: string): Promise<void> {
    const task = findTask(taskId)
    if (!task) return

    task.abortController?.abort()

    if (task.uploadId) {
      try {
        await abortChunkUpload(task.uploadId)
      } catch {
        // 忽略取消失败
      }
    }

    updateTask(taskId, { status: 'cancelled', message: '已取消' })
    persist()
    scheduleNext()
  }

  /** 重试失败的任务 */
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

  /** 清空所有已完成/已取消/已失败的任务 */
  function clearCompleted(): void {
    tasks.value = tasks.value.filter((t) =>
      ['pending', 'hashing', 'initializing', 'uploading', 'paused', 'completing'].includes(
        t.status,
      ),
    )
    if (tasks.value.length === 0) clearSnapshot()
  }

  /** 移除单个任务 */
  function removeTask(taskId: string): void {
    const task = findTask(taskId)
    if (task?.status === 'uploading' || task?.status === 'hashing') {
      task.abortController?.abort()
    }
    tasks.value = tasks.value.filter((t) => t.id !== taskId)
    persist()
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