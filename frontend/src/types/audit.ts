// The audit log as GET /api/v1/admin/audit-events sends it: read-only, newest first.

export type AuditAction =
  | 'USER_CREATED'
  | 'USER_DETAILS_CHANGED'
  | 'USER_STATUS_CHANGED'
  | 'USER_ROLES_CHANGED'
  | 'USER_TEMPORARY_PASSWORD_ISSUED'
  | 'STUDENT_PROFILE_CREATED'
  | 'FACULTY_PROFILE_CREATED'

export type AuditRow = {
  auditEventId: number
  occurredAt: string
  /** Null for the system. */
  actorLoginId: string | null
  action: AuditAction
  targetType: string
  targetId: number | null
  /** The login ID when the target is an account. */
  targetLoginId: string | null
  details: string | null
  requestId: string | null
}

export type AuditPage = { items: AuditRow[]; page: number; size: number; totalElements: number; totalPages: number }

export type AuditFilters = { action?: AuditAction; from?: string; to?: string; q?: string; page: number; size: number }
