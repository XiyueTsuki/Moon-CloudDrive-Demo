<!--
  移动文件/文件夹对话框（单个和批量通用）
  通过文件夹树选择目标位置
-->
<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    width="500px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-tree
      :load="loadNodes"
      lazy
      node-key="id"
      highlight-current
      :expand-on-click-node="false"
      @node-click="(data: any) => $emit('node-click', data)"
      style="max-height: 360px; overflow-y: auto"
    />
    <div v-if="selectedParentLabel" style="margin-top: 12px; color: #606266">
      目标文件夹：{{ selectedParentLabel }}
    </div>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="$emit('submit')">确认移动</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
const props = defineProps<{
  visible: boolean
  title?: string
  loadNodes: (node: any, resolve: (data: any[]) => void) => void
  selectedParentLabel?: string
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'node-click', data: any): void
  (e: 'submit'): void
}>()
</script>