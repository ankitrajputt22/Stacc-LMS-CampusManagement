import type { HelpdeskTicketDetail } from '../types/helpdesk'
import type { ApprovalTaskSummary, GateMovement, HostelOuting, OutingKind, OutingPass, PassCheck } from '../types/outings'
import { apiClient } from './apiClient'

// Who asks always comes from the session. Wardens decide outings through the approval task routes.

export async function getMyOutings(signal?: AbortSignal) {
  return (await apiClient.get<HostelOuting[]>('/hostel/student/outings', { signal })).data
}

export async function requestOuting(body: { kind: OutingKind; leaveFrom: string; leaveUntil: string; reason: string; destination: string | null }) {
  return (await apiClient.post<HostelOuting>('/hostel/student/outings', body)).data
}

export async function cancelOuting(outingId: number) {
  return (await apiClient.post<HostelOuting>(`/hostel/student/outings/${outingId}/cancel`)).data
}

export async function extendOuting(outingId: number, until: string, reason: string) {
  return (await apiClient.post<HostelOuting>(`/hostel/student/outings/${outingId}/extensions`, { until, reason })).data
}

export async function openOutingPass(outingId: number) {
  return (await apiClient.post<OutingPass>(`/hostel/student/outings/${outingId}/pass`)).data
}

export async function reportRoomProblem(subject: string, description: string) {
  return (await apiClient.post<HelpdeskTicketDetail>('/hostel/student/complaints', { subject, description })).data
}

export async function checkPass(code: string) {
  return (await apiClient.post<PassCheck>('/gate/check', { code })).data
}

export async function recordMovement(code: string, direction: 'OUT' | 'IN') {
  return (await apiClient.post<PassCheck>('/gate/movements', { code, direction })).data
}

export async function getGateMovements(signal?: AbortSignal) {
  return (await apiClient.get<GateMovement[]>('/gate/movements', { signal })).data
}

export async function lookUpStudentOutings(loginId: string) {
  return (await apiClient.get<HostelOuting[]>(`/gate/students/${encodeURIComponent(loginId)}/outings`)).data
}

export async function getOverdueOutings(signal?: AbortSignal) {
  return (await apiClient.get<HostelOuting[]>('/hostel/warden/overdue', { signal })).data
}

export async function getMyApprovalTasks(signal?: AbortSignal) {
  return (await apiClient.get<{ items: ApprovalTaskSummary[] }>('/approvals/tasks', { params: { page: 0, size: 50 }, signal })).data.items
}
