<script setup lang="ts">
/**
 * 上传任务面板组件
 * 固定在右下角的浮层面板，展示所有上传任务的实时状态
 * 支持暂停/续传/取消/重试操作，可折叠/展开
 */
import { ref, computed, watch, onMounted } from 'vue'
import { useUploadStore } from '../stores/upload'
import { RefreshFileListEvent } from '@/features/files/events/files'
import type { UploadTask, UploadTaskStatus } from '../types/upload'

const store = useUploadStore()

/** 面板折叠状态 */
const collapsed = ref(false)

/** 面板可见性（无任务时自动隐藏） */
const visible = computed(() => store.tasks.length > 0)

/** 任务列表（最新在顶部） */
const taskList = computed(() => [...store.tasks].reverse())

/**
 * 当有任务完成时，通知文件列表刷新
 */
watch(
  () => store.tasks.map((t) => ({ id: t.id, status: t.status, completedAt: t.completedAt })),
  () => {
    const justDone = store.tasks.some(
      (t) => t.status === 'done' && t.completedAt && Date.now() - t.completedAt < 3000,
    )
    if (justDone) {
      window.dispatchEvent(new CustomEvent(RefreshFileListEvent))
    }
  },
  { deep: true },
)

/** 页面加载时检查未完成任务 */
onMounted(() => {
  const snapshots = store.restoreSnapshots()
  if (snapshots.length > 0) {
    collapsed.value = false
  }
})

// ==================== 格式化 ====================

/** 格式化文件大小 */
function formatSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/** 状态标签类型映射 */
function statusTagType(status: UploadTaskStatus): string {
  const map: Record<string, string> = {
    pending: 'info',
    hashing: 'warning',
    initializing: 'warning',
    uploading: '',
    paused: 'warning',
    completing: 'warning',
    done: 'success',
    failed: 'danger',
    cancelled: 'info',
  }
  return map[status] || 'info'
}

/** 状态中文描述 */
function statusLabel(status: UploadTaskStatus): string {
  const map: Record<string, string> = {
    pending: '排队中',
    hashing: '计算哈希',
    initializing: '初始化',
    uploading: '上传中',
    paused: '已暂停',
    completing: '合并中',
    done: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return map[status] || status
}

/** 进度条状态 */
function progressStatus(task: UploadTask): 'success' | 'exception' | '' {
  if (task.status === 'done') return 'success'
  if (task.status === 'failed') return 'exception'
  return ''
}

// ==================== 操作处理 ====================

function handlePause(task: UploadTask): void {
  store.pauseTask(task.id)
}

function handleResume(task: UploadTask): void {
  store.resumeTask(task.id)
}

function handleCancel(task: UploadTask): void {
  store.cancelTask(task.id)
}

function handleRetry(task: UploadTask): void {
  store.retryTask(task.id)
}

function handleRemove(task: UploadTask): void {
  store.removeTask(task.id)
}

function handleClearCompleted(): void {
  store.clearCompleted()
}
</script>

<template>
  <div v-if="visible" class="upload-panel" :class="{ collapsed }">
    <!-- 面板标题栏 -->
    <div class="panel-header" @click="collapsed = !collapsed">
      <span class="panel-title">
        上传任务 ({{ store.tasks.filter((t) => t.status !== 'cancelled').length }})
      </span>
      <span class="panel-toggle">{{ collapsed ? '展开' : '收起' }}</span>
    </div>

    <!-- 任务列表 -->
    <div v-if="!collapsed" class="panel-body">
      <div
        v-for="task in taskList"
        :key="task.id"
        class="task-item"
        :class="{ 'is-done': task.status === 'done' }"
      >
        <!-- 文件名 + 状态标签 -->
        <div class="task-header">
          <span class="task-name" :title="task.fileName">{{ task.fileName }}</span>
          <el-tag :type="statusTagType(task.status)" size="small">
            {{ statusLabel(task.status) }}
          </el-tag>
        </div>

        <!-- 文件大小 -->
        <div class="task-meta">
          {{ formatSize(task.fileSize) }}
        </div>

        <!-- 进度条 -->
        <div v-if="task.status !== 'cancelled'" class="task-progress">
          <el-progress
            :percentage="task.progress"
            :status="progressStatus(task)"
            :stroke-width="6"
            :show-text="task.status !== 'done'"
          />
        </div>

        <!-- 状态消息 -->
        <p v-if="task.message" class="task-message" :class="{ 'is-error': task.status === 'failed' }">
          {{ task.message }}
        </p>

        <!-- 操作按钮 -->
        <div class="task-actions">
          <el-button
            v-if="['hashing', 'initializing', 'uploading', 'completing'].includes(task.status)"
            size="small"
            type="warning"
            text
            @click="handlePause(task)"
          >
            暂停
          </el-button>
          <el-button
            v-if="task.status === 'paused'"
            size="small"
            type="primary"
            text
            @click="handleResume(task)"
          >
            继续
          </el-button>
          <el-button
            v-if="task.status === 'failed'"
            size="small"
            type="warning"
            text
            @click="handleRetry(task)"
          >
            重试
          </el-button>
          <el-button
            v-if="!['done', 'failed', 'cancelled'].includes(task.status)"
            size="small"
            type="danger"
            text
            @click="handleCancel(task)"
          >
            取消
          </el-button>
          <el-button
            v-if="['done', 'failed', 'cancelled'].includes(task.status)"
            size="small"
            type="info"
            text
            @click="handleRemove(task)"
          >
            删除
          </el-button>
        </div>
      </div>

      <!-- 空状态 -->
      <div v-if="taskList.length === 0" class="panel-empty">
        暂无上传任务
      </div>

      <!-- 底部操作栏 -->
      <div v-if="taskList.length > 0" class="panel-footer">
        <el-button size="small" type="primary" text @click="handleClearCompleted">
          清空已完成
        </el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.upload-panel {
  position: fixed;
  right: 16px;
  bottom: 16px;
  width: 360px;
  max-height: 480px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.12);
  display: flex;
  flex-direction: column;
  z-index: 1000;
  overflow: hidden;
  transition: all 0.2s ease;
}

.upload-panel.collapsed {
  max-height: none;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #f5f7fa;
  cursor: pointer;
  user-select: none;
  border-bottom: 1px solid #ebeef5;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.panel-toggle {
  font-size: 12px;
  color: #909399;
}

.panel-body {
  overflow-y: auto;
  flex: 1;
  max-height: 400px;
}

.task-item {
  padding: 10px 14px;
  border-bottom: 1px solid #f2f3f5;
  transition: background 0.15s;
}

.task-item:hover {
  background: #f9fafb;
}

.task-item.is-done {
  opacity: 0.75;
}

.task-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.task-name {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}

.task-meta {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.task-progress {
  margin-bottom: 4px;
}

.task-message {
  font-size: 12px;
  color: #909399;
  margin: 4px 0 6px;
  line-height: 1.4;
}

.task-message.is-error {
  color: #f56c6c;
}

.task-actions {
  display: flex;
  gap: 4px;
}

.panel-empty {
  padding: 32px 14px;
  text-align: center;
  font-size: 13px;
  color: #c0c4cc;
}

.panel-footer {
  padding: 8px 14px;
  border-top: 1px solid #ebeef5;
  text-align: right;
}
</style>