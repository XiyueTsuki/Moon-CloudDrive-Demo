/**
 * 文件预览 Composable
 * 负责文件在线预览的加载、分流渲染、代码高亮和剪贴板复制
 */
import { ref } from 'vue'
import { getPreviewInfo, getTextContent, getPdfPreview } from '@/features/preview/api/preview'
import type { PreviewInfo } from '@/features/preview/types/preview'
import type { PdfPreviewInfo } from '@/features/preview/types/preview'
import type { FileInfo } from '../types/files'
import { ElMessage } from 'element-plus'
import hljs from 'highlight.js'
import 'highlight.js/styles/github.css'

export function useFilePreview() {
  /** 预览弹窗可见性 */
  const previewDialogVisible = ref(false)
  /** 加载状态 */
  const previewLoading = ref(false)
  /** 预览信息 */
  const previewInfo = ref<PreviewInfo | null>(null)
  /** 文本内容 */
  const previewTextContent = ref('')
  /** 高亮后的 HTML */
  const previewTextHtml = ref('')
  /** 错误消息 */
  const previewErrorMsg = ref('')
  /** 当前预览的文件 */
  const previewFile = ref<FileInfo | null>(null)

  /** PDF 预览信息 */
  const pdfPreviewInfo = ref<PdfPreviewInfo | null>(null)
  /** PDF 页面预览 URL 列表 */
  const pdfPageUrls = ref<string[]>([])

  /**
   * 打开文件预览
   * 根据后端返回的 previewType 分流渲染
   */
  async function handlePreview(file: FileInfo) {
    previewFile.value = file
    previewDialogVisible.value = true
    previewLoading.value = true
    previewInfo.value = null
    previewTextContent.value = ''
    previewTextHtml.value = ''
    previewErrorMsg.value = ''
    pdfPreviewInfo.value = null
    pdfPageUrls.value = []

    try {
      const res = await getPreviewInfo(file.id)
      const info = res.data.data
      previewInfo.value = info

      if (info.previewType === 'text') {
        await loadTextContent(file.id, info.language || 'plaintext')
      } else if (info.previewType === 'pdf' || info.previewType === 'pdf_image') {
        await loadPdfPreview(file.id)
      }

      previewLoading.value = false
    } catch {
      previewErrorMsg.value = '加载预览信息失败，请重试'
      previewLoading.value = false
    }
  }

  /** 获取文本内容并使用 highlight.js 高亮 */
  async function loadTextContent(fileId: number, language: string) {
    try {
      const res = await getTextContent(fileId)
      const text = res.data.data
      previewTextContent.value = text.content

      const result =
        language === 'plaintext'
          ? hljs.highlightAuto(text.content)
          : hljs.highlight(text.content, { language, ignoreIllegals: true })
      previewTextHtml.value = result.value
    } catch (e: any) {
      previewErrorMsg.value = e?.response?.data?.msg || '获取文本内容失败'
    }
  }

  /** 关闭预览 */
  function handleClosePreview() {
    previewDialogVisible.value = false
    previewInfo.value = null
    previewFile.value = null
    previewTextContent.value = ''
    previewTextHtml.value = ''
    previewErrorMsg.value = ''
  }

  /** 复制文本内容到剪贴板 */
  async function copyTextContent() {
    if (!previewTextContent.value) return
    try {
      await navigator.clipboard.writeText(previewTextContent.value)
      ElMessage.success('已复制到剪贴板')
    } catch {
      const textarea = document.createElement('textarea')
      textarea.value = previewTextContent.value
      textarea.style.position = 'fixed'
      textarea.style.opacity = '0'
      document.body.appendChild(textarea)
      textarea.select()
      document.execCommand('copy')
      document.body.removeChild(textarea)
      ElMessage.success('已复制到剪贴板')
    }
  }

  /** 获取 PDF 预览信息（服务端转图片模式） */
  async function loadPdfPreview(fileId: number) {
    try {
      const res = await getPdfPreview(fileId)
      const pdfInfo = res.data.data
      pdfPreviewInfo.value = pdfInfo

      const token = localStorage.getItem('token') || ''
      const urlPrefix = `/api/file/preview/pdf/page/`
      pdfPageUrls.value = Array.from({ length: pdfInfo.totalPages }, (_, i) =>
        `${urlPrefix}${i + 1}?fileId=${fileId}&satoken=${token}`
      )
    } catch (e: any) {
      previewErrorMsg.value = e?.response?.data?.msg || '获取PDF预览信息失败'
    }
  }

  return {
    previewDialogVisible,
    previewLoading,
    previewInfo,
    previewTextContent,
    previewTextHtml,
    previewErrorMsg,
    previewFile,
    pdfPreviewInfo,
    pdfPageUrls,
    handlePreview,
    handleClosePreview,
    copyTextContent,
  }
}