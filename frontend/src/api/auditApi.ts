import type { AuditFilters, AuditPage } from '../types/audit'
import { apiClient } from './apiClient'

/** One page of the audit log. Needs ADMIN with AUDIT_LOG_VIEW; there is no way to change the log. */
export async function searchAuditEvents(filters: AuditFilters, signal?: AbortSignal) {
  return (await apiClient.get<AuditPage>('/admin/audit-events', { params: filters, signal })).data
}
