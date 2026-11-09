import type {
  AdminCampusNotice,
  AdminCampusRequestDetail,
  AdminCampusRequestQuery,
  AdminCampusRequestSummary,
  AdminCampusService,
  CampusNotice,
  CampusNoticeContent,
  CampusNoticeStatus,
  CampusRequestDetail,
  CampusRequestQuery,
  CampusRequestSummary,
  CampusService,
  CampusServiceSettings,
  Page,
} from '../types/campus'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api, and only the client sets the Authorization header. No request names a
// requester, account, status, or approval request: the caller and its roles come from the access token. Approval
// decisions are not here: they go through approveTask and rejectTask in approvalsApi.

// --- Requesters

export async function getCampusServices(signal?: AbortSignal) {
  return (await apiClient.get<CampusService[]>('/campus/services', { signal })).data
}

export async function createCampusRequest(request: { serviceId: number; subject: string; details: string }) {
  return (await apiClient.post<CampusRequestDetail>('/campus/requests', request)).data
}

export async function getMyCampusRequests(query: CampusRequestQuery, signal?: AbortSignal) {
  return (await apiClient.get<Page<CampusRequestSummary>>('/campus/requests/me', { params: query, signal })).data
}

export async function getMyCampusRequest(requestId: number, signal?: AbortSignal) {
  return (await apiClient.get<CampusRequestDetail>(`/campus/requests/${requestId}`, { signal })).data
}

export async function cancelCampusRequest(requestId: number) {
  return (await apiClient.post<CampusRequestDetail>(`/campus/requests/${requestId}/cancel`)).data
}

export async function getCampusNotices(query: { page: number; size: number }, signal?: AbortSignal) {
  return (await apiClient.get<Page<CampusNotice>>('/campus/notices', { params: query, signal })).data
}

// --- Admin: service catalog

export async function getAdminCampusServices(signal?: AbortSignal) {
  return (await apiClient.get<AdminCampusService[]>('/campus/admin/services', { signal })).data
}

export async function createCampusService(request: CampusServiceSettings & { code: string }) {
  return (await apiClient.post<AdminCampusService>('/campus/admin/services', request)).data
}

export async function updateCampusService(serviceId: number, request: CampusServiceSettings) {
  return (await apiClient.put<AdminCampusService>(`/campus/admin/services/${serviceId}`, request)).data
}

// --- Admin: service requests

export async function getAdminCampusRequests(query: AdminCampusRequestQuery, signal?: AbortSignal) {
  return (await apiClient.get<Page<AdminCampusRequestSummary>>('/campus/admin/requests', { params: query, signal })).data
}

export async function getAdminCampusRequest(requestId: number, signal?: AbortSignal) {
  return (await apiClient.get<AdminCampusRequestDetail>(`/campus/admin/requests/${requestId}`, { signal })).data
}

export async function completeCampusRequest(requestId: number) {
  return (await apiClient.post<AdminCampusRequestDetail>(`/campus/admin/requests/${requestId}/complete`)).data
}

// --- Admin: notices

export async function getAdminCampusNotices(query: { status?: CampusNoticeStatus; page: number; size: number }, signal?: AbortSignal) {
  return (await apiClient.get<Page<AdminCampusNotice>>('/campus/admin/notices', { params: query, signal })).data
}

export async function createCampusNotice(request: CampusNoticeContent) {
  return (await apiClient.post<AdminCampusNotice>('/campus/admin/notices', request)).data
}

export async function updateCampusNotice(noticeId: number, request: CampusNoticeContent) {
  return (await apiClient.put<AdminCampusNotice>(`/campus/admin/notices/${noticeId}`, request)).data
}

export async function publishCampusNotice(noticeId: number) {
  return (await apiClient.post<AdminCampusNotice>(`/campus/admin/notices/${noticeId}/publish`)).data
}

export async function archiveCampusNotice(noticeId: number) {
  return (await apiClient.post<AdminCampusNotice>(`/campus/admin/notices/${noticeId}/archive`)).data
}
