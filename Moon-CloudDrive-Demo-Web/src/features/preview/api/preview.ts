/**
 * 预览模块 - API 接口封装
 * 包含文件预览信息获取、文本内容获取、PDF 预览等接口
 */
import http from '@/shared/http'
import type { ApiResponse } from '@/shared/types'
import type { PreviewInfo, TextPreview } from '../types/preview'

/** 获取文件预览信息 */
export function getPreviewInfo(fileId: number) {
  return http.get<ApiResponse<PreviewInfo>>('/api/file/preview/info', {
    params: { fileId },
  })
}

/** 获取文本文件内容（用于代码高亮预览） */
export function getTextContent(fileId: number) {
  return http.get<ApiResponse<TextPreview>>('/api/file/preview/text', {
    params: { fileId },
  })
}

/** PDF 转为图片后的预览信息 */
export interface PdfPreview {
  totalPages: number
  status: 'converting' | 'ready' | 'failed'
  pageUrls: string[]
  errorMessage?: string
}

/** 获取 PDF 预览信息（服务端转图片模式） */
export function getPdfPreview(fileId: number) {
  return http.get<ApiResponse<PdfPreview>>('/api/file/preview/pdf', {
    params: { fileId },
  })
}

/**
 * 获取 PDF 单页图片流地址
 * 返回带认证 token 的图片流 URL，浏览器可直接作为 img src 使用
 */
export function getPdfPageUrl(fileId: number, pageNum: number): string {
  const token = localStorage.getItem('token')
  return `/api/file/preview/pdf/page/${pageNum}?fileId=${fileId}&satoken=${token}`
}