import type { MyPersonProfile } from '../types/person'
import { apiClient } from './apiClient'

export async function getMyProfile(signal?: AbortSignal): Promise<MyPersonProfile> {
  const response = await apiClient.get<MyPersonProfile>('/me/profile', { signal })
  return response.data
}

export async function updateMyProfile(preferredName: string | null, phone: string | null): Promise<MyPersonProfile> {
  const response = await apiClient.put<MyPersonProfile>('/me/profile', { preferredName, phone })
  return response.data
}

export async function getMyPhoto(signal?: AbortSignal): Promise<Blob> {
  const response = await apiClient.get<Blob>('/me/profile/photo', { responseType: 'blob', signal })
  return response.data
}

export async function updateMyMailingAddress(mailingAddress: string | null): Promise<MyPersonProfile> {
  const response = await apiClient.put<MyPersonProfile>('/me/profile/mailing-address', { mailingAddress })
  return response.data
}

export async function addMyEmergencyContact(contact: { name: string; relation: string; phone: string }): Promise<MyPersonProfile> {
  const response = await apiClient.post<MyPersonProfile>('/me/profile/emergency-contacts', contact)
  return response.data
}

export async function removeMyEmergencyContact(contactId: number): Promise<MyPersonProfile> {
  const response = await apiClient.delete<MyPersonProfile>(`/me/profile/emergency-contacts/${contactId}`)
  return response.data
}
