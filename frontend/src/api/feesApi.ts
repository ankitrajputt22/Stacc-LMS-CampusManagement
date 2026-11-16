import type { AdjustmentStatus, FeeAdjustment, FeeItem, FeeLedger, FeePayment, FeeStructure, PaymentMode } from '../types/fees'
import { apiClient } from './apiClient'

export async function getMyFees(signal?: AbortSignal): Promise<FeeLedger> {
  return (await apiClient.get<FeeLedger>('/erp/fees/me', { signal })).data
}

export async function applyForScholarship(body: { title: string; amount: number; reason: string }): Promise<FeeAdjustment> {
  return (await apiClient.post<FeeAdjustment>('/erp/fees/me/scholarships', body)).data
}

export async function askForRefund(body: { amount: number; reason: string }): Promise<FeeAdjustment> {
  return (await apiClient.post<FeeAdjustment>('/erp/fees/me/refunds', body)).data
}

export async function getStudentLedger(loginId: string, signal?: AbortSignal): Promise<FeeLedger> {
  return (await apiClient.get<FeeLedger>(`/admin/erp/fees/students/${encodeURIComponent(loginId)}`, { signal })).data
}

export async function recordPayment(loginId: string, body: { amount: number; mode: PaymentMode; reference: string | null; paidOn: string }): Promise<FeePayment> {
  return (await apiClient.post<FeePayment>(`/admin/erp/fees/students/${encodeURIComponent(loginId)}/payments`, body)).data
}

export async function getFeeRequests(status: AdjustmentStatus | '', signal?: AbortSignal): Promise<FeeAdjustment[]> {
  return (await apiClient.get<FeeAdjustment[]>('/admin/erp/fees/requests', { params: status ? { status } : {}, signal })).data
}

export async function decideFeeRequest(adjustmentId: number, approve: boolean, comment: string | null): Promise<FeeAdjustment> {
  return (await apiClient.post<FeeAdjustment>(`/admin/erp/fees/requests/${adjustmentId}/decide`, { approve, comment })).data
}

export async function getFeeStructure(programId: number, academicSessionId: number, signal?: AbortSignal): Promise<FeeStructure> {
  return (await apiClient.get<FeeStructure>('/admin/erp/fees/structures', { params: { programId, academicSessionId }, signal })).data
}

export async function setFeeStructure(programId: number, academicSessionId: number, body: { dueDate: string; items: FeeItem[] }): Promise<FeeStructure> {
  return (await apiClient.put<FeeStructure>('/admin/erp/fees/structures', body, { params: { programId, academicSessionId } })).data
}
