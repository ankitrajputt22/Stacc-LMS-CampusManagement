import type {
  AdminCalendarEvent,
  CalendarEventFilters,
  CalendarEventRequest,
  MyCalendarResponse,
} from '../types/calendar'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api. Who is asking always comes from the access token; no account or
// profile ID is ever sent.

/**
 * The signed-in account's calendar from one day to another, both included. The time zone only tells the backend
 * which deadlines fall on those days; it decides nothing about access.
 */
export async function getMyCalendar(range: { from: string; to: string; timeZone: string }, signal?: AbortSignal) {
  const response = await apiClient.get<MyCalendarResponse>('/calendar/me', { params: range, signal })
  return response.data
}

// The admin routes need the admin role and ACADEMIC_CALENDAR_VIEW; changes also need ACADEMIC_CALENDAR_MANAGE.
// There is no delete.
const EVENTS = '/admin/academic/calendar/events'

export async function getCalendarEvents(filters: CalendarEventFilters, signal?: AbortSignal) {
  const response = await apiClient.get<AdminCalendarEvent[]>(EVENTS, { params: filters, signal })
  return response.data
}

export async function createCalendarEvent(body: CalendarEventRequest) {
  const response = await apiClient.post<AdminCalendarEvent>(EVENTS, body)
  return response.data
}

export async function updateCalendarEvent(eventId: number, body: CalendarEventRequest) {
  const response = await apiClient.put<AdminCalendarEvent>(`${EVENTS}/${eventId}`, body)
  return response.data
}

export async function publishCalendarEvent(eventId: number) {
  const response = await apiClient.post<AdminCalendarEvent>(`${EVENTS}/${eventId}/publish`)
  return response.data
}

export async function cancelCalendarEvent(eventId: number) {
  const response = await apiClient.post<AdminCalendarEvent>(`${EVENTS}/${eventId}/cancel`)
  return response.data
}
