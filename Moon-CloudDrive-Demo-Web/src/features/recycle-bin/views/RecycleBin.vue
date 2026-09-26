<script setup lang="ts">
/**
 * 回收站页面
 * 展示当前用户已删除的文件，支持恢复和彻底删除操作
 * 文件在回收站中保留 30 天后将被系统自动清理
 */
import { ref, onMounted } from 'vue'
import { getRecycleBinList, restoreFile, permanentDeleteFile } from '../api/recycleBin'
import type { FileInfo } from '@/features/files/types/files'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, RefreshLeft } from '@element-plus/icons-vue'

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
 * 从回收站恢复文件或文件夹。
 * 文件夹会递归恢复其下所有子孙节点
 * @param file 要恢复的文件/文件夹
 */
async function handleRestore(file: FileInfo) {
  const label = file.isFolder === 1 ? '文件夹' : '文件'
  try {
    await restoreFile(file.id)
    ElMessage.success(`${label}「${file.originalFilename}」已恢复`)
    loadRecycleBin()
  } catch {
    // 统一拦截处理
  }
}

/**
 * 彻底删除文件或文件夹。
 * 文件夹会递归彻底删除其下所有子孙节点（含 OSS 删除）
 * @param file 要彻底删除的文件/文件夹
 */
async function handlePermanentDelete(file: FileInfo) {
  const label = file.isFolder === 1 ? '文件夹' : '文件'
  const confirmMsg = file.isFolder === 1
    ? `确定要彻底删除文件夹「${file.originalFilename}」吗？其中的所有文件和子文件夹将被永久删除，不可恢复。`
    : `确定要彻底删除文件「${file.originalFilename}」吗？此操作不可恢复，文件将从服务器上永久删除。`

  try {
    await ElMessageBox.confirm(confirmMsg, `彻底删除${label}`, {
      confirmButtonText: '彻底删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await permanentDeleteFile(file.id)
    ElMessage.success(`${label}已彻底删除`)
    loadRecycleBin()
  } catch {
    // 取消或不处理
  }
}

/** 组件挂载时加载回收站数据 */
onMounted(() => {
  loadRecycleBin()
})
</script>

<template>
  <div class="recycle-bin-container">
    <div class="rb-toolbar">
      <div class="toolbar-title">
        <h1>回收站</h1>
        <span class="header-hint">（文件删除后将在此保留 30 天，过期后自动彻底删除）</span>
      </div>
      <el-button :icon="RefreshLeft" @click="loadRecycleBin" :loading="loading">
        刷新
      </el-button>
    </div>

    <!-- 回收站文件表格 -->
    <main class="rb-main">
      <el-table
        :data="fileList"
        v-loading="loading"
        empty-text="回收站暂无可恢复文件"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="originalFilename" label="文件名" min-width="220">
          <template #default="{ row }">
            <div class="name-cell">
              <el-icon v-if="row.isFolder === 1" color="#e6a23c" :size="18">
                <Folder />
              </el-icon>
              <span class="file-name" :title="row.originalFilename">{{ row.originalFilename }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="fileSize" label="大小" width="120" align="center">
          <template #default="{ row }">
            {{ row.isFolder === 1 ? '-' : formatFileSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column prop="contentType" label="类型" width="140" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isFolder === 1 ? 'warning' : 'info'">
              {{ row.isFolder === 1 ? '文件夹' : (row.contentType || '未知') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deleteTime" label="删除时间" width="180" align="center">
          <template #default="{ row }">
            {{ new Date(row.deleteTime).toLocaleString() }}
          </template>
        </el-table-column>
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
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.rb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-title h1 {
  font-size: 20px;
  color: #303133;
  margin: 0;
}

.header-hint {
  font-size: 13px;
  color: #909399;
}

.rb-main {}

.file-name {
  display: inline-block;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>