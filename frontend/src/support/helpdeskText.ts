import type { HelpdeskTicketHistory, HelpdeskTicketPriority, HelpdeskTicketStatus } from '../types/helpdesk'

// The one place helpdesk codes become words. Pages never show a raw code.

export const TICKET_STATUSES: readonly HelpdeskTicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
export const TICKET_PRIORITIES: readonly HelpdeskTicketPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT']
/** What a requester may choose. Urgent is set only by the support team; the backend refuses it anyway. */
export const REQUESTER_PRIORITIES = ['LOW', 'NORMAL', 'HIGH'] as const

// The backend's limits, so obvious mistakes are caught before sending. The backend checks everything again.
export const SUBJECT_MAX = 200
export const DESCRIPTION_MAX = 10_000
export const MESSAGE_MAX = 10_000
export const CATEGORY_CODE_MAX = 50
export const CATEGORY_NAME_MAX = 120
export const CATEGORY_DESCRIPTION_MAX = 1000

const STATUS_LABELS: Record<HelpdeskTicketStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In Progress',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
}

const PRIORITY_LABELS: Record<HelpdeskTicketPriority, string> = {
  LOW: 'Low',
  NORMAL: 'Normal',
  HIGH: 'High',
  URGENT: 'Urgent',
}

export function statusLabel(status: string): string {
  return STATUS_LABELS[status as HelpdeskTicketStatus] ?? status
}

export function priorityLabel(priority: string): string {
  return PRIORITY_LABELS[priority as HelpdeskTicketPriority] ?? priority
}

/**
 * The statuses a ticket may move to next, as the backend's lifecycle allows: forward only, and nothing after Closed.
 * Only these are offered; the backend checks the move again.
 */
export function nextStatuses(status: HelpdeskTicketStatus): HelpdeskTicketStatus[] {
  switch (status) {
    case 'OPEN':
      return ['IN_PROGRESS', 'RESOLVED', 'CLOSED']
    case 'IN_PROGRESS':
      return ['RESOLVED', 'CLOSED']
    case 'RESOLVED':
      return ['CLOSED']
    case 'CLOSED':
      return []
  }
}

/** One history entry as a sentence, such as "Status changed from Open to In Progress." */
export function historyText(entry: HelpdeskTicketHistory): string {
  switch (entry.action) {
    case 'CREATED':
      return 'Ticket created.'
    case 'MESSAGE_ADDED':
      return 'Message added.'
    case 'STATUS_CHANGED':
      return `Status changed from ${statusLabel(entry.fromValue ?? '')} to ${statusLabel(entry.toValue ?? '')}.`
    case 'PRIORITY_CHANGED':
      return `Priority changed from ${priorityLabel(entry.fromValue ?? '')} to ${priorityLabel(entry.toValue ?? '')}.`
    case 'ASSIGNED':
      return `Assigned to ${entry.toValue}.`
    case 'UNASSIGNED':
      return 'Ticket unassigned.'
    default:
      return 'Ticket updated.'
  }
}

export const SIGN_IN_AGAIN =
  'If your access changed recently, sign out and sign in again. Otherwise, contact the college administration.'

/** A refused change in safe words: the backend's own message for 400, 404, and 409, never request details. */
export function refusalText(status: number | null, apiMessage: string | null, what = 'save'): string {
  switch (status) {
    case 400:
    case 404:
    case 409:
      return apiMessage ?? `Couldn’t ${what}. Try again.`
    case 403:
      return 'You don’t have access to do this. If your access changed recently, sign out and sign in again.'
    default:
      return `Couldn’t ${what}. Check your connection and try again.`
  }
}
