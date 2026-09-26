/**
 * 分享模块 - API 接口封装
 * 包含分享创建、查询、验证、下载等功能
 */
import http from '@/shared/http'
import type { ApiResponse } from '@/shared/types'
import type { CreateShareRequest, ShareInfo, ShareInfoResponse, VerifyCodeRequest } from '../types/share'

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