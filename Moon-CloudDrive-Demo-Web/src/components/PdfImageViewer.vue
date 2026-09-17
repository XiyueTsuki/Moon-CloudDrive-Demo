<template>
  <div class="pdf-image-viewer" v-loading="loading">
    <div v-if="isConverting" class="pdf-converting">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>正在转换 PDF，请稍候...</p>
      <p class="pdf-converting-hint">首次预览需要将 PDF 转为图片，大约需要几秒到几十秒</p>
    </div>

    <div v-else-if="error" class="pdf-error">
      <el-result icon="error" :title="error" />
    </div>

    <template v-else-if="totalPages > 0">
      <div class="pdf-toolbar">
        <div class="pdf-toolbar-section">
          <el-button
            size="small"
            :icon="ArrowLeft"
            :disabled="currentPage <= 1"
            @click="goToPage(currentPage - 1)"
          />
          <el-input-number
            v-model="currentPage"
            :min="1"
            :max="totalPages"
            controls-position="right"
            size="small"
            class="pdf-page-input"
            @change="goToPage"
          />
          <span class="pdf-toolbar-label">/ {{ totalPages }}</span>
          <el-button
            size="small"
            :icon="ArrowRight"
            :disabled="currentPage >= totalPages"
            @click="goToPage(currentPage + 1)"
          />
        </div>

        <div class="pdf-toolbar-divider" />

        <div class="pdf-toolbar-section">
          <el-button
            size="small"
            :icon="ZoomOut"
            :disabled="scale <= 0.5"
            @click="zoomOut"
          />
          <span class="pdf-toolbar-label pdf-zoom-label">{{ Math.round(scale * 100) }}%</span>
          <el-button
            size="small"
            :icon="ZoomIn"
            :disabled="scale >= 3.0"
            @click="zoomIn"
          />
        </div>

        <span class="pdf-file-name">{{ fileName }}</span>
      </div>

      <div ref="scrollContainer" class="pdf-scroll-container" @scroll="onScroll">
        <div
          v-for="page in totalPages"
          :key="page"
          :ref="el => onPageRefChange(page, el as HTMLElement | null)"
          class="pdf-page-item"
        >
          <img
            v-if="loadedPages.has(page)"
            :src="getPageSrc(page)"
            :style="{ width: scale * 100 + '%' }"
            class="pdf-page-img"
            alt=""
          />
          <div v-else class="pdf-page-placeholder">
            <el-icon class="is-loading"><Loading /></el-icon>
          </div>
          <span class="pdf-page-label">{{ page }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ArrowLeft, ArrowRight, ZoomIn, ZoomOut, Loading } from '@element-plus/icons-vue'
import { getPdfPreview, getPdfPageUrl } from '@/api/file'

const props = defineProps<{
  fileId: number
  fileName?: string
}>()

const loading = ref(true)
const isConverting = ref(false)
const error = ref('')
const totalPages = ref(0)
const currentPage = ref(1)
const scale = ref(1.0)
const scrollContainer = ref<HTMLElement>()

const loadedPages = ref(new Set<number>())
const pageRefs = new Map<number, HTMLElement>()

let pollTimer: ReturnType<typeof setInterval> | null = null
let scrollTimer: ReturnType<typeof setTimeout> | null = null

function onPageRefChange(page: number, el: HTMLElement | null) {
  if (el) {
    pageRefs.set(page, el)
  } else {
    pageRefs.delete(page)
  }
}

function getPageSrc(pageNum: number): string {
  return getPdfPageUrl(props.fileId, pageNum)
}

async function loadPdfPreview() {
  try {
    const res = await getPdfPreview(props.fileId)
    const data = res.data.data

    if (data.status === 'ready') {
      totalPages.value = data.totalPages
      loadedPages.value.add(1)
      loading.value = false
    } else if (data.status === 'converting') {
      isConverting.value = true
      loading.value = false
      startPolling()
    } else if (data.status === 'failed') {
      error.value = data.errorMessage || 'PDF 转换失败，请稍后重试'
      loading.value = false
    }
  } catch (e: any) {
    error.value = e?.response?.data?.msg || '获取预览信息失败'
    loading.value = false
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await getPdfPreview(props.fileId)
      const data = res.data.data

      if (data.status === 'ready') {
        totalPages.value = data.totalPages
        isConverting.value = false
        loadedPages.value.add(1)
        stopPolling()
      } else if (data.status === 'failed') {
        isConverting.value = false
        error.value = data.errorMessage || 'PDF 转换失败'
        stopPolling()
      }
    } catch {
      // 网络错误不中断轮询
    }
  }, 1000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function goToPage(pageNum: number) {
  if (pageNum < 1 || pageNum > totalPages.value) return
  currentPage.value = pageNum

  if (!loadedPages.value.has(pageNum)) {
    loadedPages.value.add(pageNum)
  }

  const el = pageRefs.get(pageNum)
  if (el && el.isConnected) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

function onScroll() {
  if (scrollTimer) clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    if (!scrollContainer.value) return

    const container = scrollContainer.value
    const containerRect = container.getBoundingClientRect()
    const containerMid = containerRect.top + containerRect.height / 2

    let closestPage = 1
    let closestDist = Infinity

    pageRefs.forEach((el, page) => {
      if (!el.isConnected) {
        pageRefs.delete(page)
        return
      }

      const rect = el.getBoundingClientRect()
      const pageMid = rect.top + rect.height / 2
      const dist = Math.abs(pageMid - containerMid)

      if (dist < closestDist) {
        closestDist = dist
        closestPage = page
      }

      if (rect.bottom >= containerRect.top - 200 &&
          rect.top <= containerRect.bottom + 200) {
        if (!loadedPages.value.has(page)) {
          loadedPages.value.add(page)
        }
        if (page > 1 && !loadedPages.value.has(page - 1)) {
          loadedPages.value.add(page - 1)
        }
        if (page < totalPages.value && !loadedPages.value.has(page + 1)) {
          loadedPages.value.add(page + 1)
        }
      }
    })

    currentPage.value = closestPage
  }, 100)
}

function zoomIn() {
  scale.value = Math.min(3.0, scale.value + 0.25)
}

function zoomOut() {
  scale.value = Math.max(0.5, scale.value - 0.25)
}

onMounted(() => {
  loadPdfPreview()
})

onUnmounted(() => {
  stopPolling()
  if (scrollTimer) clearTimeout(scrollTimer)
  pageRefs.clear()
})
</script>

<style scoped>
.pdf-image-viewer {
  display: flex;
  flex-direction: column;
  height: 70vh;
  background: #525659;
}

/* ---- converting / error states ---- */
.pdf-converting {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #fff;
  gap: 8px;
}

.pdf-converting-hint {
  font-size: 13px;
  color: #999;
}

.pdf-error {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: #fff;
}

/* ---- toolbar ---- */
.pdf-toolbar {
  display: flex;
  align-items: center;
  gap: 0;
  padding: 6px 12px;
  background: #323639;
  flex-shrink: 0;
  min-height: 40px;
}

.pdf-toolbar-section {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pdf-toolbar-section .el-button {
  width: 28px;
  height: 28px;
  padding: 0;
  color: #ccc;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 4px;
}

.pdf-toolbar-section .el-button:hover:not(:disabled) {
  color: #fff;
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 255, 255, 0.15);
}

.pdf-toolbar-section .el-button:disabled {
  color: #555;
  background: transparent;
}

.pdf-toolbar-divider {
  width: 1px;
  height: 20px;
  background: rgba(255, 255, 255, 0.15);
  margin: 0 10px;
  flex-shrink: 0;
}

.pdf-toolbar-label {
  font-size: 13px;
  color: #bbb;
  padding: 0 2px;
  user-select: none;
}

.pdf-zoom-label {
  min-width: 44px;
  text-align: center;
}

/* ---- page input dark theme ---- */
.pdf-page-input {
  width: 72px;
}

.pdf-page-input :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: none;
  padding: 0 4px 0 8px;
  border-radius: 4px;
}

.pdf-page-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.25);
}

.pdf-page-input :deep(.el-input__wrapper.is-focus) {
  border-color: #409eff;
  background: rgba(255, 255, 255, 0.12);
}

.pdf-page-input :deep(.el-input__inner) {
  color: #e0e0e0;
  text-align: center;
  font-size: 13px;
}

.pdf-page-input :deep(.el-input-number__decrease),
.pdf-page-input :deep(.el-input-number__increase) {
  background: rgba(255, 255, 255, 0.05);
  color: #aaa;
  border-color: rgba(255, 255, 255, 0.06);
}

.pdf-page-input :deep(.el-input-number__decrease:hover),
.pdf-page-input :deep(.el-input-number__increase:hover) {
  color: #fff;
}

.pdf-file-name {
  margin-left: auto;
  font-size: 13px;
  color: #999;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 240px;
  padding-left: 16px;
}

/* ---- scroll area ---- */
.pdf-scroll-container {
  flex: 1;
  overflow-y: auto;
  overflow-x: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

.pdf-scroll-container::-webkit-scrollbar {
  width: 8px;
  height: 8px;
}

.pdf-scroll-container::-webkit-scrollbar-track {
  background: transparent;
}

.pdf-scroll-container::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.15);
  border-radius: 4px;
}

.pdf-scroll-container::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.25);
}

/* ---- page items ---- */
.pdf-page-item {
  position: relative;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
  background: #fff;
  display: flex;
  justify-content: center;
}

.pdf-page-img {
  display: block;
  transition: width 0.15s ease;
}

.pdf-page-placeholder {
  width: 100%;
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #999;
}

.pdf-page-label {
  position: absolute;
  bottom: 4px;
  right: 8px;
  font-size: 11px;
  color: #999;
  background: rgba(255, 255, 255, 0.85);
  padding: 1px 6px;
  border-radius: 3px;
}
</style>