<!--
  批量重命名对话框
  支持序列化命名
-->
<template>
  <el-dialog
    :model-value="visible"
    title="批量重命名"
    width="450px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
  >
    <el-form @submit.prevent="$emit('submit')">
      <el-form-item label="命名模式">
        <el-radio-group v-model="renameMode">
          <el-radio value="sequence">序列化命名</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="命名模板">
        <el-input
          v-model="renameValue"
          placeholder="如：photo，将产生 photo_1、photo_2..."
          maxlength="200"
        />
        <span style="color: #909399; font-size: 12px">
          将以「{{ renameValue || '(模板名称)' }}_1」的格式依次命名
        </span>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">取消</el-button>
      <el-button type="primary" @click="$emit('submit')" :disabled="!renameValue.trim()">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'

defineProps<{
  visible: boolean
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit'): void
}>()

const renameMode = ref('sequence')
const renameValue = ref('')
</script>