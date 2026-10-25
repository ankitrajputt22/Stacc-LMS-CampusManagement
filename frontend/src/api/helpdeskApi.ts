import type {
  AdminHelpdeskCategory,
  CreateHelpdeskCategoryRequest,
  CreateHelpdeskTicketRequest,
  HelpdeskAssignee,
  HelpdeskCategory,
  HelpdeskTicketDetail,
  HelpdeskTicketMessage,
  HelpdeskTicketPage,
  HelpdeskTicketPriority,
  HelpdeskTicketStatus,
  MyTicketQuery,
  QueueQuery,
  UpdateHelpdeskCategoryRequest,
} from '../types/helpdesk'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api, and only the client sets the Authorization header. The requester, every
// author, and every actor come from the access token: no request names an account.

export async function getHelpdeskCategories(signal?: AbortSignal) {
  const response = await apiClient.get<HelpdeskCategory[]>('/helpdesk/categories', { signal })
  return response.data
}

export async function createHelpdeskTicket(request: CreateHelpdeskTicketRequest) {
  const response = await apiClient.post<HelpdeskTicketDetail>('/helpdesk/tickets', request)
  return response.data
}

export async function getMyHelpdeskTickets(query: MyTicketQuery, signal?: AbortSignal) {
  const response = await apiClient.get<HelpdeskTicketPage>('/helpdesk/tickets/me', { params: query, signal })
  return response.data
}

export async function getHelpdeskTicket(ticketId: number, signal?: AbortSignal) {
  const response = await apiClient.get<HelpdeskTicketDetail>(`/helpdesk/tickets/${ticketId}`, { signal })
  return response.data
}

export async function addHelpdeskMessage(ticketId: number, request: { message: string }) {
  const response = await apiClient.post<HelpdeskTicketMessage>(`/helpdesk/tickets/${ticketId}/messages`, request)
  return response.data
}

export async function getAdminHelpdeskTickets(query: QueueQuery, signal?: AbortSignal) {
  const response = await apiClient.get<HelpdeskTicketPage>('/helpdesk/admin/tickets', { params: query, signal })
  return response.data
}

export async function getHelpdeskAssignees(signal?: AbortSignal) {
  const response = await apiClient.get<HelpdeskAssignee[]>('/helpdesk/admin/assignees', { signal })
  return response.data
}

export async function assignHelpdeskTicket(ticketId: number, request: { assignedToLoginId: string | null }) {
  const response = await apiClient.put<HelpdeskTicketDetail>(`/helpdesk/admin/tickets/${ticketId}/assignment`, request)
  return response.data
}

export async function changeHelpdeskTicketPriority(ticketId: number, request: { priority: HelpdeskTicketPriority }) {
  const response = await apiClient.put<HelpdeskTicketDetail>(`/helpdesk/admin/tickets/${ticketId}/priority`, request)
  return response.data
}

export async function changeHelpdeskTicketStatus(ticketId: number, request: { status: HelpdeskTicketStatus }) {
  const response = await apiClient.put<HelpdeskTicketDetail>(`/helpdesk/admin/tickets/${ticketId}/status`, request)
  return response.data
}

export async function getAdminHelpdeskCategories(signal?: AbortSignal) {
  const response = await apiClient.get<AdminHelpdeskCategory[]>('/helpdesk/admin/categories', { signal })
  return response.data
}

export async function createHelpdeskCategory(request: CreateHelpdeskCategoryRequest) {
  const response = await apiClient.post<AdminHelpdeskCategory>('/helpdesk/admin/categories', request)
  return response.data
}

export async function updateHelpdeskCategory(categoryId: number, request: UpdateHelpdeskCategoryRequest) {
  const response = await apiClient.put<AdminHelpdeskCategory>(`/helpdesk/admin/categories/${categoryId}`, request)
  return response.data
}
