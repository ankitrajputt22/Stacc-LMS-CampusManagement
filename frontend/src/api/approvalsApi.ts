import type { ApprovalDecision, ApprovalWorkflowSummary } from '../types/approvals'
import { apiClient } from './apiClient'

export async function approveTask(approvalRequestStepId: number, comment: string | null) {
  const response = await apiClient.post<ApprovalDecision>(`/approvals/tasks/${approvalRequestStepId}/approve`, {
    comment: comment === null || comment.trim() === '' ? null : comment.trim(),
  })
  return response.data
}

export async function rejectTask(approvalRequestStepId: number, comment: string) {
  const response = await apiClient.post<ApprovalDecision>(`/approvals/tasks/${approvalRequestStepId}/reject`, {
    comment: comment.trim(),
  })
  return response.data
}

export async function getApprovalWorkflows(signal?: AbortSignal) {
  return (await apiClient.get<ApprovalWorkflowSummary[]>('/approvals/admin/workflows', { signal })).data
}
