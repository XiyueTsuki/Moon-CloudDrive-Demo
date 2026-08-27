import http from './index'
import type { ApiResponse, CreateShareRequest, ShareInfo, ShareInfoResponse, VerifyCodeRequest } from '@/types/api'

export function createShare(data: CreateShareRequest) {
  return http.post<ApiResponse<ShareInfo>>('/api/share/create', data)
}

export function getMyShares() {
  return http.get<ApiResponse<ShareInfo[]>>('/api/share/my')
}

export function cancelShare(shareCode: string) {
  return http.delete<ApiResponse<null>>(`/api/share/${shareCode}`)
}

export function getShareInfo(shareCode: string) {
  return http.get<ApiResponse<ShareInfoResponse>>(`/s/${shareCode}`)
}

export function verifySharePassword(shareCode: string, data: VerifyCodeRequest) {
  return http.post<ApiResponse<string>>(`/s/${shareCode}/verify`, data)
}