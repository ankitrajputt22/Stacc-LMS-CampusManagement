import type { LoginResponse } from '../auth/types'
import { apiClient } from './apiClient'

/**
 * Signs in with college credentials. The values are sent exactly as typed: the backend
 * decides how a login ID is compared, and a password is never trimmed or changed.
 */
export async function login(
  loginId: string,
  password: string,
): Promise<LoginResponse> {
  // The client's base URL already ends in /api, so this is POST /api/auth/login.
  const response = await apiClient.post<LoginResponse>('/auth/login', {
    loginId,
    password,
  })

  return response.data
}
