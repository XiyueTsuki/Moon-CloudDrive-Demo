/**
 * 预览模块 - 类型定义
 * 包含文件在线预览相关的类型
 */

/** 文件预览信息响应 */
export interface PreviewInfo {
  /** 预览类型: image / video / audio / pdf / text / unsupported */
  previewType: string
  /** 预览 URL（图片/视频/音频/PDF 时有效，text 时为 null） */
  previewUrl: string | null
  /** 原始文件名 */
  fileName: string
  /** MIME 类型 */
  mimeType: string
  /** 文件大小（字节） */
  fileSize: number
  /** 文本文件语言标识（仅预览text类型时有效） */
  language: string | null
  /** 字幕文件URL（视频文件可能附带） */
  subtitleUrl?: string | null
}

/** 文本文件预览内容响应 */
export interface TextPreview {
  /** 文本文件完整内容（UTF-8 解码后） */
  content: string
  /** 语言标识，供 highlight.js 使用 */
  language: string
  /** 字符编码，固定为 "UTF-8" */
  encoding: string
}

/** PDF 服务端转图片预览信息 */
export interface PdfPreviewInfo {
  /** PDF 总页数 */
  totalPages: number
  /** 转换状态: converting / ready / failed */
  status: 'converting' | 'ready' | 'failed'
  /** 每页图片的 OSS 预签名 URL 列表 */
  pageUrls: string[]
  /** 错误信息（status=failed 时有效） */
  errorMessage?: string
}