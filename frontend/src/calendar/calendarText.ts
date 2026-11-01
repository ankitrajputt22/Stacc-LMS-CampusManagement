import type { CalendarEventCategory, CalendarEventStatus } from '../types/calendar'
import type { BadgeTone } from '../ui'

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

const STATUS_TONES: Record<CalendarEventStatus, BadgeTone> = {
  DRAFT: 'warning',
  PUBLISHED: 'success',
  CANCELLED: 'neutral',
}

/** The badge tone of an event status; the word always says it too. */
export function eventStatusTone(status: CalendarEventStatus): BadgeTone {
  return STATUS_TONES[status] ?? 'neutral'
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
