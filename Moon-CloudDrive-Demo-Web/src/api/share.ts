/**
 * 分享相关 API 接口封装
 * 包含分享创建、查询、验证、下载等功能
 */
import http from './index'
import type { ApiResponse, CreateShareRequest, ShareInfo, ShareInfoResponse, VerifyCodeRequest } from '@/types/api'

/**
 * 创建分享链接
 * @param data 分享参数（文件ID、提取码、有效时长、最大下载次数）
 */
export function createShare(data: CreateShareRequest) {
  return http.post<ApiResponse<ShareInfo>>('/api/share/create', data)
}

/**
 * 获取我的分享列表
 */
export function getMyShares() {
  return http.get<ApiResponse<ShareInfo[]>>('/api/share/my')
}

/**
 * 取消分享链接
 * @param shareCode 分享码
 */
export function cancelShare(shareCode: string) {
  return http.delete<ApiResponse<null>>(`/api/share/${shareCode}`)
}

/**
 * 获取分享文件元信息（不包含下载链接，不消耗下载次数）
 * @param shareCode 分享码
 */
export function getShareInfo(shareCode: string) {
  return http.get<ApiResponse<ShareInfoResponse>>(`/share/${shareCode}`)
}

/**
 * 验证分享提取码（仅校验，不消耗下载次数）
 * @param shareCode 分享码
 * @param data      提取码
 */
export function verifySharePassword(shareCode: string, data: VerifyCodeRequest) {
  return http.post<ApiResponse<null>>(`/share/${shareCode}/verify`, data)
}

/**
 * 获取分享文件下载链接（实际下载时调用，消耗一次下载次数）
 * @param shareCode 分享码
 * @param password  提取码（无密码分享可不传）
 */
export function getShareDownloadUrl(shareCode: string, password?: string) {
  return http.get<ApiResponse<string>>(`/share/${shareCode}/download`, {
    params: password ? { password } : {},
  })
}