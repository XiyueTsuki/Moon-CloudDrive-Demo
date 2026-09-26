<!--
  文件预览对话框
  根据后端返回的 previewType 分流渲染：
  - image：显示图片
  - video/audio：播放音视频（含字幕）
  - pdf：以图片形式渲染 PDF 每一页
  - text：用 highlight.js 语法高亮代码
  - other：显示不支持在线预览提示
  支持复制文本类型文件的内容到剪贴板
-->
<template>
  <el-dialog
    :model-value="visible"
    :title="'预览 — ' + (file?.originalFilename ?? '')"
    top="2vh"
    width="80%"
    destroy-on-close
    @update:model-value="$emit('update:visible', $event)"
  >
    <div v-loading="loading">

      <!-- 图片预览 -->
      <template v-if="info?.previewType === 'image'">
        <div style="text-align: center; background: #f5f5f5; border-radius: 4px; min-height: 300px">
          <el-image
            :src="info.previewUrl ?? undefined"
            fit="contain"
            style="max-height: 80vh; max-width: 100%"
            :preview-src-list="[info.previewUrl ?? '']"
            :hide-on-click-modal="true"
            loading="lazy"
          />
        </div>
      </template>

      <!-- 音视频预览 -->
      <template v-else-if="info?.previewType === 'video' || info?.previewType === 'audio'">
        <div style="background: #000; border-radius: 4px; min-height: 200px">
          <video
            v-if="info?.previewType === 'video'"
            :src="info.previewUrl ?? undefined"
            controls
            style="width: 100%; max-height: 70vh; outline: none; display: block"
          >
            您的浏览器不支持 video 标签
          </video>
          <audio
            v-else
            :src="info.previewUrl ?? undefined"
            controls
            style="width: 100%; margin: 80px 0; outline: none"
          >
            您的浏览器不支持 audio 标签
          </audio>
        </div>
      </template>

      <!-- PDF 预览（服务端转图片模式） -->
      <template v-else-if="info?.previewType === 'pdf' || info?.previewType === 'pdf_image'">
        <div v-if="pdfError" class="preview-error">
          <el-result icon="error" :sub-title="pdfError" />
        </div>
        <div v-else class="pdf-preview-wrap">
          <!-- PDF 转换中 -->
          <template v-if="pdfInfo?.status === 'converting'">
            <div class="pdf-converting">
              <el-icon class="is-loading" :size="32"><Loading /></el-icon>
              <p>PDF 正在转换中，请稍候…</p>
            </div>
          </template>
          <!-- PDF 转换失败 -->
          <template v-else-if="pdfInfo?.status === 'failed'">
            <el-result icon="error" :sub-title="pdfInfo?.errorMessage || 'PDF 转换失败'" />
          </template>
          <!-- PDF 转换完成，分页展示图片 -->
          <template v-else-if="pdfInfo?.status === 'ready'">
            <div
              v-for="(url, idx) in pdfPageUrls"
              :key="idx"
              class="pdf-page"
            >
              <el-image
                :src="url"
                fit="contain"
                style="width: 100%; display: block"
                :preview-src-list="pdfPageUrls"
                :initial-index="idx"
                loading="lazy"
              />
              <p class="pdf-page-num">{{ idx + 1 }} / {{ pdfInfo?.totalPages }}</p>
            </div>
          </template>
        </div>
      </template>

      <!-- 文本/代码预览 -->
      <template v-else-if="info?.previewType === 'text'">
        <div v-if="errorMsg" class="preview-error">
          <el-result icon="error" :sub-title="errorMsg" />
        </div>
        <div v-else class="preview-text-wrap">
          <div class="preview-text-toolbar">
            <span style="font-size: 14px; color: #606266">
              语言: {{ info?.language || '未知' }} | 行数: {{ lineCount }}
            </span>
            <el-button size="small" @click="$emit('copy')">复制内容</el-button>
          </div>
          <pre class="preview-code-block"><code v-html="htmlContent" /></pre>
        </div>
      </template>

      <!-- 不支持预览 -->
      <template v-else>
        <div class="preview-error">
          <el-result icon="info" title="此文件不支持在线预览" sub-title="请下载后查看" />
        </div>
      </template>

      <!-- 加载错误 -->
      <template v-if="errorMsg && !info?.previewType">
        <div class="preview-error">
          <el-result icon="error" :sub-title="errorMsg" />
        </div>
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { FileInfo } from '../types/files'
import type { PreviewInfo, PdfPreviewInfo } from '@/features/preview/types/preview'

const props = defineProps<{
  visible: boolean
  file: FileInfo | null
  loading: boolean
  info: PreviewInfo | null
  textContent: string
  htmlContent: string
  errorMsg: string
  pdfInfo?: PdfPreviewInfo | null
  pdfPageUrls?: string[]
  pdfError?: string
}>()

defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'copy'): void
}>()

/** 文本行数 */
const lineCount = computed(() => (props.textContent ? props.textContent.split('\n').length : 0))
</script>

<style scoped>
.preview-error {
  padding: 40px 0;
}
.preview-text-wrap {
  max-height: 75vh;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.preview-text-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.preview-code-block {
  max-height: 70vh;
  overflow: auto;
  background: #f6f8fa;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 16px;
  font-size: 14px;
  line-height: 1.5;
  margin: 0;
}
.pdf-preview-wrap {
  max-height: 70vh;
  overflow-y: auto;
}
.pdf-page {
  margin-bottom: 12px;
  text-align: center;
}
.pdf-page-num {
  margin: 6px 0 0;
  font-size: 13px;
  color: #909399;
}
.pdf-converting {
  text-align: center;
  padding: 60px 0;
  color: #606266;
}
</style>