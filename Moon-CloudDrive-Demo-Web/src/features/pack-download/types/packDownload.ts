/**
 * 打包下载模块 - 类型定义
 */

/** 打包进度信息 */
export interface PackProgress {
  status: string
  percent: number
  message: string
  zipFilename: string | null
}