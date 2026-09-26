<!--
  新建文件夹对话框
  文件夹名称通过 v-model:name 与父组件双向绑定
-->
<template>
  <el-dialog
    :model-value="visible"
    title="新建文件夹"
    width="400px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
    @opened="handleOpened"
  >
    <el-form @submit.prevent="$emit('submit')">
      <el-form-item label="文件夹名称">
        <el-input
          ref="inputRef"
          :model-value="name"
          @update:model-value="$emit('update:name', $event)"
          placeholder="请输入文件夹名称"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="$emit('submit')" :disabled="!name.trim()">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'

defineProps<{
  visible: boolean
  name: string
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'update:name', value: string): void
  (e: 'submit'): void
}>()

const inputRef = ref<any>(null)

async function handleOpened() {
  await nextTick()
  inputRef.value?.focus()
}
</script>