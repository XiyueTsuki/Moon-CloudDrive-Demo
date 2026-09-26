/**
 * 分享模块 - API 接口封装
 * 包含分享创建、查询、验证、下载、文件夹打包下载等功能
 */
import http from '@/shared/http'
import type { ApiResponse } from '@/shared/types'
import type { CreateShareRequest, ShareInfo, ShareInfoResponse, VerifyCodeRequest, PackProgressResponse } from '../types/share'

/** 创建分享链接 */
export function createShare(data: CreateShareRequest) {
  return http.post<ApiResponse<ShareInfo>>('/api/share/create', data)
}

/** 获取我的分享列表 */
export function getMyShares() {
  return http.get<ApiResponse<ShareInfo[]>>('/api/share/my')
}

/** 取消分享链接 */
export function cancelShare(shareCode: string) {
  return http.delete<ApiResponse<null>>(`/api/share/${shareCode}`)
}

/** 获取分享文件元信息 */
export function getShareInfo(shareCode: string) {
  return http.get<ApiResponse<ShareInfoResponse>>(`/share/${shareCode}`)
}

/** 验证分享提取码 */
export function verifySharePassword(shareCode: string, data: VerifyCodeRequest) {
  return http.post<ApiResponse<null>>(`/share/${shareCode}/verify`, data)
}

/** 获取分享文件下载链接 */
export function getShareDownloadUrl(shareCode: string, password?: string) {
  return http.get<ApiResponse<string>>(`/share/${shareCode}/download`, {
    params: password ? { password } : {},
  })
}

/**
 * 提交分享文件夹打包下载任务
 * @param shareCode 分享码
 * @param password  提取码（可选）
 * @returns 包含 taskId 的响应
 */
export function prepareSharePackDownload(shareCode: string, password?: string) {
  return http.post<ApiResponse<string>>(
    `/share/${shareCode}/prepare-pack`,
    null,
    { params: password ? { password } : {} },
  )
}

/**
 * 查询分享文件夹打包进度
 * @param shareCode 分享码
 * @param taskId    打包任务ID
 * @returns 打包进度信息
 */
export function getSharePackProgress(shareCode: string, taskId: string) {
  return http.get<ApiResponse<PackProgressResponse>>(
    `/share/${shareCode}/pack-progress`,
    { params: { taskId } },
  )
}