<script setup lang="ts">
/**
 * 首页组件
 * 提供文件管理核心功能：上传、文件夹管理、下载、删除、重命名、分享、移动
 * 支持文件夹层级导航（面包屑），区分文件和文件夹的不同操作
 */
import { ref, onMounted, onUnmounted, watch } from 'vue'
import {
  uploadFile, getProgress, getFileList, getDownloadUrl,
  deleteFile, renameFile, createFolder, moveFile, getFolderPath,
} from '@/api/file'
import { createShare } from '@/api/share'
import { chunkUpload, shouldUseChunkUpload, abortChunkUpload } from '@/utils/chunkUpload'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Download, Delete, Edit, Share, FolderAdd, FolderOpened, RefreshRight, Search } from '@element-plus/icons-vue'
import type { FileInfo } from '@/types/api'

// ==================== 上传相关状态 ====================
const uploading = ref(false)
const uploadPercent = ref(0)
const uploadStatus = ref('')
const uploadMessage = ref('')
const uploadStage = ref('')
const currentTaskId = ref('')
const selectedFile = ref<File | null>(null)
const uploadDialogVisible = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null

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
const folderList = ref<FileInfo[]>([])

// ==================== 分享弹窗相关状态 ====================
const shareDialogVisible = ref(false)
const shareFileId = ref(0)
const sharePassword = ref('')
const shareExpireHours = ref(24)
const shareMaxDownloads = ref(0)

// ==================== 上传功能 ====================

function handleFileChange(file: File) {
  selectedFile.value = file
}

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

function startPolling(taskId: string) {
  pollTimer = setInterval(async () => {
    try {
      const res = await getProgress(taskId)
      const progress = res.data.data
      uploadPercent.value = progress.percent
      uploadStatus.value = progress.status
      uploadMessage.value = progress.message

      if (progress.status === 'done') {
        stopPolling()
        uploading.value = false
        uploadDialogVisible.value = false
        selectedFile.value = null
        refreshFileList()
      } else if (progress.status === 'failed') {
        stopPolling()
        ElMessage.error('上传失败：' + progress.message)
        uploading.value = false
      }
    } catch {
      stopPolling()
      uploading.value = false
    }
  }, 1000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  uploadPercent.value = 0
  uploadMessage.value = ''

  // 文件 > 10MB 自动启用分片上传，≤ 10MB 使用普通上传
  if (shouldUseChunkUpload(selectedFile.value)) {
    uploadStatus.value = 'uploading'
    uploadStage.value = 'chunk'

    try {
      await chunkUpload(selectedFile.value, currentParentId.value, {
        onProgress(percent, message) {
          uploadPercent.value = percent
          uploadMessage.value = message
        },
        onStatusChange(status) {
          if (status === 'done') {
            uploadStatus.value = 'done'
            uploadStage.value = ''
            uploading.value = false
            // 延迟关闭对话框让用户看到完成状态
            setTimeout(() => {
              uploadDialogVisible.value = false
              selectedFile.value = null
            }, 1200)
            refreshFileList()
          } else if (status === 'failed') {
            uploadStatus.value = 'failed'
            uploadStage.value = ''
            ElMessage.error(uploadMessage.value || '上传失败')
            uploading.value = false
          }
        },
      })
    } catch (e) {
      uploadStatus.value = 'failed'
      uploadStage.value = ''
      const errMsg = e instanceof Error ? e.message : '上传失败'
      ElMessage.error(errMsg)
      uploading.value = false
    }
    return
  }

  // 小文件：使用原有普通上传 + 轮询进度
  uploadStatus.value = 'uploading'
  uploadStage.value = 'simple'
  uploadMessage.value = '正在上传...'

  try {
    const res = await uploadFile(selectedFile.value, currentParentId.value)
    const taskId = res.data.data
    currentTaskId.value = taskId
    startPolling(taskId)
  } catch {
    uploading.value = false
    uploadStage.value = ''
  }
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
  // 加载根目录文件夹列表供选择
  try {
    const res = await getFileList(null)
    // 只保留文件夹，排除自身
    folderList.value = (res.data.data || []).filter(
      (f: FileInfo) => f.isFolder === 1 && f.id !== file.id,
    )
  } catch {
    folderList.value = []
  }
  moveDialogVisible.value = true
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

// ==================== 生命周期 ====================

onMounted(() => {
  loadBreadcrumbs()
  loadFileList()
})

onUnmounted(() => {
  stopPolling()
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
          :data="fileList"
          v-loading="fileListLoading"
          empty-text="此文件夹为空"
          style="width: 100%"
          stripe
          @sort-change="handleSortChange"
        >
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
          <el-table-column label="操作" width="320" align="center" fixed="right">
            <template #default="{ row }">
              <template v-if="row.isFolder !== 1">
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

    <!-- ==================== 悬浮上传按钮（右下角） ==================== -->
    <el-tooltip content="上传文件" placement="left">
      <div class="upload-fab" @click="uploadDialogVisible = true">
        <el-icon :size="24"><UploadFilled /></el-icon>
      </div>
    </el-tooltip>

    <!-- ==================== 上传对话框 ==================== -->
    <el-dialog
      v-model="uploadDialogVisible"
      title="上传文件"
      width="480px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <p class="upload-target-hint">
        上传到：{{ breadcrumbs[breadcrumbs.length - 1]?.name }}
      </p>

      <el-upload
        class="upload-area"
        drag
        :auto-upload="false"
        :show-file-list="true"
        :on-change="(file: any) => handleFileChange(file.raw)"
        :limit="1"
        accept="*"
      >
        <el-icon class="upload-icon"><UploadFilled /></el-icon>
        <div class="upload-text">
          <p class="upload-title">将文件拖到此处，或<em>点击选择</em></p>
        </div>
      </el-upload>

      <div v-if="selectedFile" class="file-info">
        <el-tag type="info" size="large">
          {{ selectedFile.name }} ({{ formatFileSize(selectedFile.size) }})
        </el-tag>
      </div>

      <div v-if="uploading || uploadStatus === 'done'" class="progress-section">
        <div class="progress-header">
          <span>上传进度</span>
          <el-tag
            :type="uploadStatus === 'done' ? 'success' : uploadStatus === 'failed' ? 'danger' : 'warning'"
            size="small"
          >
            {{ uploadStatus === 'done' ? '已完成' : uploadStatus === 'failed' ? '失败' : '上传中' }}
          </el-tag>
        </div>
        <el-progress
          :percentage="uploadPercent"
          :status="uploadStatus === 'done' ? 'success' : uploadStatus === 'failed' ? 'exception' : ''"
          :stroke-width="20"
          :text-inside="true"
        />
        <p v-if="uploadMessage" class="progress-msg">
          {{ uploadMessage }}
          <template v-if="uploadStage === 'chunk' && uploadPercent >= 10 && uploadPercent < 95">
            ，超出 10MB 已自动启用分片上传
          </template>
        </p>
      </div>

      <template #footer>
        <el-button @click="uploadDialogVisible = false" :disabled="uploading">取消</el-button>
        <el-button
          type="primary"
          :loading="uploading"
          :disabled="!selectedFile"
          @click="handleUpload"
        >
          {{ uploading ? '上传中...' : '开始上传' }}
        </el-button>
      </template>
    </el-dialog>

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
    <el-dialog v-model="moveDialogVisible" title="移动到..." width="420px" :close-on-click-modal="false">
      <p class="move-desc" v-if="movingFile">
        将「{{ movingFile.originalFilename }}」移动到：
      </p>
      <el-radio-group v-model="moveTargetParentId" style="display:flex;flex-direction:column;gap:8px">
        <el-radio :value="null">根目录</el-radio>
        <el-radio v-for="f in folderList" :key="f.id" :value="f.id">
          📁 {{ f.originalFilename }}
        </el-radio>
      </el-radio-group>
      <el-empty v-if="folderList.length === 0" description="没有可用的目标文件夹" :image-size="60" />
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitMove">移动</el-button>
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

/* ==================== 上传对话框 ==================== */
.upload-target-hint {
  margin: 0 0 12px 0;
  color: #909399;
  font-size: 13px;
}

.upload-area {
  width: 100%;
}

.upload-icon {
  font-size: 48px;
  color: #409eff;
}

.upload-text {
  margin-top: 8px;
}

.upload-title {
  font-size: 16px;
  color: #606266;
  margin: 0;
}

.upload-title em {
  color: #409eff;
  font-style: normal;
}

.file-info {
  margin: 12px 0;
}

.progress-section {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  font-size: 14px;
  color: #606266;
}

.progress-msg {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
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
</style>