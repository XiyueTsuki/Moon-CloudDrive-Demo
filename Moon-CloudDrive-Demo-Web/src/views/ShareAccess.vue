<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { getShareInfo, verifySharePassword } from '@/api/share'
import type { ShareInfoResponse } from '@/types/api'
import { ElMessage } from 'element-plus'

const route = useRoute()
const shareCode = route.params.shareCode as string

const loading = ref(false)
const verifying = ref(false)
const shareInfo = ref<ShareInfoResponse | null>(null)
const password = ref('')
const downloadUrl = ref('')

function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

async function fetchShareInfo() {
  loading.value = true
  try {
    const res = await getShareInfo(shareCode)
    shareInfo.value = res.data.data
    if (!shareInfo.value.needPassword && shareInfo.value.downloadUrl) {
      downloadUrl.value = shareInfo.value.downloadUrl
    }
  } catch {
    shareInfo.value = null
  } finally {
    loading.value = false
  }
}

async function handleVerify() {
  if (!password.value) {
    ElMessage.warning('请输入提取码')
    return
  }
  verifying.value = true
  try {
    const res = await verifySharePassword(shareCode, { password: password.value })
    downloadUrl.value = res.data.data
    ElMessage.success('验证成功')
  } catch {
    // 错误已在拦截器中处理
  } finally {
    verifying.value = false
  }
}

function handleDownload() {
  if (downloadUrl.value) {
    window.open(downloadUrl.value, '_blank')
  }
}

onMounted(() => {
  fetchShareInfo()
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
        <div class="file-info-card">
          <div class="file-icon">
            <el-icon :size="48" color="#409eff">
              <Document />
            </el-icon>
          </div>
          <div class="file-details">
            <h3>{{ shareInfo.fileName }}</h3>
            <p>文件大小：{{ formatFileSize(shareInfo.fileSize) }}</p>
            <p>分享码：{{ shareInfo.shareCode }}</p>
          </div>
        </div>

        <div v-if="shareInfo.needPassword && !downloadUrl" class="password-section">
          <p class="password-hint">此文件需要提取码才能下载</p>
          <div class="password-input-row">
            <el-input
              v-model="password"
              placeholder="请输入提取码"
              :prefix-icon="'Lock'"
              @keyup.enter="handleVerify"
            />
            <el-button
              type="primary"
              :loading="verifying"
              @click="handleVerify"
            >
              验证
            </el-button>
          </div>
        </div>

        <div v-if="downloadUrl" class="download-section">
          <el-button
            type="success"
            size="large"
            class="download-btn"
            @click="handleDownload"
          >
            下载文件
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
  width: 500px;
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
</style>