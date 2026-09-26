/**
 * 文件夹操作 Composable
 * 负责新建文件夹和移动文件/文件夹的逻辑
 */
import { ref } from 'vue'
import { createFolder, moveFile, getFileList } from '../api/files'
import { ElMessage } from 'element-plus'
import type { FileInfo } from '../types/files'

export function useFolderOps(currentParentId: () => number | null, onSuccess: () => void) {
  /** 新建文件夹弹窗 */
  const newFolderDialogVisible = ref(false)
  const newFolderName = ref('')
  /** 移动弹窗 */
  const moveDialogVisible = ref(false)
  const movingFile = ref<FileInfo | null>(null)
  const moveTargetParentId = ref<number | null>(null)
  const moveExcludeId = ref<number | null>(null)

  /** 打开新建文件夹弹窗 */
  function openNewFolderDialog() {
    newFolderName.value = ''
    newFolderDialogVisible.value = true
  }

  /** 创建文件夹 */
  async function handleCreateFolder() {
    if (!newFolderName.value.trim()) {
      ElMessage.warning('请输入文件夹名称')
      return
    }
    try {
      await createFolder(newFolderName.value.trim(), currentParentId())
      ElMessage.success('文件夹创建成功')
      newFolderDialogVisible.value = false
      onSuccess()
    } catch {
      // 错误已在拦截器中统一处理
    }
  }

  /** 打开移动弹窗 */
  async function openMoveDialog(file: FileInfo) {
    movingFile.value = file
    moveTargetParentId.value = null
    moveExcludeId.value = file.id
    moveDialogVisible.value = true
  }

  /** 懒加载文件夹树节点 */
  async function loadMoveFolderNodes(node: any, resolve: (data: any[]) => void) {
    const parentId = node.level === 0 ? null : node.data.id
    try {
      const res = await getFileList({ parentId, size: 500 })
      resolve(
        (res.data.data.records || [])
          .filter((f: FileInfo) => f.isFolder === 1 && f.id !== moveExcludeId.value)
          .map((f: FileInfo) => ({ id: f.id, label: f.originalFilename, isLeaf: false })),
      )
    } catch {
      resolve([])
    }
  }

  /** 树节点点击 */
  function handleMoveTreeNodeClick(data: { id: number }) {
    moveTargetParentId.value = data.id
  }

  /** 提交移动 */
  async function handleSubmitMove() {
    if (!movingFile.value) return
    try {
      await moveFile(movingFile.value.id, moveTargetParentId.value)
      ElMessage.success('移动成功')
      moveDialogVisible.value = false
      onSuccess()
    } catch {
      // 错误已在拦截器中统一处理
    }
  }

  return {
    newFolderDialogVisible,
    newFolderName,
    moveDialogVisible,
    movingFile,
    moveTargetParentId,
    moveExcludeId,
    openNewFolderDialog,
    handleCreateFolder,
    openMoveDialog,
    loadMoveFolderNodes,
    handleMoveTreeNodeClick,
    handleSubmitMove,
  }
}