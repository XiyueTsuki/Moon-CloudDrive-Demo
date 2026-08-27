<script setup lang="ts">
import { ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { uploadFile, getProgress } from '@/api/file'
import { createShare } from '@/api/share'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const uploading = ref(false)
const uploadPercent = ref(0)
const uploadStatus = ref('')
const uploadMessage = ref('')
const currentTaskId = ref('')
const selectedFile = ref<File | null>(null)
let pollTimer: ReturnType<typeof setInterval> | null = null

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
        ElMessage.success('文件上传完成！')
        uploading.value = false

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
          // 暂不创建
        })
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

async function showCreateShareDialog(taskId: string) {
  ElMessageBox.prompt('请输入分享提取码（可选，留空则无需提取码）', '创建分享链接', {
    confirmButtonText: '创建',
    cancelButtonText: '取消',
    inputPlaceholder: '请输入提取码',
  }).then(async ({ value }) => {
    try {
      await createShare({
        fileId: parseInt(taskId, 36) || 0,
        password: value || undefined,
      })
      ElMessage.success('分享链接创建成功')
    } catch {
      ElMessage.warning('请前往分享管理页面手动创建分享')
    }
  }).catch(() => {
    // 取消
  })
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

onUnmounted(() => {
  stopPolling()
})
</script>

<template>
  <div class="home-container">
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
    </main>
  </div>
</template>

<style scoped>
.home-container {
  min-height: 100vh;
  background: #f5f7fa;
}

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

.home-main {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
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
  margin-top: 16px;
  text-align: center;
}

.upload-btn {
  margin-top: 16px;
  width: 100%;
  height: 44px;
  font-size: 16px;
}

.progress-section {
  margin-top: 20px;
  padding: 16px;
  background: #fafafa;
  border-radius: 8px;
}

.progress-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  font-size: 14px;
  color: #606266;
}

.progress-msg {
  margin: 8px 0 0 0;
  font-size: 12px;
  color: #909399;
  text-align: center;
}
</style>