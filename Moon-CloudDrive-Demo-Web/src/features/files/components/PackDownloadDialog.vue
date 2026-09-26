<!--
  打包下载进度对话框
  展示打包任务的实时进度和最终下载状态
-->
<template>
  <el-dialog
    :model-value="visible"
    title="打包下载"
    width="420px"
    :close-on-click-modal="false"
    :show-close="status !== 'running'"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div style="text-align: center; padding: 20px">
      <!-- 打包中 -->
      <template v-if="status === 'queued' || status === 'running'">
        <el-icon class="is-loading" :size="40" color="var(--el-color-primary)">
          <Loading />
        </el-icon>
      </template>
      <!-- 成功 -->
      <template v-else-if="status === 'ready'">
        <el-icon :size="40" color="#67c23a">
          <CircleCheck />
        </el-icon>
      </template>
      <!-- 失败 -->
      <template v-else-if="status === 'failed'">
        <el-icon :size="40" color="#f56c6c">
          <CircleClose />
        </el-icon>
      </template>

      <!-- 进度条 -->
      <el-progress
        v-if="status === 'queued' || status === 'running'"
        :percentage="percent"
        :status="status as any === 'failed' ? 'exception' : undefined"
        style="margin: 16px 0"
      />

      <!-- 提示信息 -->
      <p style="color: #606266; margin-top: 12px">{{ message }}</p>
    </div>

    <template #footer>
      <el-button
        v-if="status === 'ready' || status === 'failed'"
        @click="$emit('update:visible', false)"
      >关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { Loading, CircleCheck, CircleClose } from '@element-plus/icons-vue'

defineProps<{
  visible: boolean
  status: string
  percent: number
  message: string
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
}>()
</script>