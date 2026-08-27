<script setup lang="ts">
/**
 * 首页组件
 * 提供文件上传、文件列表展示、下载、删除、重命名等核心功能
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { uploadFile, getProgress, getFileList, getDownloadUrl, deleteFile, renameFile } from '@/api/file'
import { createShare } from '@/api/share'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Download, Delete, Edit, Share } from '@element-plus/icons-vue'
import type { FileInfo } from '@/types/api'

const router = useRouter()
const userStore = useUserStore()

// ==================== 上传相关状态 ====================
/** 是否正在上传 */
const uploading = ref(false)
/** 上传进度百分比 */
const uploadPercent = ref(0)
/** 上传状态：uploading | done | failed */
const uploadStatus = ref('')
/** 上传进度消息 */
const uploadMessage = ref('')
/** 当前上传任务ID */
const currentTaskId = ref('')
/** 用户选择的文件 */
const selectedFile = ref<File | null>(null)
/** 轮询定时器 */
let pollTimer: ReturnType<typeof setInterval> | null = null

// ==================== 文件列表相关状态 ====================
/** 文件列表数据 */
const fileList = ref<FileInfo[]>([])
/** 文件列表是否正在加载 */
const fileListLoading = ref(false)
/** 重命名对话框是否可见 */
const renameDialogVisible = ref(false)
/** 当前正在重命名的文件 */
const renamingFile = ref<FileInfo | null>(null)
/** 重命名输入框的新文件名 */
const renameNewName = ref('')

// ==================== 分享弹窗相关状态 ====================
/** 分享创建对话框是否可见 */
const shareDialogVisible = ref(false)
/** 分享弹窗：文件ID */
const shareFileId = ref(0)
/** 分享弹窗：提取码 */
const sharePassword = ref('')
/** 分享弹窗：有效时长（小时），默认 24 小时 */
const shareExpireHours = ref(24)
/** 分享弹窗：最大下载次数，0 表示不限制 */
const shareMaxDownloads = ref(0)

// ==================== 上传功能 ====================

/**
 * 处理文件选择变化
 * @param file 用户选择的文件对象
 */
function handleFileChange(file: File) {
  selectedFile.value = file
}

/**
 * 格式化文件大小显示
 * @param bytes 文件字节数
 * @returns 格式化后的文件大小字符串，如 "1.5 MB"
 */
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/**
 * 开始轮询上传进度
 * 每秒查询一次进度，直到上传完成或失败
 * @param taskId 上传任务ID
 */
function startPolling(taskId: string) {
  pollTimer = setInterval(async () => {
    try {
      const res = await getProgress(taskId)
      const progress = res.data.data
      uploadPercent.value = progress.percent
      uploadStatus.value = progress.status
      uploadMessage.value = progress.message

      if (progress.status === 'done') {
        // 上传完成，停止轮询并刷新文件列表
        stopPolling()
        ElMessage.success('文件上传完成！')
        uploading.value = false
        loadFileList() // 刷新文件列表

        ElMessageBox.confirm(
          '文件上传成功！是否为此文件创建分享链接？',
          '上传完成',
          {
            confirmButtonText: '创建分享',
            cancelButtonText: '暂不创建',
            type: 'success',
          },
        ).then(() => {
          showCreateShareDialog(taskId)
        }).catch(() => {
          // 暂不创建，不做任何操作
        })
      } else if (progress.status === 'failed') {
        // 上传失败，停止轮询
        stopPolling()
        ElMessage.error('上传失败：' + progress.message)
        uploading.value = false
      }
    } catch {
      // 接口异常时停止轮询，避免无限请求
      stopPolling()
      uploading.value = false
    }
  }, 1000)
}

/** 停止轮询上传进度 */
function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

/** 处理上传按钮点击 */
async function handleUpload() {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  uploading.value = true
  uploadPercent.value = 0
  uploadStatus.value = 'uploading'
  uploadMessage.value = '正在上传...'

  try {
    const res = await uploadFile(selectedFile.value)
    const taskId = res.data.data
    currentTaskId.value = taskId
    ElMessage.success('上传任务已提交，正在处理...')
    startPolling(taskId)
  } catch {
    uploading.value = false
  }
}

// ==================== 文件列表功能 ====================

/** 加载文件列表 */
async function loadFileList() {
  fileListLoading.value = true
  try {
    const res = await getFileList()
    fileList.value = res.data.data
  } catch {
    // 错误已在拦截器中统一处理
  } finally {
    fileListLoading.value = false
  }
}

/**
 * 处理文件下载
 * 获取预签名URL后在新窗口打开下载
 * @param file 要下载的文件信息
 */
async function handleDownload(file: FileInfo) {
  try {
    const res = await getDownloadUrl(file.id)
    const downloadUrl = res.data.data
    // 在新窗口打开下载链接，触发浏览器下载
    window.open(downloadUrl, '_blank')
  } catch {
    // 错误已在拦截器中统一处理
  }
}

/**
 * 处理文件删除
 * 弹出确认框后执行删除操作
 * @param file 要删除的文件信息
 */
async function handleDelete(file: FileInfo) {
  try {
    await ElMessageBox.confirm(
      `确定要删除文件 "${file.originalFilename}" 吗？删除后不可恢复。`,
      '确认删除',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await deleteFile(file.id)
    ElMessage.success('文件删除成功')
    loadFileList() // 删除后刷新列表
  } catch {
    // 用户取消删除或删除失败
  }
}

/**
 * 打开重命名对话框
 * 将当前文件名预填到输入框中
 * @param file 要重命名的文件信息
 */
function openRenameDialog(file: FileInfo) {
  renamingFile.value = file
  // 获取文件名（不含扩展名）作为默认输入值
  const lastDot = file.originalFilename.lastIndexOf('.')
  if (lastDot > 0) {
    renameNewName.value = file.originalFilename.substring(0, lastDot)
  } else {
    renameNewName.value = file.originalFilename
  }
  renameDialogVisible.value = true
}

/**
 * 处理文件重命名
 * 调用后端接口更新文件名
 */
async function handleRename() {
  if (!renamingFile.value || !renameNewName.value.trim()) {
    ElMessage.warning('文件名不能为空')
    return
  }

  try {
    // 构建新文件名：保留原始扩展名
    const originalName = renamingFile.value.originalFilename
    const lastDot = originalName.lastIndexOf('.')
    let newName = renameNewName.value.trim()
    if (lastDot > 0) {
      newName += originalName.substring(lastDot)
    }

    await renameFile(renamingFile.value.id, newName)
    ElMessage.success('重命名成功')
    renameDialogVisible.value = false
    loadFileList() // 重命名后刷新列表
  } catch {
    // 错误已在拦截器中统一处理
  }
}

// ==================== 分享功能 ====================

/**
 * 打开分享创建对话框（从文件列表调用）
 * 重置表单字段并显示自定义弹窗
 * @param file 要创建分享的文件信息
 */
function handleCreateShare(file: FileInfo) {
  shareFileId.value = file.id
  sharePassword.value = ''
  shareExpireHours.value = 24
  shareMaxDownloads.value = 0
  shareDialogVisible.value = true
}

/**
 * 打开分享创建对话框（上传完成后调用）
 * 由于上传后只知道 taskId 不知道 fileId，此处 fileId 传 0 作为占位
 * @param taskId 上传任务ID（预留，后续可改为 fileId）
 */
function showCreateShareDialog(taskId: string) {
  shareFileId.value = parseInt(taskId, 36) || 0
  sharePassword.value = ''
  shareExpireHours.value = 24
  shareMaxDownloads.value = 0
  shareDialogVisible.value = true
}

/**
 * 提交创建分享请求
 * 将弹窗中的表单数据发送到后端接口
 */
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
    // 错误已在拦截器中统一处理
  }
}

// ==================== 用户功能 ====================

/** 退出登录 */
function handleLogout() {
  userStore.logout()
  router.push('/login')
}

// ==================== 生命周期 ====================

/** 组件挂载时加载文件列表 */
onMounted(() => {
  loadFileList()
})

/** 组件卸载时清理轮询定时器 */
onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="home-container">
    <!-- 顶部导航栏 -->
    <header class="home-header">
      <div class="header-left">
        <h1>Moon 云盘</h1>
      </div>
      <div class="header-right">
        <el-button text @click="router.push('/shares')">分享管理</el-button>
        <el-dropdown>
          <span class="user-info">
            <el-avatar :size="32" icon="UserFilled" />
            <span class="username">{{ userStore.username }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>
                <span>{{ userStore.email }}</span>
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <span style="color: #f56c6c">退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="home-main">
      <!-- 文件上传区域 -->
      <div class="upload-section">
        <el-card class="upload-card">
          <template #header>
            <span>文件上传</span>
          </template>

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
              <p class="upload-title">将文件拖到此处，或<em>点击上传</em></p>
              <p class="upload-hint">支持任意文件类型，单文件最大 100MB</p>
            </div>
          </el-upload>

          <div v-if="selectedFile" class="file-info">
            <el-tag type="info" size="large">
              {{ selectedFile.name }} ({{ formatFileSize(selectedFile.size) }})
            </el-tag>
          </div>

          <el-button
            type="primary"
            :loading="uploading"
            :disabled="!selectedFile"
            class="upload-btn"
            @click="handleUpload"
          >
            {{ uploading ? '上传中...' : '开始上传' }}
          </el-button>

          <!-- 上传进度条 -->
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
            <p v-if="uploadMessage" class="progress-msg">{{ uploadMessage }}</p>
          </div>
        </el-card>
      </div>

      <!-- 文件列表区域 -->
      <div class="file-list-section">
        <el-card class="file-list-card">
          <template #header>
            <div class="file-list-header">
              <span>我的文件</span>
              <el-button text type="primary" :icon="'Refresh'" @click="loadFileList" :loading="fileListLoading">
                刷新
              </el-button>
            </div>
          </template>

          <!-- 文件列表表格 -->
          <el-table
            :data="fileList"
            v-loading="fileListLoading"
            empty-text="暂无文件，请先上传"
            style="width: 100%"
            stripe
          >
            <el-table-column prop="originalFilename" label="文件名" min-width="200">
              <template #default="{ row }">
                <span class="file-name" :title="row.originalFilename">{{ row.originalFilename }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fileSize" label="大小" width="120" align="center">
              <template #default="{ row }">
                {{ formatFileSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column prop="contentType" label="类型" width="140" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.contentType || '未知' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="uploadTime" label="上传时间" width="180" align="center">
              <template #default="{ row }">
                {{ new Date(row.uploadTime).toLocaleString() }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280" align="center" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  size="small"
                  :icon="Download"
                  link
                  @click="handleDownload(row)"
                >
                  下载
                </el-button>
                <el-button
                  type="success"
                  size="small"
                  :icon="Share"
                  link
                  @click="handleCreateShare(row)"
                >
                  分享
                </el-button>
                <el-button
                  type="warning"
                  size="small"
                  :icon="Edit"
                  link
                  @click="openRenameDialog(row)"
                >
                  重命名
                </el-button>
                <el-button
                  type="danger"
                  size="small"
                  :icon="Delete"
                  link
                  @click="handleDelete(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>
    </main>

    <!-- 重命名对话框 -->
    <el-dialog
      v-model="renameDialogVisible"
      title="重命名文件"
      width="400px"
      :close-on-click-modal="false"
    >
      <el-input
        v-model="renameNewName"
        placeholder="请输入新文件名"
        maxlength="200"
        show-word-limit
        @keyup.enter="handleRename"
      >
        <template #append>
          <span v-if="renamingFile" class="file-ext-suffix">
            {{ renamingFile.originalFilename.substring(renamingFile.originalFilename.lastIndexOf('.')) }}
          </span>
        </template>
      </el-input>
      <template #footer>
        <el-button @click="renameDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleRename">确定</el-button>
      </template>
    </el-dialog>

    <!-- 创建分享对话框 -->
    <el-dialog
      v-model="shareDialogVisible"
      title="创建分享链接"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-width="100px" label-position="left">
        <el-form-item label="提取码">
          <el-input
            v-model="sharePassword"
            placeholder="可选，留空则无需提取码"
            maxlength="20"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="有效时长">
          <el-input-number
            v-model="shareExpireHours"
            :min="1"
            :max="720"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
          <span class="form-hint">小时，默认 24 小时，最长 720 小时（30天）</span>
        </el-form-item>
        <el-form-item label="最大下载次数">
          <el-input-number
            v-model="shareMaxDownloads"
            :min="0"
            :max="9999"
            :step="1"
            controls-position="right"
            style="width: 100%"
          />
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
  min-height: 100vh;
  background: #f5f7fa;
}

/* ==================== 顶部导航栏 ==================== */
.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.header-left h1 {
  font-size: 20px;
  color: #303133;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  font-size: 14px;
  color: #303133;
}

/* ==================== 主内容区域 ==================== */
.home-main {
  padding: 24px;
  max-width: 1000px;
  margin: 0 auto;
}

/* ==================== 上传区域 ==================== */
.upload-section {
  margin-bottom: 24px;
}

.upload-card {
  border-radius: 8px;
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

.upload-hint {
  font-size: 12px;
  color: #909399;
  margin: 4px 0 0 0;
}

.file-info {
  margin: 12px 0;
}

.upload-btn {
  margin-top: 12px;
  width: 100%;
}

/* ==================== 进度条区域 ==================== */
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

/* ==================== 文件列表区域 ==================== */
.file-list-section {
  margin-top: 0;
}

.file-list-card {
  border-radius: 8px;
}

.file-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.file-name {
  display: inline-block;
  max-width: 300px;
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
</style>