import { isAxiosError } from 'axios'
import type { ApiErrorResponse } from '../types/api'

function isApiErrorResponse(data: unknown): data is ApiErrorResponse {
  if (typeof data !== 'object' || data === null) {
    return false
  }

  const candidate = data as Record<string, unknown>

  return (
    typeof candidate.status === 'number' &&
    typeof candidate.message === 'string' &&
    Array.isArray(candidate.fieldErrors)
  )
}

export function getHttpStatus(error: unknown): number | null {
  return isAxiosError(error) ? (error.response?.status ?? null) : null
}

export function getApiError(error: unknown): ApiErrorResponse | null {
  if (!isAxiosError(error)) {
    return null
  }

  const data: unknown = error.response?.data

  return isApiErrorResponse(data) ? data : null
}
