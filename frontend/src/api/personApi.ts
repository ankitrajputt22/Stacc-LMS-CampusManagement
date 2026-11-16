import type { MyPersonProfile } from '../types/person'
import { apiClient } from './apiClient'

/** The signed-in person's own profile: GET /api/v1/me/profile. The person is the one in the access token. */
export async function getMyProfile(signal?: AbortSignal): Promise<MyPersonProfile> {
  const response = await apiClient.get<MyPersonProfile>('/me/profile', { signal })
  return response.data
}

/** Changes the preferred name and phone; blank clears. The college sets the full name, email, and photo. */
export async function updateMyProfile(preferredName: string | null, phone: string | null): Promise<MyPersonProfile> {
  const response = await apiClient.put<MyPersonProfile>('/me/profile', { preferredName, phone })
  return response.data
}

/** The signed-in person's official photo as image data, for an object URL. */
export async function getMyPhoto(signal?: AbortSignal): Promise<Blob> {
  const response = await apiClient.get<Blob>('/me/profile/photo', { responseType: 'blob', signal })
  return response.data
}

/** Changes the signed-in person's mailing address; blank clears it. */
export async function updateMyMailingAddress(mailingAddress: string | null): Promise<MyPersonProfile> {
  const response = await apiClient.put<MyPersonProfile>('/me/profile/mailing-address', { mailingAddress })
  return response.data
}

/** Adds an emergency contact (at most three). */
export async function addMyEmergencyContact(contact: { name: string; relation: string; phone: string }): Promise<MyPersonProfile> {
  const response = await apiClient.post<MyPersonProfile>('/me/profile/emergency-contacts', contact)
  return response.data
}

/** Removes one of the signed-in person's own emergency contacts. */
export async function removeMyEmergencyContact(contactId: number): Promise<MyPersonProfile> {
  const response = await apiClient.delete<MyPersonProfile>(`/me/profile/emergency-contacts/${contactId}`)
  return response.data
}
