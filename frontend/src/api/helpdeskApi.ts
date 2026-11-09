import type {
  AdminHelpdeskCategory,
  CreateHelpdeskCategoryRequest,
  CreateHelpdeskTicketRequest,
  HelpdeskAssignee,
  HelpdeskCategory,
  HelpdeskTicketDetail,
  HelpdeskTicketMessage,
  HelpdeskTeam,
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

/** An internal note, seen only by the support side. */
export async function addHelpdeskNote(ticketId: number, message: string) {
  await apiClient.post(`/helpdesk/tickets/${ticketId}/notes`, { message })
}

/** The requester reopens a resolved ticket within 7 days. */
export async function reopenHelpdeskTicket(ticketId: number) {
  return (await apiClient.post<HelpdeskTicketDetail>(`/helpdesk/tickets/${ticketId}/reopen`)).data
}

/** The requester rates a resolved or closed ticket once. */
export async function rateHelpdeskTicket(ticketId: number, rating: number, comment: string | null) {
  return (await apiClient.post<HelpdeskTicketDetail>(`/helpdesk/tickets/${ticketId}/rating`, { rating, comment })).data
}

/** The tickets of the signed-in staff member's teams. */
export async function getTeamHelpdeskTickets(query: { status?: string; page: number; size: number }, signal?: AbortSignal) {
  return (await apiClient.get<HelpdeskTicketPage>('/helpdesk/team/tickets', { params: query, signal })).data
}

export async function getHelpdeskTeams(signal?: AbortSignal) {
  return (await apiClient.get<HelpdeskTeam[]>('/helpdesk/admin/teams', { signal })).data
}

export async function createHelpdeskTeam(code: string, name: string) {
  return (await apiClient.post<HelpdeskTeam>('/helpdesk/admin/teams', { code, name })).data
}

export async function updateHelpdeskTeam(teamId: number, name: string, active: boolean) {
  return (await apiClient.put<HelpdeskTeam>(`/helpdesk/admin/teams/${teamId}`, { name, active })).data
}

export async function setHelpdeskTeamMembers(teamId: number, loginIds: string[]) {
  return (await apiClient.put<HelpdeskTeam>(`/helpdesk/admin/teams/${teamId}/members`, { loginIds })).data
}

export async function routeHelpdeskCategory(categoryId: number, teamId: number | null) {
  await apiClient.put(`/helpdesk/admin/categories/${categoryId}/team`, { teamId })
}
