<!--
  文件表格组件
  展示当前目录下的文件和文件夹列表，支持排序、多选
-->
<template>
  <el-table
    ref="tableRef"
    :data="fileList"
    v-loading="loading"
    stripe
    @sort-change="$emit('sort-change', $event)"
    @selection-change="$emit('selection-change', $event)"
    style="width: 100%"
    row-key="id"
  >
    <!-- 复选框列：仅文件可勾选，文件夹不可勾选 -->
    <el-table-column
      :selectable="(row: FileInfo) => row.isFolder !== 1"
      type="selection"
      width="48"
      :reserve-selection="true"
    />

    <!-- 名称列：支持文件夹点击进入、文件点击预览 -->
    <el-table-column label="名称" prop="originalFilename" sortable="custom" min-width="200">
      <template #default="{ row }">
        <div
          class="file-name-cell"
          :class="{ 'is-folder': row.isFolder === 1 }"
          @click="row.isFolder === 1 ? $emit('folder-click', row) : $emit('file-click', row)"
        >
          <el-icon v-if="row.isFolder === 1" style="margin-right: 6px"><Folder /></el-icon>
          <el-icon v-else style="margin-right: 6px"><Document /></el-icon>
          <span class="file-name-text">{{ row.originalFilename }}</span>
        </div>
      </template>
    </el-table-column>

    <!-- 大小列 -->
    <el-table-column label="大小" prop="fileSize" sortable="custom" width="110" align="right">
      <template #default="{ row }">{{ row.isFolder === 1 ? '-' : formatFileSize(row.fileSize) }}</template>
    </el-table-column>

    <!-- 上传时间列 -->
    <el-table-column label="上传时间" prop="uploadTime" sortable="custom" width="180" align="center">
      <template #default="{ row }">{{ formatTime(row.uploadTime) }}</template>
    </el-table-column>

    <!-- 操作列 -->
    <el-table-column label="操作" width="340" align="center" fixed="right">
      <template #default="{ row }">
        <el-button text type="primary" size="small" @click="$emit('download', row)">下载</el-button>
        <el-button text type="primary" size="small" @click="$emit('share', row)">分享</el-button>
        <el-button text type="primary" size="small" @click="$emit('rename', row)">重命名</el-button>
        <el-button text type="primary" size="small" @click="$emit('move', row)">移动</el-button>
        <el-button text type="danger" size="small" @click="$emit('delete', row)">删除</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { Folder, Document } from '@element-plus/icons-vue'
import type { FileInfo } from '../types/files'

defineProps<{
  fileList: FileInfo[]
  loading: boolean
}>()

defineEmits<{
  (e: 'sort-change', value: any): void
  (e: 'selection-change', value: any): void
  (e: 'folder-click', row: FileInfo): void
  (e: 'file-click', row: FileInfo): void
  (e: 'download', row: FileInfo): void
  (e: 'share', row: FileInfo): void
  (e: 'rename', row: FileInfo): void
  (e: 'move', row: FileInfo): void
  (e: 'delete', row: FileInfo): void
}>()

/** 格式化文件大小为人类可读的文本 */
function formatFileSize(bytes: number): string {
  if (bytes == null || bytes < 0) return '-'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let value = bytes
  let unitIndex = 0
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024
    unitIndex++
  }
  return `${value.toFixed(unitIndex === 0 ? 0 : 2)} ${units[unitIndex]}`
}

/** 格式化时间字符串 */
function formatTime(time: string): string {
  if (!time) return '-'
  return new Date(time).toLocaleString()
}
</script>

<style scoped>
.file-name-cell {
  display: flex;
  align-items: center;
  cursor: pointer;
}
.file-name-cell.is-folder:hover {
  color: var(--el-color-primary);
}
.file-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>