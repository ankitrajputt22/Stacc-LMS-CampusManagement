export type HelpdeskTicketStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING_ON_REQUESTER' | 'RESOLVED' | 'CLOSED'

export type HelpdeskTicketPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

export type HelpdeskHistoryAction =
  | 'CREATED'
  | 'MESSAGE_ADDED'
  | 'STATUS_CHANGED'
  | 'PRIORITY_CHANGED'
  | 'ASSIGNED'
  | 'UNASSIGNED'
  | 'NOTE_ADDED'
  | 'REOPENED'
  | 'RATED'
  | 'TEAM_CHANGED'

export type HelpdeskCategory = {
  categoryId: number
  code: string
  name: string
  description: string | null
}

export type AdminHelpdeskCategory = HelpdeskCategory & {
  active: boolean
  createdAt: string
  updatedAt: string
}

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
  teamName: string | null
  firstResponseAt: string | null
  rating: number | null
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
  message: string
  createdAt: string
  internal: boolean
}

export type HelpdeskTicketHistory = {
  action: HelpdeskHistoryAction
  fromValue: string | null
  toValue: string | null
  actorLoginId: string
  createdAt: string
}

export type HelpdeskTicketDetail = {
  ticketId: number
  ticketNumber: string
  categoryCode: string
  categoryName: string
  categoryActive: boolean
  subject: string
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
  teamCode: string | null
  teamName: string | null
  firstResponseAt: string | null
  rating: number | null
  ratingComment: string | null
  supportSide: boolean
  canReopen: boolean
  canRate: boolean
}

export type HelpdeskTeam = {
  teamId: number
  code: string
  name: string
  active: boolean
  memberLoginIds: string[]
}

export type HelpdeskAssignee = {
  loginId: string
}

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

export type HelpArticle = {
  articleId: number
  categoryId: number
  categoryName: string
  title: string
  body: string
  publishedAt: string
}

export type HelpArticleStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'

export type AdminHelpArticle = {
  articleId: number
  categoryId: number
  categoryName: string
  categoryActive: boolean
  title: string
  body: string
  status: HelpArticleStatus
  publishedAt: string | null
  updatedAt: string
}

export type HelpArticleInput = { categoryId: number; title: string; body: string }
