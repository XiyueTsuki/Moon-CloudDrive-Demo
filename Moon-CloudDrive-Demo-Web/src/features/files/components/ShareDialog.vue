<!--
  分享链接对话框
  设置有效期、提取码、最大下载次数，生成分享链接（每次打开只能创建一次链接）
-->
<template>
  <el-dialog
    :model-value="visible"
    title="分享文件"
    width="480px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
    @open="onOpen"
  >
    <el-form @submit.prevent="handleSubmit">
      <el-form-item label="分享文件">
        <span>{{ fileName }}</span>
      </el-form-item>

      <el-form-item label="有效期">
        <el-select v-model="selectedDays" style="width: 100%" :disabled="linkGenerated">
          <el-option label="1天" :value="1" />
          <el-option label="7天" :value="7" />
          <el-option label="30天" :value="30" />
        </el-select>
      </el-form-item>

      <el-form-item label="提取码">
        <el-input
          v-model="password"
          placeholder="可选，留空则不设提取码"
          :disabled="linkGenerated"
          show-password
          maxlength="20"
        />
      </el-form-item>

      <el-form-item label="最大下载次数">
        <el-input-number
          v-model="maxDownloads"
          :min="0"
          :max="9999"
          :disabled="linkGenerated"
          controls-position="right"
          style="width: 100%"
        />
        <span class="form-hint">0 表示不限制下载次数</span>
      </el-form-item>

      <el-form-item v-if="shareLink" label="分享链接">
        <el-input :model-value="shareLink" readonly>
          <template #append>
            <el-button @click="copyLink">复制</el-button>
          </template>
        </el-input>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:visible', false)">关闭</el-button>
      <el-button
        type="primary"
        :disabled="linkGenerated"
        @click="handleSubmit"
      >
        生成分享链接
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

const props = defineProps<{
  visible: boolean
  fileName: string
  shareLink: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'submit', payload: { days: number; password: string; maxDownloads: number }): void
}>()

const selectedDays = ref(7)
const password = ref('')
const maxDownloads = ref(0)
const linkGenerated = ref(false)

/** 弹窗每次打开时重置所有状态 */
function onOpen() {
  selectedDays.value = 7
  password.value = ''
  maxDownloads.value = 0
  linkGenerated.value = false
}

/** 监听父组件传入的 shareLink 变化，有值说明链接已生成 */
watch(
  () => props.shareLink,
  (val) => {
    if (val) {
      linkGenerated.value = true
    }
  },
)

/** 提交生成分享链接 */
function handleSubmit() {
  if (linkGenerated.value) return
  emit('submit', {
    days: selectedDays.value,
    password: password.value.trim(),
    maxDownloads: maxDownloads.value,
  })
}

/** 复制分享链接到剪贴板 */
function copyLink() {
  if (!props.shareLink) return
  navigator.clipboard.writeText(props.shareLink).then(() => {
    ElMessage.success('已复制到剪贴板')
  })
}
</script>

<style scoped>
.form-hint {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>