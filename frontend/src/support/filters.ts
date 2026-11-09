import type { HelpdeskTicketPriority, HelpdeskTicketStatus } from '../types/helpdesk'
import { TICKET_PRIORITIES, TICKET_STATUSES } from './helpdeskText'

// Filters live in the address, so the list comes back the same after opening a ticket. Anything unknown in the address
// is ignored rather than sent.

export function statusParam(value: string | null): HelpdeskTicketStatus | undefined {
  return TICKET_STATUSES.find((status) => status === value)
}

export function priorityParam(value: string | null): HelpdeskTicketPriority | undefined {
  return TICKET_PRIORITIES.find((priority) => priority === value)
}

export function idParam(value: string | null): number | undefined {
  return value !== null && /^[1-9]\d{0,17}$/.test(value) ? Number(value) : undefined
}

export function pageParam(value: string | null): number {
  return value !== null && /^\d{1,6}$/.test(value) ? Number(value) : 0
}

export const PAGE_SIZE = 20
