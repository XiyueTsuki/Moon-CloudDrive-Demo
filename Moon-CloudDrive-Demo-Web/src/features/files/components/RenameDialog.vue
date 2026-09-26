<!--
  重命名对话框
  支持单独重命名文件和文件夹，文件夹直接输入名称，文件自动附加扩展名
-->
<template>
  <el-dialog
    :model-value="visible"
    title="重命名"
    width="420px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
    @opened="handleOpened"
  >
    <el-form @submit.prevent="$emit('submit')">
      <el-form-item label="新名称">
        <el-input
          ref="inputRef"
          v-model="name"
          :placeholder="'请输入新名称'"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
      <template v-if="file?.isFolder !== 1">
        <span style="color: #909399; font-size: 13px">
          原扩展名：{{ fileExtension }}
        </span>
      </template>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="$emit('submit')" :disabled="!name.trim()">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import type { FileInfo } from '../types/files'

const props = defineProps<{
  visible: boolean
  file: FileInfo | null
  modelValue: string
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'update:modelValue', value: string): void
  (e: 'submit'): void
}>()

const inputRef = ref<any>(null)
const name = computed({
  get: () => props.modelValue,
  // handled via update:modelValue emitted by the input
  set: () => void 0,
})

/** 文件扩展名（不含点号） */
const fileExtension = computed(() => {
  if (!props.file) return ''
  const lastDot = props.file.originalFilename.lastIndexOf('.')
  return lastDot > 0 ? props.file.originalFilename.slice(lastDot) : ''
})

/** 弹窗打开后自动聚焦输入框 */
async function handleOpened() {
  await nextTick()
  inputRef.value?.focus()
}
</script>