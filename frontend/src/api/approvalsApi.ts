import type { ApprovalDecision } from '../types/approvals'
import { apiClient } from './apiClient'

// Decisions on approval steps, through the approval engine's task routes. The deciding account comes from the access
// token, and the backend checks the step's role and APPROVAL_TASK_DECIDE on every call.

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
