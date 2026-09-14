/**
 * 分片上传工具模块
 * 负责：SHA-256 计算、文件分片、分片上传流程控制、断点续传
 *
 * 使用场景：文件超过 10MB 时自动启用，通过 shouldUseChunkUpload() 判断
 *
 * 上传流程：
 * 1. SHA-256 → 计算文件哈希用于秒传和完整性校验
 * 2. init    → 调用后端接口初始化分片上传任务
 * 3. upload  → 将文件按5MB分片，并发上传（最多3个并发）
 * 4. complete→ 所有分片完成后调用后端合并接口
 *
 * 断点续传流程：
 * 1. getProgress → 查询已上传分片列表
 * 2. 跳过已完成分片，仅上传缺失分片
 * 3. complete     → 合并所有分片
 */
import {
  initChunkUpload,
  uploadChunk,
  completeChunkUpload,
  getChunkProgress,
  abortChunkUpload,
} from '@/api/file'
import type { ChunkInitResponse, FileInfo } from '@/types/api'

/** 每个分片大小：5MB（OSS最小分片要求） */
const CHUNK_SIZE = 5 * 1024 * 1024
/** 小文件阈值：10MB以下使用普通上传，以上使用分片上传 */
const SMALL_FILE_THRESHOLD = 10 * 1024 * 1024
/** 分片上传最大并发数 */
const MAX_CONCURRENT = 3

/** 分片上传过程中的回调函数 */
export interface ChunkUploadCallbacks {
  /** 进度更新回调，percent为0-100，message为描述文字 */
  onProgress?: (percent: number, message: string) => void
  /** 状态变更回调 */
  onStatusChange?: (status: 'hashing' | 'initializing' | 'uploading' | 'completing' | 'done' | 'failed') => void
}

interface ChunkTask {
  index: number
  blob: Blob
}

/**
 * 使用 Web Crypto API 计算文件的 SHA-256 哈希值
 * 用于秒传去重和文件完整性校验
 *
 * @param file 要计算哈希的文件对象
 * @returns 十六进制格式的SHA-256哈希字符串
 */
export async function sha256(file: File): Promise<string> {
  const buffer = await file.arrayBuffer()
  const hashBuffer = await crypto.subtle.digest('SHA-256', buffer)
  const hashArray = Array.from(new Uint8Array(hashBuffer))
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('')
}

/**
 * 判断文件是否应使用分片上传
 * 阈值：大于 10MB 的文件启用分片上传
 *
 * @param file 要判断的文件对象
 */
export function shouldUseChunkUpload(file: File): boolean {
  return file.size > SMALL_FILE_THRESHOLD
}

/**
 * 完整的分片上传流程
 *
 * 步骤：
 * 1. 计算文件 SHA-256 哈希
 * 2. 调用后端 init 接口，获取 uploadId 和分片参数
 * 3. 如果后端返回秒传（instantComplete=true），直接返回已有文件信息
 * 4. 按 chunkSize 切分文件，分批并发上传（每批 MAX_CONCURRENT 个）
 * 5. 全部分片完成后调用 complete 接口合并文件
 *
 * @param file      要上传的文件对象
 * @param parentId  目标文件夹ID，null表示根目录
 * @param callbacks 进度和状态回调
 * @returns 上传完成的文件信息
 */
export async function chunkUpload(
  file: File,
  parentId: number | null,
  callbacks: ChunkUploadCallbacks = {},
): Promise<FileInfo> {
  const { onProgress, onStatusChange } = callbacks

  // 阶段1：计算文件哈希
  onStatusChange?.('hashing')
  onProgress?.(0, '正在计算文件哈希...')

  const fileHash = await sha256(file)
  onProgress?.(5, 'SHA-256 计算完成')

  // 阶段2：初始化分片上传
  onStatusChange?.('initializing')
  onProgress?.(5, '正在初始化分片上传...')

  const initRes = await initChunkUpload({
    fileName: file.name,
    fileSize: file.size,
    fileHash,
    parentId,
  })

  const initData: ChunkInitResponse = initRes.data.data

  // 秒传：文件已存在，无需重复上传
  if (initData.instantComplete) {
    onStatusChange?.('done')
    onProgress?.(100, '秒传成功')
    return initData.file!
  }

  // 阶段3：构建分片任务列表
  const { uploadId, chunkCount, chunkSize } = initData
  const pendingTasks: ChunkTask[] = []

  for (let i = 0; i < chunkCount; i++) {
    const start = i * chunkSize
    const end = Math.min(start + chunkSize, file.size)
    pendingTasks.push({
      index: i,
      blob: file.slice(start, end),
    })
  }

  // 阶段4：并发上传分片
  onStatusChange?.('uploading')
  onProgress?.(10, `开始上传，共 ${chunkCount} 个分片`)

  let completedCount = 0
  const totalChunks = chunkCount

  const uploadSingleChunk = async (task: ChunkTask): Promise<void> => {
    try {
      await uploadChunk(task.blob, uploadId, task.index)
      completedCount++
      // 进度范围：10% → 95%（预留5%给哈希计算和合并阶段）
      const percent = 10 + Math.round((completedCount / totalChunks) * 85)
      onProgress?.(percent, `分片上传中 ${completedCount}/${totalChunks}`)
    } catch (e) {
      throw new Error(`分片 ${task.index} 上传失败: ${e}`)
    }
  }

  // 分批并发：每次最多 MAX_CONCURRENT 个分片同时上传
  for (let i = 0; i < pendingTasks.length; i += MAX_CONCURRENT) {
    const batch = pendingTasks.slice(i, i + MAX_CONCURRENT)
    await Promise.all(batch.map(uploadSingleChunk))
  }

  // 阶段5：合并分片
  onStatusChange?.('completing')
  onProgress?.(95, '正在合并分片...')

  const completeRes = await completeChunkUpload({
    uploadId,
    contentType: file.type || 'application/octet-stream',
  })

  onStatusChange?.('done')
  onProgress?.(100, '上传完成')

  return completeRes.data.data
}

/**
 * 断点续传：从已上传进度恢复分片上传
 *
 * 使用场景：
 * - 上传中断后，用户重新选择同一个文件
 * - 传入之前的 uploadId，系统自动跳过已完成分片
 *
 * 步骤：
 * 1. 查询进度，获取已完成分片集合
 * 2. 如果所有分片已完成，直接调用 complete 合并
 * 3. 否则仅上传缺失的分片，最后合并
 *
 * @param uploadId 之前的上传任务标识
 * @param file     同一个文件对象（必须与初始化时一致）
 * @param callbacks 进度和状态回调
 * @returns 上传完成的文件信息
 */
export async function resumeChunkUpload(
  uploadId: string,
  file: File,
  callbacks: ChunkUploadCallbacks = {},
): Promise<FileInfo | null> {
  const { onProgress, onStatusChange } = callbacks

  // 查询进度
  onStatusChange?.('initializing')
  onProgress?.(0, '正在查询上传进度...')

  const progressRes = await getChunkProgress(uploadId)
  const progress = progressRes.data.data

  if (!progress) {
    throw new Error('上传任务不存在或已过期')
  }

  const completedSet = new Set(progress.completedParts)

  // 所有分片已完成，直接合并
  if (progress.completedCount === progress.chunkCount) {
    onStatusChange?.('completing')
    onProgress?.(95, '正在合并分片...')

    const completeRes = await completeChunkUpload({
      uploadId,
      contentType: file.type || 'application/octet-stream',
    })

    onStatusChange?.('done')
    onProgress?.(100, '上传完成')
    return completeRes.data.data
  }

  // 构建待上传分片列表（跳过已完成分片）
  const pendingTasks: ChunkTask[] = []
  const chunkSize = progress.chunkCount > 0
    ? Math.ceil(file.size / progress.chunkCount)
    : CHUNK_SIZE

  for (let i = 0; i < progress.chunkCount; i++) {
    const partNumber = i + 1
    // 跳过已完成的分片
    if (completedSet.has(partNumber)) {
      continue
    }
    const start = i * chunkSize
    const end = Math.min(start + chunkSize, file.size)
    pendingTasks.push({
      index: i,
      blob: file.slice(start, end),
    })
  }

  const remainingCount = pendingTasks.length
  onStatusChange?.('uploading')
  onProgress?.(10, `续传中，剩余 ${remainingCount} 个分片`)

  let completedCount = 0

  const uploadSingleChunk = async (task: ChunkTask): Promise<void> => {
    try {
      await uploadChunk(task.blob, uploadId, task.index)
      completedCount++
      const percent = 10 + Math.round((completedCount / remainingCount) * 85)
      onProgress?.(percent, `分片上传中 ${completedCount}/${remainingCount}`)
    } catch (e) {
      throw new Error(`分片 ${task.index} 上传失败: ${e}`)
    }
  }

  for (let i = 0; i < pendingTasks.length; i += MAX_CONCURRENT) {
    const batch = pendingTasks.slice(i, i + MAX_CONCURRENT)
    await Promise.all(batch.map(uploadSingleChunk))
  }

  onStatusChange?.('completing')
  onProgress?.(95, '正在合并分片...')

  const completeRes = await completeChunkUpload({
    uploadId,
    contentType: file.type || 'application/octet-stream',
  })

  onStatusChange?.('done')
  onProgress?.(100, '上传完成')

  return completeRes.data.data
}

export { abortChunkUpload, getChunkProgress }