// The helpdesk exactly as the backend sends it (HelpdeskResponses and HelpdeskRequests in
// com.stacc.backend.support.helpdesk). People appear by login ID only.

export type HelpdeskTicketStatus = 'OPEN' | 'IN_PROGRESS' | 'RESOLVED' | 'CLOSED'

export type HelpdeskTicketPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export type HelpdeskHistoryAction =
  | 'CREATED'
  | 'MESSAGE_ADDED'
  | 'STATUS_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'ASSIGNED'
  | 'UNASSIGNED'

/** A category that can be chosen for a new ticket. */
export type HelpdeskCategory = {
  categoryId: number
  code: string
  name: string
  description: string | null
}

/** A category as admins manage it, active or not. */
export type AdminHelpdeskCategory = HelpdeskCategory & {
  active: boolean
  createdAt: string
  updatedAt: string
}

/** One ticket in a list: no description, messages, or history. */
export type HelpdeskTicketSummary = {
  ticketId: number
  ticketNumber: string
  categoryCode: string
  categoryName: string
  subject: string
  priority: HelpdeskTicketPriority
  status: HelpdeskTicketStatus
  requesterLoginId: string
  assignedToLoginId: string | null
  createdAt: string
  updatedAt: string
}

export type HelpdeskTicketPage = {
  items: HelpdeskTicketSummary[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type HelpdeskTicketMessage = {
  messageId: number
  authorLoginId: string
  /** Plain text. */
  message: string
  createdAt: string
}

export type HelpdeskTicketHistory = {
  action: HelpdeskHistoryAction
  fromValue: string | null
  toValue: string | null
  actorLoginId: string
  createdAt: string
}

/** One ticket in full: the original description, then the conversation and history, oldest first. */
export type HelpdeskTicketDetail = {
  ticketId: number
  ticketNumber: string
  categoryCode: string
  categoryName: string
  categoryActive: boolean
  subject: string
  /** Plain text: the original issue. */
  description: string
  priority: HelpdeskTicketPriority
  status: HelpdeskTicketStatus
  requesterLoginId: string
  assignedToLoginId: string | null
  resolvedAt: string | null
  closedAt: string | null
  createdAt: string
  updatedAt: string
  messages: HelpdeskTicketMessage[]
  history: HelpdeskTicketHistory[]
}

/** An active admin account a ticket can be assigned to. */
export type HelpdeskAssignee = {
  loginId: string
}

/** The requester never sends who they are, a status, or an assignee: those come from the server. */
export type CreateHelpdeskTicketRequest = {
  categoryId: number
  subject: string
  description: string
  priority: Exclude<HelpdeskTicketPriority, 'URGENT'>
}

export type CreateHelpdeskCategoryRequest = {
  code: string
  name: string
  description: string | null
  active: boolean
}

/** The code is not part of an update: it never changes. */
export type UpdateHelpdeskCategoryRequest = {
  name: string
  description: string | null
  active: boolean
}

export type MyTicketQuery = {
  status?: HelpdeskTicketStatus
  categoryId?: number
  page: number
  size: number
}

export type QueueQuery = MyTicketQuery & {
  priority?: HelpdeskTicketPriority
  assignedToMe?: boolean
  requesterLoginId?: string
}
