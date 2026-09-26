/**
 * 文件操作 Composable
 * 负责单文件操作：下载、删除、重命名
 */
import { ref } from 'vue'
import { getDownloadUrl, deleteFile, renameFile } from '../api/files'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FileInfo } from '../types/files'

export function useFileActions(onSuccess: () => void) {
  /** 重命名弹窗可见性 */
  const renameDialogVisible = ref(false)
  /** 正在重命名的文件 */
  const renamingFile = ref<FileInfo | null>(null)
  /** 新文件名 */
  const renameNewName = ref('')

  /** 下载文件 */
  async function handleDownload(file: FileInfo) {
    try {
      const res = await getDownloadUrl(file.id)
      window.open(res.data.data, '_blank')
    } catch {
      // 错误已在拦截器中统一处理
    }
  }

  /** 删除文件（移入回收站） */
  async function handleDelete(file: FileInfo) {
    const title = file.isFolder === 1 ? '文件夹' : '文件'
    const confirmMsg =
      file.isFolder === 1
        ? `确定要删除文件夹「${file.originalFilename}」吗？其中的所有文件和子文件夹将一并移入回收站。`
        : `确定要删除文件「${file.originalFilename}」吗？删除后文件将移入回收站，30天后自动彻底清除。`

    try {
      await ElMessageBox.confirm(confirmMsg, `确认删除${title}`, {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      })
      await deleteFile(file.id)
      ElMessage.success(`${title}已移入回收站`)
      onSuccess()
    } catch {
      // 取消或不处理
    }
  }

  /** 打开重命名弹窗 */
  function openRenameDialog(file: FileInfo) {
    renamingFile.value = file
    if (file.isFolder === 1) {
      renameNewName.value = file.originalFilename
    } else {
      const lastDot = file.originalFilename.lastIndexOf('.')
      renameNewName.value =
        lastDot > 0 ? file.originalFilename.substring(0, lastDot) : file.originalFilename
    }
    renameDialogVisible.value = true
  }

  /** 提交重命名 */
  async function handleRename() {
    if (!renamingFile.value || !renameNewName.value.trim()) {
      ElMessage.warning('名称不能为空')
      return
    }

    try {
      let newName = renameNewName.value.trim()
      if (renamingFile.value.isFolder !== 1) {
        const originalName = renamingFile.value.originalFilename
        const lastDot = originalName.lastIndexOf('.')
        if (lastDot > 0) {
          newName += originalName.substring(lastDot)
        }
      }

      await renameFile(renamingFile.value.id, newName)
      ElMessage.success('重命名成功')
      renameDialogVisible.value = false
      onSuccess()
    } catch {
      // 错误已在拦截器中统一处理
    }
  }

  return {
    renameDialogVisible,
    renamingFile,
    renameNewName,
    handleDownload,
    handleDelete,
    openRenameDialog,
    handleRename,
  }
}