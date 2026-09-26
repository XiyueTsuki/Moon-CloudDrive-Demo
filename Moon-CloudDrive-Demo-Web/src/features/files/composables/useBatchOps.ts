/**
 * 批量操作 Composable
 * 负责多选、批量删除、批量移动、批量重命名逻辑
 */
import { ref } from 'vue'
import { batchDelete, batchMove, batchRename, getFileList } from '../api/files'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FileInfo } from '../types/files'

export function useBatchOps(fileList: () => FileInfo[], onSuccess: () => void) {
  /** 当前页已选中的文件ID集合 */
  const selectedFileIds = ref<Set<number>>(new Set())
  /** 是否全选当前页 */
  const isAllSelected = ref(false)
  /** 半选状态 */
  const isIndeterminate = ref(false)

  /** 批量移动弹窗 */
  const batchMoveDialogVisible = ref(false)
  const batchMoveTargetParentId = ref<number | null>(null)

  /** 批量重命名弹窗 */
  const batchRenameDialogVisible = ref(false)
  const batchRenameMode = ref('sequence')
  const batchRenameValue = ref('')

  /** 当前页可被多选的文件（排除文件夹） */
  function getSelectableFiles(): FileInfo[] {
    return fileList().filter((f) => f.isFolder !== 1)
  }

  /** 同步全选/半选状态 */
  function syncSelectAllState() {
    const selectable = getSelectableFiles()
    const total = selectable.length
    const selected = selectable.filter((f) => selectedFileIds.value.has(f.id)).length

    if (total === 0) {
      isAllSelected.value = false
      isIndeterminate.value = false
    } else if (selected === total) {
      isAllSelected.value = true
      isIndeterminate.value = false
    } else if (selected > 0) {
      isAllSelected.value = false
      isIndeterminate.value = true
    } else {
      isAllSelected.value = false
      isIndeterminate.value = false
    }
  }

  /** 重新同步选中状态（翻页后调用） */
  function resyncSelectionState() {
    syncSelectAllState()
  }

  /** 单行复选框变更 */
  function handleSelectChange(fileId: number, checked: boolean) {
    const newSet = new Set(selectedFileIds.value)
    if (checked) {
      newSet.add(fileId)
    } else {
      newSet.delete(fileId)
    }
    selectedFileIds.value = newSet
    syncSelectAllState()
  }

  /** 全选/取消全选 */
  function handleSelectAllChange(checked: boolean) {
    if (checked) {
      const ids = getSelectableFiles().map((f) => f.id)
      selectedFileIds.value = new Set(ids)
    } else {
      selectedFileIds.value = new Set()
    }
    isAllSelected.value = checked
    isIndeterminate.value = false
  }

  /** 校验是否有选中文件 */
  function checkBatchSelection(): boolean {
    if (selectedFileIds.value.size === 0) {
      ElMessage.warning('请至少勾选一个文件')
      return false
    }
    return true
  }

  /** 批量删除 */
  async function handleBatchDelete() {
    if (!checkBatchSelection()) return
    const count = selectedFileIds.value.size
    try {
      await ElMessageBox.confirm(
        `确定将该 ${count} 个文件/文件夹移入回收站吗？`,
        '批量删除确认',
        { confirmButtonText: '确定删除', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return
    }

    try {
      const res = await batchDelete(Array.from(selectedFileIds.value))
      const result = res.data.data
      if (result.failCount > 0 && result.successCount === 0) {
        ElMessage.error('批量删除失败：' + result.failReasons.join('；'))
      } else if (result.failCount > 0) {
        ElMessage.warning(`成功删除 ${result.successCount} 个，${result.failCount} 个失败：${result.failReasons.join('；')}`)
      } else {
        ElMessage.success(`成功删除 ${result.successCount} 个文件`)
      }
      clearSelection()
      onSuccess()
    } catch {
      // 统一拦截处理
    }
  }

  /** 打开批量移动弹窗 */
  async function openBatchMoveDialog() {
    if (!checkBatchSelection()) return
    batchMoveTargetParentId.value = null
    batchMoveDialogVisible.value = true
  }

  /** 懒加载批量移动的文件夹树 */
  async function loadBatchMoveFolderNodes(node: any, resolve: (data: any[]) => void) {
    const parentId = node.level === 0 ? null : node.data.id
    try {
      const res = await getFileList({ parentId, size: 500 })
      resolve(
        (res.data.data.records || [])
          .filter((f: FileInfo) => f.isFolder === 1)
          .map((f: FileInfo) => ({ id: f.id, label: f.originalFilename, isLeaf: false })),
      )
    } catch {
      resolve([])
    }
  }

  /** 批量移动树节点点击 */
  function handleBatchMoveTreeNodeClick(data: { id: number }) {
    batchMoveTargetParentId.value = data.id
  }

  /** 提交批量移动 */
  async function handleSubmitBatchMove() {
    try {
      const res = await batchMove(
        Array.from(selectedFileIds.value),
        batchMoveTargetParentId.value,
      )
      const result = res.data.data
      if (result.failCount > 0 && result.successCount === 0) {
        ElMessage.error('批量移动失败：' + result.failReasons.join('；'))
      } else if (result.failCount > 0) {
        ElMessage.warning(`成功移动 ${result.successCount} 个，${result.failCount} 个失败：${result.failReasons.join('；')}`)
      } else {
        ElMessage.success(`成功移动 ${result.successCount} 个文件`)
      }
      batchMoveDialogVisible.value = false
      clearSelection()
      onSuccess()
    } catch {
      // 统一拦截处理
    }
  }

  /** 打开批量重命名弹窗 */
  function openBatchRenameDialog() {
    if (!checkBatchSelection()) return
    batchRenameMode.value = 'sequence'
    batchRenameValue.value = ''
    batchRenameDialogVisible.value = true
  }

  /** 提交批量重命名 */
  async function handleSubmitBatchRename() {
    if (!batchRenameValue.value.trim()) {
      ElMessage.warning('请输入重命名参数')
      return
    }
    try {
      // batchRename API 参数为 (fileIds, mode, value)
      const res = await batchRename(
        Array.from(selectedFileIds.value),
        batchRenameMode.value,
        batchRenameValue.value.trim(),
      )
      const result = res.data.data
      if (result.failCount > 0 && result.successCount === 0) {
        ElMessage.error('批量重命名失败：' + result.failReasons.join('；'))
      } else if (result.failCount > 0) {
        ElMessage.warning(`成功重命名 ${result.successCount} 个，${result.failCount} 个失败：${result.failReasons.join('；')}`)
      } else {
        ElMessage.success(`成功重命名 ${result.successCount} 个文件`)
      }
      batchRenameDialogVisible.value = false
      clearSelection()
      onSuccess()
    } catch {
      // 统一拦截处理
    }
  }

  /** 清空选中状态 */
  function clearSelection() {
    selectedFileIds.value = new Set()
    isAllSelected.value = false
    isIndeterminate.value = false
  }

  return {
    selectedFileIds,
    isAllSelected,
    isIndeterminate,
    batchMoveDialogVisible,
    batchMoveTargetParentId,
    batchRenameDialogVisible,
    batchRenameMode,
    batchRenameValue,
    resyncSelectionState,
    handleSelectChange,
    handleSelectAllChange,
    handleBatchDelete,
    openBatchMoveDialog,
    loadBatchMoveFolderNodes,
    handleBatchMoveTreeNodeClick,
    handleSubmitBatchMove,
    openBatchRenameDialog,
    handleSubmitBatchRename,
    clearSelection,
  }
}