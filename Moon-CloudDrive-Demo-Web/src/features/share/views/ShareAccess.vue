<script setup lang="ts">
/**
 * 分享访问页面
 * 通过分享码访问他人分享的文件/文件夹，支持提取码验证、单文件下载、文件夹打包下载
 * 下载次数仅在用户实际点击下载/打包按钮时计数
 */
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  getShareInfo,
  verifySharePassword,
  getShareDownloadUrl,
  prepareSharePackDownload,
  getSharePackProgress,
} from '../api/share'
import type { ShareInfoResponse, PackProgressResponse } from '../types/share'
import { ElMessage } from 'element-plus'
import { Document, FolderOpened, Lock, Download, Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'

const route = useRoute()
const shareCode = route.params.shareCode as string

/** 页面是否正在加载 */
const loading = ref(false)
/** 是否正在验证提取码 */
const verifying = ref(false)
/** 是否正在获取下载链接 */
const downloading = ref(false)
/** 是否正在打包中 */
const packing = ref(false)
/** 分享文件元信息 */
const shareInfo = ref<ShareInfoResponse | null>(null)
/** 用户输入的提取码 */
const password = ref('')
/** 提取码是否已验证通过 */
const verified = ref(false)
/** 打包进度信息 */
const packProgress = ref<PackProgressResponse | null>(null)
/** 打包轮询定时器 */
let packPollTimer: ReturnType<typeof setInterval> | null = null

/**
 * 格式化文件大小
 * @param bytes 文件字节数
 */
function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/**
 * 获取分享文件元信息
 * 仅获取文件名、大小等基本信息，不消耗下载次数
 */
async function fetchShareInfo() {
  loading.value = true
  try {
    const res = await getShareInfo(shareCode)
    shareInfo.value = res.data.data
    // 无密码分享直接显示下载按钮
    if (!shareInfo.value?.needPassword) {
      verified.value = true
    }
  } catch {
    shareInfo.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 验证提取码
 * 仅校验提取码是否正确，不消耗下载次数
 */
async function handleVerify() {
  if (!password.value) {
    ElMessage.warning('请输入提取码')
    return
  }
  verifying.value = true
  try {
    await verifySharePassword(shareCode, { password: password.value })
    verified.value = true
    ElMessage.success('验证成功')
  } catch {
    // 错误已在拦截器中处理
  } finally {
    verifying.value = false
  }
}

/**
 * 处理单文件下载
 * 调用下载接口获取预签名URL，此时消耗一次下载次数
 */
async function handleDownload() {
  downloading.value = true
  try {
    const res = await getShareDownloadUrl(shareCode, password.value || undefined)
    const downloadUrl = res.data.data
    // 在新窗口打开下载链接，触发浏览器下载
    window.open(downloadUrl, '_blank')
  } catch {
    // 错误已在拦截器中处理
  } finally {
    downloading.value = false
  }
}

/**
 * 处理文件夹打包下载
 * 提交打包任务 → 轮询进度 → 触发浏览器下载 ZIP
 */
async function handlePackDownload() {
  if (packing.value) return
  packing.value = true
  packProgress.value = null

  try {
    // 1. 提交打包任务
    const res = await prepareSharePackDownload(shareCode, password.value || undefined)
    const taskId = res.data.data

    // 2. 开始轮询进度
    packProgress.value = { status: 'queued', percent: 0, message: '任务已提交，等待处理...', zipFilename: null }

    packPollTimer = setInterval(async () => {
      try {
        const progressRes = await getSharePackProgress(shareCode, taskId)
        const progress = progressRes.data.data
        packProgress.value = progress

        if (progress.status === 'ready') {
          stopPackPolling()
          // 3. 触发浏览器下载
          triggerPackDownload(shareCode, taskId)
        } else if (progress.status === 'failed') {
          stopPackPolling()
          ElMessage.error(progress.message || '打包失败')
          packing.value = false
        }
      } catch {
        // 网络错误不中断轮询
      }
    }, 1000)
  } catch {
    packing.value = false
  }
}

/** 停止打包进度轮询 */
function stopPackPolling() {
  if (packPollTimer !== null) {
    clearInterval(packPollTimer)
    packPollTimer = null
  }
}

/** 触发浏览器下载 ZIP 文件 */
function triggerPackDownload(shareCode: string, taskId: string) {
  const a = document.createElement('a')
  a.href = `/share/${shareCode}/pack-download?taskId=${taskId}`
  a.download = ''
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  ElMessage.success('开始下载')

  setTimeout(() => {
    packing.value = false
  }, 2000)
}

/** 获取打包状态对应的图标颜色 */
function packStatusColor() {
  if (!packProgress.value) return '#409eff'
  switch (packProgress.value.status) {
    case 'ready':
      return '#67c23a'
    case 'failed':
      return '#f56c6c'
    default:
      return '#409eff'
  }
}

onMounted(() => {
  fetchShareInfo()
})

onUnmounted(() => {
  stopPackPolling()
})
</script>

<template>
  <div class="share-access-container">
    <div class="share-access-card">
      <div class="share-access-header">
        <h1>Moon 云盘</h1>
        <p>文件分享</p>
      </div>

      <div v-if="loading" class="loading-state">
        <el-skeleton :rows="4" animated />
      </div>

      <div v-else-if="!shareInfo" class="error-state">
        <el-result icon="error" title="链接无效" sub-title="该分享链接不存在或已过期" />
      </div>

      <div v-else class="share-content">
        <!-- 文件/文件夹信息卡片 -->
        <div class="file-info-card">
          <div class="file-icon">
            <el-icon :size="48" :color="shareInfo.isFolder ? '#e6a23c' : '#409eff'">
              <FolderOpened v-if="shareInfo.isFolder" />
              <Document v-else />
            </el-icon>
          </div>
          <div class="file-details">
            <h3>{{ shareInfo.fileName }}</h3>
            <p v-if="!shareInfo.isFolder">文件大小：{{ formatFileSize(shareInfo.fileSize) }}</p>
            <p v-else>类型：文件夹</p>
            <p>分享码：{{ shareInfo.shareCode }}</p>
          </div>
        </div>

        <!-- 提取码输入区域 -->
        <div v-if="shareInfo.needPassword && !verified" class="password-section">
          <p class="password-hint">此分享需要提取码才能下载</p>
          <div class="password-input-row">
            <el-input
              v-model="password"
              placeholder="请输入提取码"
              :prefix-icon="Lock"
              @keyup.enter="handleVerify"
            />
            <el-button type="primary" :loading="verifying" @click="handleVerify">
              验证
            </el-button>
          </div>
        </div>

        <!-- 单文件下载按钮 -->
        <div v-if="verified && !shareInfo.isFolder" class="download-section">
          <el-button
            type="success"
            size="large"
            class="download-btn"
            :loading="downloading"
            @click="handleDownload"
          >
            <el-icon style="margin-right: 4px"><Download /></el-icon>
            下载文件
          </el-button>
        </div>

        <!-- 文件夹打包下载区域 -->
        <div v-if="verified && shareInfo.isFolder" class="download-section">
          <!-- 打包进度展示 -->
          <div v-if="packing && packProgress" class="pack-progress-card">
            <div class="pack-status-row">
              <el-icon :size="24" :color="packStatusColor()">
                <Loading v-if="packProgress.status === 'queued' || packProgress.status === 'processing'" />
                <CircleCheck v-else-if="packProgress.status === 'ready'" />
                <CircleClose v-else-if="packProgress.status === 'failed'" />
              </el-icon>
              <span class="pack-status-text">{{ packProgress.message }}</span>
            </div>
            <el-progress
              v-if="packProgress.status !== 'failed'"
              :percentage="packProgress.percent"
              :status="packProgress.status === 'ready' ? 'success' : undefined"
              :stroke-width="16"
            />
            <p v-if="packProgress.status === 'ready'" class="ready-hint">
              文件较大，浏览器正在准备下载，请稍候...
            </p>
          </div>

          <!-- 打包下载按钮（未开始或失败后重试） -->
          <el-button
            v-if="!packing || packProgress?.status === 'failed'"
            type="success"
            size="large"
            class="download-btn"
            @click="handlePackDownload"
          >
            <el-icon style="margin-right: 4px"><Download /></el-icon>
            {{ packProgress?.status === 'failed' ? '打包失败，点击重试' : '打包下载' }}
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.share-access-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.share-access-card {
  width: 520px;
  padding: 40px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.share-access-header {
  text-align: center;
  margin-bottom: 32px;
}

.share-access-header h1 {
  font-size: 28px;
  color: #303133;
  margin: 0 0 8px 0;
}

.share-access-header p {
  color: #909399;
  margin: 0;
  font-size: 14px;
}

.loading-state {
  padding: 20px 0;
}

.file-info-card {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 24px;
}

.file-details h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  color: #303133;
  word-break: break-all;
}

.file-details p {
  margin: 0 0 4px 0;
  font-size: 13px;
  color: #909399;
}

.password-section {
  text-align: center;
}

.password-hint {
  color: #606266;
  font-size: 14px;
  margin-bottom: 12px;
}

.password-input-row {
  display: flex;
  gap: 12px;
}

.password-input-row .el-input {
  flex: 1;
}

.download-section {
  text-align: center;
  margin-top: 8px;
}

.download-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
}

/* 打包进度卡片 */
.pack-progress-card {
  padding: 20px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.pack-status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.pack-status-text {
  font-size: 14px;
  color: #606266;
}

.ready-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
</style>