/**
 * 打包下载 Composable
 * 负责提交打包任务、轮询进度、触发浏览器下载
 */
import { ref } from 'vue'
import { preparePackDownload, getPackProgress } from '@/features/pack-download/api/packDownload'
import { ElMessage } from 'element-plus'
import { Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'

export function usePackDownload(selectedFileIds: () => Set<number>, onDownloadDone: () => void) {
  /** 打包弹窗 */
  const packDialogVisible = ref(false)
  const packTaskId = ref('')
  const packStatus = ref('')
  const packPercent = ref(0)
  const packMessage = ref('')
  const packZipFilename = ref('')
  let packPollTimer: ReturnType<typeof setInterval> | null = null

  /** 提交打包任务并开始轮询 */
  async function handleBatchDownload() {
    if (selectedFileIds().size === 0) {
      ElMessage.warning('请至少勾选一个文件')
      return
    }

    try {
      const res = await preparePackDownload(Array.from(selectedFileIds()))
      const taskId = res.data.data
      startPackPolling(taskId)
    } catch {
      // 统一拦截处理
    }
  }

  /** 开始轮询打包进度 */
  function startPackPolling(taskId: string) {
    packTaskId.value = taskId
    packStatus.value = 'queued'
    packPercent.value = 0
    packMessage.value = '任务已提交，等待处理...'
    packZipFilename.value = ''
    packDialogVisible.value = true
    stopPackPolling()

    packPollTimer = setInterval(async () => {
      try {
        const res = await getPackProgress(taskId)
        const progress = res.data.data
        packStatus.value = progress.status
        packPercent.value = progress.percent
        packMessage.value = progress.message
        packZipFilename.value = progress.zipFilename || ''

        if (progress.status === 'ready') {
          stopPackPolling()
          triggerPackDownload(taskId)
        } else if (progress.status === 'failed') {
          stopPackPolling()
          ElMessage.error(progress.message || '打包失败')
        }
      } catch {
        // 网络错误不中断轮询
      }
    }, 1000)
  }

  /** 停止轮询 */
  function stopPackPolling() {
    if (packPollTimer !== null) {
      clearInterval(packPollTimer)
      packPollTimer = null
    }
  }

  /** 触发浏览器下载 ZIP */
  function triggerPackDownload(taskId: string) {
    const token = localStorage.getItem('token')
    if (!token) {
      ElMessage.error('登录状态已失效，请重新登录')
      return
    }
    const a = document.createElement('a')
    a.href = `/api/file/pack/download?taskId=${taskId}&satoken=${token}`
    a.download = ''
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    ElMessage.success('开始下载')

    setTimeout(() => {
      packDialogVisible.value = false
      onDownloadDone()
    }, 1500)
  }

  /** 手动关闭打包弹窗 */
  function handleClosePackDialog() {
    stopPackPolling()
    packDialogVisible.value = false
  }

  return {
    packDialogVisible,
    packStatus,
    packPercent,
    packMessage,
    packZipFilename,
    handleBatchDownload,
    handleClosePackDialog,
  }
}

export { Loading, CircleCheck, CircleClose }