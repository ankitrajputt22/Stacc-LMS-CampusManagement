import type {
  NotificationInboxItem,
  NotificationPage,
  NotificationReadAllResult,
  UnreadNotificationCount,
} from '../types/notifications'
import type { NotificationChoice, NotificationSettings } from '../types/notificationSettings'
import { apiClient } from './apiClient'

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

export async function getNotificationSettings(signal?: AbortSignal): Promise<NotificationSettings> {
  return (await apiClient.get<NotificationSettings>('/notifications/settings', { signal })).data
}

export async function saveNotificationSettings(choices: NotificationChoice[]): Promise<NotificationSettings> {
  return (await apiClient.put<NotificationSettings>('/notifications/settings', { choices })).data
}
