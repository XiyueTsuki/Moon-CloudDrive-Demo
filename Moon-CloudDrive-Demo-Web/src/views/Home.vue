<script setup lang="ts">
/**
 * 首页组件
 * 提供文件管理核心功能：上传、文件夹管理、下载、删除、重命名、分享、移动、多文件打包下载
 * 支持文件夹层级导航（面包屑），区分文件和文件夹的不同操作
 */
import { ref, onMounted, onUnmounted, watch } from 'vue'
import {
  getFileList, getDownloadUrl,
  deleteFile, renameFile, createFolder, moveFile, getFolderPath,
  preparePackDownload, getPackProgress,
  getPreviewInfo, getTextContent,
  batchDelete, batchMove, batchRename,
} from '@/api/file'
import { createShare } from '@/api/share'
import { useUploadStore } from '@/stores/upload'
import UploadTaskPanel from '@/components/UploadTaskPanel.vue'
import PdfImageViewer from '@/components/PdfImageViewer.vue'
import { RefreshFileListEvent } from '@/events/fileEvents'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Download, Delete, Edit, Share, FolderAdd, FolderOpened, RefreshRight, Search, Loading, CircleCheck, CircleClose, View } from '@element-plus/icons-vue'
import type { FileInfo, PreviewInfo } from '@/types/api'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

// ==================== 上传相关状态 ====================
const uploadStore = useUploadStore()
const uploadInputRef = ref<HTMLInputElement | null>(null)

// ==================== 导航相关状态 ====================
const currentParentId = ref<number | null>(null)
const breadcrumbs = ref<{ id: number | null; name: string }[]>([{ id: null, name: '根目录' }])

// ==================== 文件列表相关状态 ====================
const fileList = ref<FileInfo[]>([])
const fileListLoading = ref(false)
// 分页
const currentPage = ref(1)
const pageSize = ref(20)
const totalFiles = ref(0)
// 排序
const sortBy = ref('uploadTime')
const sortOrder = ref<'asc' | 'desc'>('desc')
// 搜索
const searchKeyword = ref('')
const renameDialogVisible = ref(false)
const renamingFile = ref<FileInfo | null>(null)
const renameNewName = ref('')

// ==================== 文件夹弹窗状态 ====================
const newFolderDialogVisible = ref(false)
const newFolderName = ref('')
const moveDialogVisible = ref(false)
const movingFile = ref<FileInfo | null>(null)
const moveTargetParentId = ref<number | null>(null)
const moveExcludeId = ref<number | null>(null)

// ==================== 批量操作弹窗相关状态 ====================
const batchMoveDialogVisible = ref(false)
const batchMoveTargetParentId = ref<number | null>(null)
const batchRenameDialogVisible = ref(false)
const batchRenameMode = ref('sequence')
const batchRenameValue = ref('')

// ==================== 分享弹窗相关状态 ====================
const shareDialogVisible = ref(false)
const shareFileId = ref(0)
const sharePassword = ref('')
const shareExpireHours = ref(24)
const shareMaxDownloads = ref(0)

// ==================== 文件选择（用于多文件打包下载）相关状态 ====================
/** 当前页已选中的文件ID集合 */
const selectedFileIds = ref<Set<number>>(new Set())
/** 是否全选当前页所有文件 */
const isAllSelected = ref(false)
/** 是否处于全选但部分取消的半选状态 */
const isIndeterminate = ref(false)

// ==================== 打包下载弹窗相关状态 ====================
const packDialogVisible = ref(false)
const packTaskId = ref('')
const packStatus = ref('')
const packPercent = ref(0)
const packMessage = ref('')
const packZipFilename = ref('')
/** 轮询定时器ID */
let packPollTimer: ReturnType<typeof setInterval> | null = null
/** 用于在打包对话框内中止请求的控制器 */
let packAbortController: AbortController | null = null

// ==================== 文件预览相关状态 ====================
const previewDialogVisible = ref(false)
const previewLoading = ref(false)
const previewInfo = ref<PreviewInfo | null>(null)
/** 文本文件内容（仅文本预览时有效） */
const previewTextContent = ref('')
/** 经过 highlight.js 高亮后的 HTML（仅文本预览时有效） */
const previewTextHtml = ref('')
/** 预览错误消息 */
const previewErrorMsg = ref('')
/** 当前预览的文件信息（用于不支持预览时提供下载入口） */
const previewFile = ref<FileInfo | null>(null)

// ==================== 上传功能 ====================

/** 点击悬浮按钮 → 打开文件选择器，选中后加入上传队列 */
function triggerUpload(): void {
  uploadInputRef.value?.click()
}

/** 文件选择器变更 → 将文件加入上传队列 */
function handleFileInputChange(e: Event): void {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadStore.addTask(file, currentParentId.value)
  // 清空 input 以便重复选择同一文件
  input.value = ''
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// ==================== 文件列表功能 ====================

async function loadFileList() {
  fileListLoading.value = true
  try {
    const res = await getFileList({
      parentId: currentParentId.value,
      page: currentPage.value,
      size: pageSize.value,
      sortBy: sortBy.value,
      sortOrder: sortOrder.value,
      keyword: searchKeyword.value || undefined,
    })
    const pageResult = res.data.data
    fileList.value = pageResult.records
    totalFiles.value = pageResult.total
    resyncSelectionState()
  } catch {
    // 统一拦截处理
  } finally {
    fileListLoading.value = false
  }
}

/** 重置到第一页并刷新（创建/删除/重命名/移动等操作后使用） */
function refreshFileList() {
  currentPage.value = 1
  loadFileList()
}

/** 排序变化 */
function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
  if (order) {
    sortBy.value = prop
    sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  } else {
    sortBy.value = 'uploadTime'
    sortOrder.value = 'desc'
  }
  currentPage.value = 1
  loadFileList()
}

/** 分页大小变化 */
function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadFileList()
}

/** 页码变化 */
function handlePageChange(page: number) {
  currentPage.value = page
  loadFileList()
}

/** 搜索 */
function handleSearch() {
  currentPage.value = 1
  loadFileList()
}

/** 按名称排序切换 */
function sortOrderLabel(field: string): string {
  if (sortBy.value !== field) return ''
  return sortOrder.value === 'asc' ? ' ↑' : ' ↓'
}

async function loadBreadcrumbs() {
  if (currentParentId.value == null) {
    breadcrumbs.value = [{ id: null, name: '根目录' }]
    return
  }
  try {
    const res = await getFolderPath(currentParentId.value)
    const path = res.data.data || []
    breadcrumbs.value = [
      { id: null, name: '根目录' },
      ...path.map((f: FileInfo) => ({ id: f.id, name: f.originalFilename })),
    ]
  } catch {
    breadcrumbs.value = [{ id: null, name: '根目录' }]
  }
}

function navigateTo(folderId: number | null) {
  currentParentId.value = folderId
}

async function handleFolderClick(folder: FileInfo) {
  currentParentId.value = folder.id
}

watch(currentParentId, async () => {
  currentPage.value = 1
  searchKeyword.value = ''
  await Promise.all([loadBreadcrumbs(), loadFileList()])
})

async function handleDownload(file: FileInfo) {
  try {
    const res = await getDownloadUrl(file.id)
    window.open(res.data.data, '_blank')
  } catch {
    // 统一拦截处理
  }
}

async function handleDelete(file: FileInfo) {
  const title = file.isFolder === 1 ? '文件夹' : '文件'
  const confirmMsg = file.isFolder === 1
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
    refreshFileList()
  } catch {
    // 取消或不处理
  }
}

function openRenameDialog(file: FileInfo) {
  renamingFile.value = file
  if (file.isFolder === 1) {
    renameNewName.value = file.originalFilename
  } else {
    const lastDot = file.originalFilename.lastIndexOf('.')
    renameNewName.value = lastDot > 0 ? file.originalFilename.substring(0, lastDot) : file.originalFilename
  }
  renameDialogVisible.value = true
}

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
    refreshFileList()
    loadBreadcrumbs()
  } catch {
    // 统一拦截处理
  }
}

// ==================== 文件夹新建功能 ====================

function openNewFolderDialog() {
  newFolderName.value = ''
  newFolderDialogVisible.value = true
}

async function handleCreateFolder() {
  if (!newFolderName.value.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  try {
    await createFolder(newFolderName.value.trim(), currentParentId.value)
    ElMessage.success('文件夹创建成功')
    newFolderDialogVisible.value = false
    refreshFileList()
  } catch {
    // 统一拦截处理
  }
}

// ==================== 文件夹移动功能 ====================

async function openMoveDialog(file: FileInfo) {
  movingFile.value = file
  moveTargetParentId.value = null
  moveExcludeId.value = file.id
  moveDialogVisible.value = true
}

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

function handleMoveTreeNodeClick(data: { id: number }) {
  moveTargetParentId.value = data.id
}

async function handleSubmitMove() {
  if (!movingFile.value) return
  try {
    await moveFile(movingFile.value.id, moveTargetParentId.value)
    ElMessage.success('移动成功')
    moveDialogVisible.value = false
    refreshFileList()
    loadBreadcrumbs()
  } catch {
    // 统一拦截处理
  }
}

// ==================== 分享功能 ====================

function handleCreateShare(file: FileInfo) {
  shareFileId.value = file.id
  sharePassword.value = ''
  shareExpireHours.value = 24
  shareMaxDownloads.value = 0
  shareDialogVisible.value = true
}

async function submitCreateShare() {
  try {
    await createShare({
      fileId: shareFileId.value,
      password: sharePassword.value || undefined,
      expireHours: shareExpireHours.value || undefined,
      maxDownloads: shareMaxDownloads.value > 0 ? shareMaxDownloads.value : undefined,
    })
    ElMessage.success('分享链接创建成功')
    shareDialogVisible.value = false
  } catch {
    // 统一拦截处理
  }
}

// ==================== 多文件选择功能（用于打包下载） ====================

/**
 * 计算当前页中所有可被多选的文件
 * 排除文件夹，只选择普通文件
 */
function getSelectableFiles(): FileInfo[] {
  return fileList.value.filter(f => f.isFolder !== 1)
}

/** 单行复选框变更 → 更新选中集合、全选/半选状态 */
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

/** 全选/取消全选变更 */
function handleSelectAllChange(checked: boolean) {
  if (checked) {
    const ids = getSelectableFiles().map(f => f.id)
    selectedFileIds.value = new Set(ids)
  } else {
    selectedFileIds.value = new Set()
  }
  isAllSelected.value = checked
  isIndeterminate.value = false
}

/** 根据当前选中集合同步全选/半选状态 */
function syncSelectAllState() {
  const selectable = getSelectableFiles()
  const total = selectable.length
  const selected = selectable.filter(f => selectedFileIds.value.has(f.id)).length

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

/** 翻页或刷新后重新同步全选状态（selectedFileIds 是跨页持久的） */
function resyncSelectionState() {
  // 新加载的文件列表可能与缓存的选择不一致，仅同步表头状态
  syncSelectAllState()
}

// ==================== 多文件打包下载功能 ====================

/**
 * 点击"批量下载"按钮 → 提交打包任务 → 打开进度弹窗 → 轮询进度
 * 流程：校验选中 → 提交任务 → 开弹窗并轮询 → 就绪后自动触发浏览器下载
 */
async function handleBatchDownload() {
  if (selectedFileIds.value.size === 0) {
    ElMessage.warning('请至少勾选一个文件')
    return
  }

  try {
    const res = await preparePackDownload(Array.from(selectedFileIds.value))
    const taskId = res.data.data
    startPackPolling(taskId)
  } catch {
    // 统一拦截处理
  }
}

/**
 * 开始轮询打包进度
 * 每1秒查询一次，直到 ready / failed
 *
 * @param taskId 后端返回的打包任务ID
 */
function startPackPolling(taskId: string) {
  // 重置状态
  packTaskId.value = taskId
  packStatus.value = 'queued'
  packPercent.value = 0
  packMessage.value = '任务已提交，等待处理...'
  packZipFilename.value = ''
  packDialogVisible.value = true

  // 清除旧定时器
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
        // 打包完成，停止轮询，触发浏览器下载
        stopPackPolling()
        triggerPackDownload(taskId)
      } else if (progress.status === 'failed') {
        // 打包失败，停止轮询，保留弹窗展示错误信息
        stopPackPolling()
        ElMessage.error(progress.message || '打包失败')
      }
    } catch {
      // 网络错误等，不中断轮询，由后端超时兜底
    }
  }, 1000)
}

/** 停止打包进度轮询 */
function stopPackPolling() {
  if (packPollTimer !== null) {
    clearInterval(packPollTimer)
    packPollTimer = null
  }
}

/**
 * 触发浏览器下载打包好的 ZIP 文件
 * 通过创建隐藏 <a> 标签并携带 token 参数实现认证下载
 *
 * @param taskId 打包任务ID
 */
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

  // 下载开始后延迟关闭弹窗，清空选中
  setTimeout(() => {
    packDialogVisible.value = false
    selectedFileIds.value = new Set()
    isAllSelected.value = false
    isIndeterminate.value = false
  }, 1500)
}

/** 用户手动关闭打包进度弹窗 → 停止轮询 */
function handleClosePackDialog() {
  stopPackPolling()
  packDialogVisible.value = false
}

// ==================== 批量操作功能 ====================

function checkBatchSelection(): boolean {
  if (selectedFileIds.value.size === 0) {
    ElMessage.warning('请至少勾选一个文件')
    return false
  }
  return true
}

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
    selectedFileIds.value = new Set()
    isAllSelected.value = false
    isIndeterminate.value = false
    refreshFileList()
    loadBreadcrumbs()
  } catch {
    // 统一拦截处理
  }
}

async function openBatchMoveDialog() {
  if (!checkBatchSelection()) return
  batchMoveTargetParentId.value = null
  batchMoveDialogVisible.value = true
}

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

function handleBatchMoveTreeNodeClick(data: { id: number }) {
  batchMoveTargetParentId.value = data.id
}

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
    selectedFileIds.value = new Set()
    isAllSelected.value = false
    isIndeterminate.value = false
    refreshFileList()
  } catch {
    // 统一拦截处理
  }
}

function openBatchRenameDialog() {
  if (!checkBatchSelection()) return
  batchRenameMode.value = 'sequence'
  batchRenameValue.value = ''
  batchRenameDialogVisible.value = true
}

async function handleSubmitBatchRename() {
  if (!batchRenameValue.value.trim()) {
    ElMessage.warning('请输入重命名参数')
    return
  }
  try {
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
    selectedFileIds.value = new Set()
    isAllSelected.value = false
    isIndeterminate.value = false
    refreshFileList()
  } catch {
    // 统一拦截处理
  }
}

// ==================== 文件在线预览功能 ====================

/**
 * 打开文件预览对话框
 * 调用后端接口获取预览信息，根据 previewType 分流渲染：
 * - image/video/audio/pdf → 直接使用返回的 URL
 * - text → 再调 getTextContent 获取内容并用 highlight.js 高亮
 * - unsupported → 显示提示
 *
 * @param file 要预览的文件信息
 */
async function handlePreview(file: FileInfo) {
  previewFile.value = file
  // 重置所有状态
  previewDialogVisible.value = true
  previewLoading.value = true
  previewInfo.value = null
  previewTextContent.value = ''
  previewTextHtml.value = ''
  previewErrorMsg.value = ''

  try {
    const res = await getPreviewInfo(file.id)
    const info = res.data.data
    previewInfo.value = info

    // 文本类文件 → 额外获取文本内容并高亮
    if (info.previewType === 'text') {
      await loadTextContent(file.id, info.language || 'plaintext')
    }

    previewLoading.value = false
  } catch {
    previewErrorMsg.value = '加载预览信息失败，请重试'
    previewLoading.value = false
  }
}

/**
 * 获取文本文件内容并进行代码高亮
 *
 * @param fileId   文件 ID
 * @param language 语言标识，如 "java", "json"
 */
async function loadTextContent(fileId: number, language: string) {
  try {
    const res = await getTextContent(fileId)
    const text = res.data.data
    previewTextContent.value = text.content

    // 使用 highlight.js 进行代码高亮
    // plaintext 时用 highlightAuto 自动检测，否则直接指定语言
    const result = language === 'plaintext'
      ? hljs.highlightAuto(text.content)
      : hljs.highlight(text.content, { language, ignoreIllegals: true })
    previewTextHtml.value = result.value
  } catch (e: any) {
    previewErrorMsg.value = e?.response?.data?.msg || '获取文本内容失败'
  }
}

/**
 * 关闭预览对话框 → 重置所有状态，释放资源
 */
function handleClosePreview() {
  previewDialogVisible.value = false
  previewInfo.value = null
  previewFile.value = null
  previewTextContent.value = ''
  previewTextHtml.value = ''
  previewErrorMsg.value = ''
}

/**
 * 复制文本内容到剪贴板
 * 仅文本/代码预览时可用
 */
async function copyTextContent() {
  if (!previewTextContent.value) return
  try {
    await navigator.clipboard.writeText(previewTextContent.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    // fallback for older browsers
    const textarea = document.createElement('textarea')
    textarea.value = previewTextContent.value
    textarea.style.position = 'fixed'
    textarea.style.opacity = '0'
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('已复制到剪贴板')
  }
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadBreadcrumbs()
  loadFileList()
  // 监听上传完成事件，自动刷新文件列表
  window.addEventListener(RefreshFileListEvent, refreshFileList)
})

onUnmounted(() => {
  window.removeEventListener(RefreshFileListEvent, refreshFileList)
})
</script>

<template>
  <div class="home-container">
    <main class="home-main">
      <!-- ==================== 文件/文件夹展示区域（面包屑 + 工具栏 + 列表） ==================== -->
      <el-card class="content-card" shadow="never">
        <!-- 面包屑导航 -->
        <div class="breadcrumb-bar">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item
              v-for="(crumb, index) in breadcrumbs"
              :key="crumb.id ?? 'root'"
            >
              <el-link
                :underline="false"
                :type="index === breadcrumbs.length - 1 ? 'default' : 'primary'"
                :disabled="index === breadcrumbs.length - 1"
                @click="navigateTo(crumb.id)"
              >
                {{ crumb.name }}
              </el-link>
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <!-- 工具栏 -->
        <div class="toolbar">
          <el-button type="primary" :icon="FolderAdd" @click="openNewFolderDialog">
            新建文件夹
          </el-button>
          <el-button
            type="success"
            :icon="Download"
            :disabled="selectedFileIds.size === 0"
            @click="handleBatchDownload"
          >
            批量下载 ({{ selectedFileIds.size }})
          </el-button>
          <el-button
            type="danger"
            :icon="Delete"
            :disabled="selectedFileIds.size === 0"
            @click="handleBatchDelete"
          >
            批量删除
          </el-button>
          <el-button
            type="warning"
            :disabled="selectedFileIds.size === 0"
            @click="openBatchMoveDialog"
          >
            批量移动
          </el-button>
          <el-button
            :disabled="selectedFileIds.size === 0"
            @click="openBatchRenameDialog"
          >
            批量重命名
          </el-button>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文件名..."
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" plain @click="handleSearch">搜索</el-button>
          <el-button text type="primary" :icon="RefreshRight" @click="refreshFileList" :loading="fileListLoading">
            刷新
          </el-button>
        </div>

        <!-- 文件/文件夹表格 -->
        <el-table
          ref="fileTableRef"
          :data="fileList"
          v-loading="fileListLoading"
          empty-text="此文件夹为空"
          style="width: 100%"
          stripe
          @sort-change="handleSortChange"
        >
          <!-- 多选复选框列 -->
          <el-table-column width="50" align="center">
            <template #header>
              <el-checkbox
                v-model="isAllSelected"
                :indeterminate="isIndeterminate"
                @change="handleSelectAllChange"
              />
            </template>
            <template #default="{ row }">
              <el-checkbox
                v-if="row.isFolder !== 1"
                :model-value="selectedFileIds.has(row.id)"
                @change="(checked: boolean) => handleSelectChange(row.id, checked)"
              />
            </template>
          </el-table-column>
          <el-table-column label="名称" min-width="240" prop="name" sortable="custom">
            <template #default="{ row }">
              <div
                :class="['name-cell', { 'is-folder': row.isFolder === 1 }]"
                @click="row.isFolder === 1 ? handleFolderClick(row) : undefined"
              >
                <el-icon v-if="row.isFolder === 1" class="folder-icon" color="#e6a23c">
                  <FolderOpened />
                </el-icon>
                <span class="file-name" :title="row.originalFilename">{{ row.originalFilename }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="大小" width="120" align="center" prop="size" sortable="custom">
            <template #default="{ row }">
              {{ row.isFolder === 1 ? '-' : formatFileSize(row.fileSize) }}
            </template>
          </el-table-column>
          <el-table-column label="类型" width="140" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.isFolder === 1 ? 'warning' : 'info'">
                {{ row.isFolder === 1 ? '文件夹' : (row.contentType || '未知') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="上传时间" width="180" align="center" prop="uploadTime" sortable="custom">
            <template #default="{ row }">
              {{ new Date(row.uploadTime).toLocaleString() }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="380" align="center" fixed="right">
            <template #default="{ row }">
              <template v-if="row.isFolder !== 1">
                <el-button type="primary" size="small" :icon="View" link @click="handlePreview(row)">预览</el-button>
                <el-button type="primary" size="small" :icon="Download" link @click="handleDownload(row)">下载</el-button>
                <el-button type="success" size="small" :icon="Share" link @click="handleCreateShare(row)">分享</el-button>
              </template>
              <el-button type="warning" size="small" :icon="Edit" link @click="openRenameDialog(row)">重命名</el-button>
              <el-button type="info" size="small" :icon="'Rank'" link @click="openMoveDialog(row)">移动</el-button>
              <el-button type="danger" size="small" :icon="Delete" link @click="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div v-if="totalFiles > 0" class="pagination-bar">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="totalFiles"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>
    </main>

    <!-- ==================== 隐藏文件选择器 ==================== -->
    <input
      ref="uploadInputRef"
      type="file"
      style="display: none"
      accept="*"
      @change="handleFileInputChange"
    />

    <!-- ==================== 悬浮上传按钮（右下角） ==================== -->
    <el-tooltip content="上传文件" placement="left">
      <div class="upload-fab" @click="triggerUpload">
        <el-icon :size="24"><UploadFilled /></el-icon>
      </div>
    </el-tooltip>

    <!-- ==================== 上传任务面板 ==================== -->
    <UploadTaskPanel />

    <!-- 重命名对话框 -->
    <el-dialog v-model="renameDialogVisible" title="重命名" width="400px" :close-on-click-modal="false">
      <el-input
        v-model="renameNewName"
        placeholder="请输入新名称"
        maxlength="200"
        show-word-limit
        @keyup.enter="handleRename"
      >
        <template v-if="renamingFile && renamingFile.isFolder !== 1" #append>
          <span class="file-ext-suffix">
            {{ renamingFile.originalFilename.substring(renamingFile.originalFilename.lastIndexOf('.')) }}
          </span>
        </template>
      </el-input>
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRename">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新建文件夹对话框 -->
    <el-dialog v-model="newFolderDialogVisible" title="新建文件夹" width="400px" :close-on-click-modal="false">
      <el-input
        v-model="newFolderName"
        placeholder="请输入文件夹名称"
        maxlength="200"
        show-word-limit
        @keyup.enter="handleCreateFolder"
      />
      <template #footer>
        <el-button @click="newFolderDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateFolder">创建</el-button>
      </template>
    </el-dialog>

    <!-- 移动文件/文件夹对话框 -->
    <el-dialog v-model="moveDialogVisible" title="移动到..." width="420px" :close-on-click-modal="false" destroy-on-close>
      <p class="move-desc" v-if="movingFile">
        将「{{ movingFile.originalFilename }}」移动到：
      </p>
      <div class="move-tree-wrapper">
        <div
          class="move-root-option"
          :class="{ active: moveTargetParentId === null }"
          @click="moveTargetParentId = null"
        >
          📂 根目录
        </div>
        <el-tree
          :load="loadMoveFolderNodes"
          lazy
          node-key="id"
          highlight-current
          :current-node-key="moveTargetParentId"
          :props="{ label: 'label', isLeaf: 'isLeaf' }"
          @node-click="handleMoveTreeNodeClick"
        >
          <template #default="{ data }">
            <span class="el-tree-node__label-custom">📁 {{ data.label }}</span>
          </template>
        </el-tree>
      </div>
      <p class="move-target-hint">
        当前目标：{{ moveTargetParentId === null ? '根目录' : '已选文件夹' }}
      </p>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitMove">移动</el-button>
      </template>
    </el-dialog>

    <!-- 批量移动对话框 -->
    <el-dialog v-model="batchMoveDialogVisible" title="批量移动到..." width="420px" :close-on-click-modal="false" destroy-on-close>
      <p style="margin-bottom:12px;color:#666">已选中 {{ selectedFileIds.size }} 个文件/文件夹</p>
      <div class="move-tree-wrapper">
        <div
          class="move-root-option"
          :class="{ active: batchMoveTargetParentId === null }"
          @click="batchMoveTargetParentId = null"
        >
          📂 根目录
        </div>
        <el-tree
          :load="loadBatchMoveFolderNodes"
          lazy
          node-key="id"
          highlight-current
          :current-node-key="batchMoveTargetParentId"
          :props="{ label: 'label', isLeaf: 'isLeaf' }"
          @node-click="handleBatchMoveTreeNodeClick"
        >
          <template #default="{ data }">
            <span class="el-tree-node__label-custom">📁 {{ data.label }}</span>
          </template>
        </el-tree>
      </div>
      <p class="move-target-hint">
        当前目标：{{ batchMoveTargetParentId === null ? '根目录' : '已选文件夹' }}
      </p>
      <template #footer>
        <el-button @click="batchMoveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitBatchMove">移动</el-button>
      </template>
    </el-dialog>

    <!-- 批量重命名对话框 -->
    <el-dialog v-model="batchRenameDialogVisible" title="批量重命名" width="500px" :close-on-click-modal="false">
      <p style="margin-bottom:12px;color:#666">已选中 {{ selectedFileIds.size }} 个文件/文件夹</p>
      <el-form label-width="80px" label-position="left">
        <el-form-item label="模式">
          <el-select v-model="batchRenameMode" style="width:100%">
            <el-option label="序号模板 (如 图片_{n})" value="sequence" />
            <el-option label="添加前缀" value="prefix" />
            <el-option label="添加后缀" value="suffix" />
            <el-option label="替换文本 (旧->新)" value="replace" />
          </el-select>
        </el-form-item>
        <el-form-item :label="batchRenameMode === 'sequence' ? '模板' : batchRenameMode === 'replace' ? '替换' : '内容'">
          <el-input
            v-model="batchRenameValue"
            :placeholder="batchRenameMode === 'sequence'
              ? '如：图片_  → 图片_1.jpg, 图片_2.jpg...'
              : batchRenameMode === 'replace'
              ? '如：旧文本->新文本'
              : '输入要添加的内容'"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <el-alert
        v-if="batchRenameMode === 'sequence'"
        type="info"
        show-icon
        :closable="false"
        style="margin-top:8px"
      >
        预览：第一个文件将变为「{{ batchRenameValue || 'file_' }}1」，
        第二个变为「{{ batchRenameValue || 'file_' }}2」，保留原扩展名
      </el-alert>
      <template #footer>
        <el-button @click="batchRenameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitBatchRename">确定重命名</el-button>
      </template>
    </el-dialog>

    <!-- 创建分享对话框 -->
    <el-dialog v-model="shareDialogVisible" title="创建分享链接" width="440px" :close-on-click-modal="false">
      <el-form label-width="100px" label-position="left">
        <el-form-item label="提取码">
          <el-input v-model="sharePassword" placeholder="可选，留空则无需提取码" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="有效时长">
          <el-input-number v-model="shareExpireHours" :min="1" :max="720" :step="1" controls-position="right" style="width:100%" />
          <span class="form-hint">小时，默认 24 小时，最长 720 小时（30天）</span>
        </el-form-item>
        <el-form-item label="最大下载次数">
          <el-input-number v-model="shareMaxDownloads" :min="0" :max="9999" :step="1" controls-position="right" style="width:100%" />
          <span class="form-hint">次，0 表示不限制下载次数</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="shareDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitCreateShare">创建</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 文件预览对话框 ==================== -->
    <el-dialog
      v-model="previewDialogVisible"
      :title="previewInfo?.fileName || '文件预览'"
      width="80%"
      :close-on-click-modal="false"
      :destroy-on-close="true"
      @close="handleClosePreview"
    >
      <div class="preview-body" v-loading="previewLoading">
        <!-- 加载中 -->
        <template v-if="previewLoading">
          <div class="preview-loading">
            <el-icon class="is-loading" :size="32"><Loading /></el-icon>
            <p>正在加载预览...</p>
          </div>
        </template>

        <!-- 加载失败 -->
        <template v-else-if="previewErrorMsg">
          <el-result icon="error" :title="previewErrorMsg" sub-title="请稍后重试或下载文件后查看" />
        </template>

        <!-- 图片预览 -->
        <template v-else-if="previewInfo?.previewType === 'image'">
          <div class="preview-image-wrap">
            <img :src="previewInfo.previewUrl!" :alt="previewInfo.fileName" class="preview-image" />
          </div>
        </template>

        <!-- 视频预览 -->
        <template v-else-if="previewInfo?.previewType === 'video'">
          <div class="preview-video-wrap">
            <video
              :src="previewInfo.previewUrl!"
              controls
              autoplay
              class="preview-video"
              preload="metadata"
            >
              您的浏览器不支持视频播放
            </video>
          </div>
        </template>

        <!-- 音频预览 -->
        <template v-else-if="previewInfo?.previewType === 'audio'">
          <div class="preview-audio-wrap">
            <el-icon :size="48" color="#409eff"><View /></el-icon>
            <p class="preview-audio-name">{{ previewInfo.fileName }}</p>
            <audio :src="previewInfo.previewUrl!" controls autoplay class="preview-audio">
              您的浏览器不支持音频播放
            </audio>
          </div>
        </template>

        <!-- PDF 预览（服务端转图片模式） -->
        <template v-else-if="previewInfo?.previewType === 'pdf_image'">
          <PdfImageViewer
            :file-id="previewFile!.id"
            :file-name="previewInfo.fileName"
          />
        </template>

        <!-- 文本/代码预览 -->
        <template v-else-if="previewInfo?.previewType === 'text'">
          <div class="preview-text-wrap">
            <!-- 语言标签 + 复制按钮 -->
            <div class="preview-text-header" v-if="previewInfo.language">
              <el-tag size="small" type="info">{{ previewInfo.language }}</el-tag>
              <el-button size="small" plain @click="copyTextContent">
                复制内容
              </el-button>
            </div>
            <!-- 代码高亮块 -->
            <pre class="preview-code-block">
              <code
                v-if="previewTextHtml"
                class="hljs"
                v-html="previewTextHtml"
              />
              <code v-else class="hljs">{{ previewTextContent }}</code>
            </pre>
          </div>
        </template>

        <!-- 不支持预览 -->
        <template v-else-if="previewInfo?.previewType === 'unsupported'">
          <el-result
            icon="warning"
            title="该文件类型暂不支持在线预览"
            :sub-title="`文件类型（${previewInfo?.mimeType || '未知'}）不在支持的预览范围内，请下载后查看`"
          />
        </template>

        <!-- 无数据时兜底 -->
        <el-empty v-else description="暂无预览数据" />
      </div>

      <template #footer>
        <el-button @click="handleClosePreview">关闭</el-button>
        <el-button
          v-if="previewInfo && previewInfo.previewType === 'unsupported' && previewFile"
          type="primary"
          @click="previewDialogVisible = false; handleDownload(previewFile!)"
        >
          下载文件
        </el-button>
      </template>
    </el-dialog>

    <!-- ==================== 打包下载进度对话框 ==================== -->
    <el-dialog
      v-model="packDialogVisible"
      title="多文件打包下载"
      width="440px"
      :close-on-click-modal="false"
      :show-close="packStatus !== 'processing' && packStatus !== 'queued'"
      @close="handleClosePackDialog"
    >
      <div class="pack-progress-body">
        <!-- 进度条 -->
        <el-progress
          :percentage="packPercent"
          :status="packStatus === 'failed' ? 'exception' : packStatus === 'ready' ? 'success' : undefined"
          :stroke-width="18"
          :text-inside="true"
        />

        <!-- 状态文字 -->
        <p class="pack-status-text">
          <!-- 排队中 -->
          <template v-if="packStatus === 'queued'">
            <el-icon class="is-loading"><Loading /></el-icon>
            {{ packMessage }}
          </template>
          <!-- 处理中 -->
          <template v-else-if="packStatus === 'processing'">
            <el-icon class="is-loading"><Loading /></el-icon>
            {{ packMessage }}
          </template>
          <!-- 已完成 -->
          <template v-else-if="packStatus === 'ready'">
            <el-icon style="color:#67c23a"><CircleCheck /></el-icon>
            打包完成，正在开始下载...
          </template>
          <!-- 失败 -->
          <template v-else-if="packStatus === 'failed'">
            <el-icon style="color:#f56c6c"><CircleClose /></el-icon>
            打包失败：{{ packMessage }}
          </template>
          <!-- 未知状态 -->
          <template v-else>
            {{ packMessage || '准备中...' }}
          </template>
        </p>
      </div>

      <template #footer>
        <el-button
          v-if="packStatus !== 'processing' && packStatus !== 'queued'"
          @click="handleClosePackDialog"
        >
          关闭
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.home-container {
  min-height: calc(100vh - 60px);
  background: #f5f7fa;
}

.home-main {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

/* ==================== 文件/文件夹展示卡片 ==================== */
.content-card {
  border-radius: 8px;
}

/* 面包屑 */
.breadcrumb-bar {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

/* 工具栏 */
.toolbar {
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ==================== 悬浮上传按钮 ==================== */
.upload-fab {
  position: fixed;
  right: 32px;
  bottom: 32px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
  z-index: 50;
}

.upload-fab:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.55);
}

.upload-fab:active {
  transform: scale(0.95);
}

/* ==================== 文件列表 ==================== */
.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.name-cell.is-folder {
  cursor: pointer;
}

.name-cell.is-folder:hover {
  color: #409eff;
}

.folder-icon {
  font-size: 20px;
  flex-shrink: 0;
}

.file-name {
  display: inline-block;
  max-width: 280px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-ext-suffix {
  color: #909399;
  font-size: 12px;
}

.form-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}

.move-desc {
  margin-bottom: 12px;
  color: #606266;
}

.move-tree-wrapper {
  max-height: 320px;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 4px 0;
}

.move-root-option {
  padding: 8px 16px;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  border-bottom: 1px solid #ebeef5;
  transition: background 0.2s;
}

.move-root-option:hover {
  background: #f5f7fa;
}

.move-root-option.active {
  color: #409eff;
  background: #ecf5ff;
}

.move-target-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}

/* ==================== 打包下载对话框 ==================== */
.pack-progress-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  padding: 10px 0;
}

.pack-status-text {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
  margin: 0;
  text-align: center;
}

/* ==================== 文件预览对话框 ==================== */
.preview-body {
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 加载中 */
.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: #909399;
}

/* 图片预览 */
.preview-image-wrap {
  width: 100%;
  display: flex;
  justify-content: center;
  background: #f5f5f5;
  border-radius: 4px;
  padding: 8px;
}

.preview-image {
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  border-radius: 4px;
}

/* 视频预览 */
.preview-video-wrap {
  width: 100%;
  display: flex;
  justify-content: center;
  background: #000;
  border-radius: 4px;
}

.preview-video {
  max-width: 100%;
  max-height: 70vh;
  outline: none;
}

/* 音频预览 */
.preview-audio-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 40px 0;
}

.preview-audio-name {
  font-size: 16px;
  color: #303133;
  margin: 0;
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-audio {
  width: 100%;
  max-width: 480px;
  outline: none;
}

/* 文本/代码预览 */
.preview-text-wrap {
  width: 100%;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
}

.preview-text-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-bottom: none;
  border-radius: 4px 4px 0 0;
}

.preview-code-block {
  margin: 0;
  padding: 16px;
  background: #fafbfc;
  border: 1px solid #e4e7ed;
  border-radius: 0 0 4px 4px;
  overflow: auto;
  max-height: 60vh;
  font-size: 13px;
  line-height: 1.6;
}

.preview-code-block code {
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>