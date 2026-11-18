import type { AuditFilters, AuditPage } from '../types/audit'
import { apiClient } from './apiClient'

export async function searchAuditEvents(filters: AuditFilters, signal?: AbortSignal) {
  return (await apiClient.get<AuditPage>('/admin/audit-events', { params: filters, signal })).data
}
