import type {
  NotificationInboxItem,
  NotificationPage,
  NotificationReadAllResult,
  UnreadNotificationCount,
} from '../types/notifications'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api. Every route works on the Inbox of the account in the access token; no
// account ID is ever sent, and there is no way to create or delete a notification.

export async function getNotifications(
  query: { page: number; size: number; unreadOnly: boolean },
  signal?: AbortSignal,
) {
  const response = await apiClient.get<NotificationPage>('/notifications', { params: query, signal })
  return response.data
}

export async function getUnreadNotificationCount(signal?: AbortSignal) {
  const response = await apiClient.get<UnreadNotificationCount>('/notifications/unread-count', { signal })
  return response.data
}

export async function markNotificationRead(notificationRecipientId: number) {
  const response = await apiClient.post<NotificationInboxItem>(`/notifications/${notificationRecipientId}/read`)
  return response.data
}

export async function markNotificationUnread(notificationRecipientId: number) {
  const response = await apiClient.post<NotificationInboxItem>(`/notifications/${notificationRecipientId}/unread`)
  return response.data
}

export async function markAllNotificationsRead() {
  const response = await apiClient.post<NotificationReadAllResult>('/notifications/read-all')
  return response.data
}
