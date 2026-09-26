<!--
  工具栏组件
  提供新建文件夹、上传、搜索以及批量操作按钮
-->
<template>
  <div class="file-toolbar">
    <div class="toolbar-left">
      <el-button type="primary" @click="$emit('new-folder')">新建文件夹</el-button>
      <el-button type="primary" @click="$emit('upload')">
        <el-icon style="margin-right: 4px"><Upload /></el-icon>上传文件
      </el-button>

      <!-- 批量操作按钮 -->
      <template v-if="hasSelection">
        <el-divider direction="vertical" />
        <el-button type="warning" @click="$emit('batch-delete')">批量删除</el-button>
        <el-button type="primary" @click="$emit('batch-move')">批量移动</el-button>
        <el-button type="primary" @click="$emit('batch-rename')">批量重命名</el-button>
        <el-button type="primary" @click="$emit('batch-download')">打包下载</el-button>
      </template>
    </div>

    <div class="toolbar-right">
      <el-input
        v-model="keyword"
        placeholder="搜索文件..."
        clearable
        @clear="$emit('search', '')"
        @keyup.enter="$emit('search', keyword)"
        style="width: 240px"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button type="primary" style="margin-left: 8px" @click="$emit('search', keyword)">搜索</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { Upload, Search } from '@element-plus/icons-vue'

defineProps<{
  hasSelection: boolean
}>()

defineEmits<{
  (e: 'new-folder'): void
  (e: 'upload'): void
  (e: 'batch-delete'): void
  (e: 'batch-move'): void
  (e: 'batch-rename'): void
  (e: 'batch-download'): void
  (e: 'search', keyword: string): void
}>()

const keyword = ref('')
</script>

<style scoped>
.file-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  flex-wrap: wrap;
  gap: 8px;
}
.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>