<script setup lang="ts">
/**
 * 回收站页面
 * 展示当前用户已删除的文件，支持恢复和彻底删除操作
 * 文件在回收站中保留 30 天后将被系统自动清理
 */
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getRecycleBinList, restoreFile, permanentDeleteFile } from '@/api/file'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, RefreshLeft } from '@element-plus/icons-vue'
import type { FileInfo } from '@/types/api'

const router = useRouter()

/** 回收站文件列表 */
const fileList = ref<FileInfo[]>([])
/** 表格是否正在加载 */
const loading = ref(false)

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
 * 计算文件在回收站中剩余天数
 * 返回一个对象，包含剩余天数和是否已过期
 * @param deleteTime 进入回收站的时间
 */
function getRemainingDays(deleteTime: string): { days: number; expired: boolean } {
  const deleted = new Date(deleteTime).getTime()
  const now = Date.now()
  const elapsedDays = (now - deleted) / (1000 * 60 * 60 * 24)
  const remaining = 30 - elapsedDays
  if (remaining <= 0) {
    return { days: 0, expired: true }
  }
  return { days: Math.ceil(remaining), expired: false }
}

/** 加载回收站文件列表 */
async function loadRecycleBin() {
  loading.value = true
  try {
    const res = await getRecycleBinList()
    fileList.value = res.data.data
  } catch {
    // 错误已在拦截器中统一处理
  } finally {
    loading.value = false
  }
}

/**
 * 从回收站恢复文件
 * 恢复后文件将重新出现在"我的文件"列表中
 * @param file 要恢复的文件
 */
async function handleRestore(file: FileInfo) {
  try {
    await restoreFile(file.id)
    ElMessage.success(`文件 "${file.originalFilename}" 已恢复`)
    loadRecycleBin()
  } catch {
    // 错误已在拦截器中统一处理
  }
}

/**
 * 彻底删除文件
 * 从数据库和 OSS 中物理删除，不可恢复
 * @param file 要彻底删除的文件
 */
async function handlePermanentDelete(file: FileInfo) {
  try {
    await ElMessageBox.confirm(
      `确定要彻底删除文件 "${file.originalFilename}" 吗？此操作不可恢复，文件将从服务器上永久删除。`,
      '彻底删除',
      {
        confirmButtonText: '彻底删除',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await permanentDeleteFile(file.id)
    ElMessage.success('文件已彻底删除')
    loadRecycleBin()
  } catch {
    // 用户取消或删除失败
  }
}

/** 组件挂载时加载回收站数据 */
onMounted(() => {
  loadRecycleBin()
})
</script>

<template>
  <div class="recycle-bin-container">
    <!-- 顶部导航栏 -->
    <header class="rb-header">
      <div class="header-left">
        <el-button text @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon>
          返回首页
        </el-button>
        <h1>回收站</h1>
        <span class="header-hint">（文件删除后将在此保留 30 天，过期后自动彻底删除）</span>
      </div>
      <div class="header-right">
        <el-button :icon="RefreshLeft" @click="loadRecycleBin" :loading="loading">
          刷新
        </el-button>
      </div>
    </header>

    <!-- 回收站文件表格 -->
    <main class="rb-main">
      <el-table
        :data="fileList"
        v-loading="loading"
        empty-text="回收站暂无可恢复文件"
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
        <!-- 删除时间列，展示文件进入回收站的时间 -->
        <el-table-column prop="deleteTime" label="删除时间" width="180" align="center">
          <template #default="{ row }">
            {{ new Date(row.deleteTime).toLocaleString() }}
          </template>
        </el-table-column>
        <!-- 剩余天数列，用颜色标识是否即将过期 -->
        <el-table-column label="剩余天数" width="120" align="center">
          <template #default="{ row }">
            <template v-if="row.deleteTime">
              <el-tag
                :type="getRemainingDays(row.deleteTime).expired ? 'danger' : getRemainingDays(row.deleteTime).days <= 3 ? 'warning' : 'info'"
                size="small"
              >
                {{ getRemainingDays(row.deleteTime).expired ? '已过期' : getRemainingDays(row.deleteTime).days + ' 天' }}
              </el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              :icon="RefreshLeft"
              link
              @click="handleRestore(row)"
            >
              恢复
            </el-button>
            <el-button
              type="danger"
              size="small"
              :icon="Delete"
              link
              @click="handlePermanentDelete(row)"
            >
              彻底删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </main>
  </div>
</template>

<style scoped>
.recycle-bin-container {
  min-height: 100vh;
  background: #f5f7fa;
}

/* ==================== 顶部导航栏 ==================== */
.rb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h1 {
  font-size: 20px;
  color: #303133;
  margin: 0;
}

.header-hint {
  font-size: 13px;
  color: #909399;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ==================== 主内容区域 ==================== */
.rb-main {
  padding: 24px;
  max-width: 1100px;
  margin: 0 auto;
}

.file-name {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>