import type { CalendarEventCategory, CalendarEventStatus } from '../types/calendar'

const CATEGORY_LABELS: Record<CalendarEventCategory, string> = {
  ACADEMIC: 'Academic',
  HOLIDAY: 'Holiday',
  EXAM: 'Exam',
  REGISTRATION: 'Registration',
  OTHER: 'Other',
}

const STATUS_LABELS: Record<CalendarEventStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Published',
  CANCELLED: 'Cancelled',
}

export const CATEGORIES = Object.keys(CATEGORY_LABELS) as CalendarEventCategory[]
export const EVENT_STATUSES = Object.keys(STATUS_LABELS) as CalendarEventStatus[]

export const CALENDAR_DENIED = 'You don’t have access to the academic calendar.'
export const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

export function categoryLabel(category: CalendarEventCategory): string {
  return CATEGORY_LABELS[category] ?? category
}

export function eventStatusLabel(status: CalendarEventStatus): string {
  return STATUS_LABELS[status] ?? status
}

/** "2026-27 · Semester 3 · BTECH-CSE", or "2026-27 · All semesters" for a session-wide event. */
export function scopeText(event: { academicSessionCode: string; semesterNumber: number | null; programCode: string | null }) {
  return event.semesterNumber === null
    ? `${event.academicSessionCode} · All semesters`
    : `${event.academicSessionCode} · Semester ${event.semesterNumber}${event.programCode ? ` · ${event.programCode}` : ''}`
}

/** The quiet tag of a category or status: a word, never colour alone. */
export const tagClasses =
  'inline-block shrink-0 rounded-sm border border-line-strong px-1.5 py-0.5 text-xs font-medium text-ink'
