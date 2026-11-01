import type { MyPersonProfile } from '../types/person'
import { apiClient } from './apiClient'

/** The signed-in person's own profile: GET /api/v1/me/profile. The person is the one in the access token. */
export async function getMyProfile(signal?: AbortSignal): Promise<MyPersonProfile> {
  const response = await apiClient.get<MyPersonProfile>('/me/profile', { signal })
  return response.data
}
