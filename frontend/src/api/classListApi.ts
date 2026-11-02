import type { ClassList, TaughtOffering } from '../types/classList'
import { apiClient } from './apiClient'

// The faculty member is the one in the access token; the backend checks the teaching assignment on every request.

export async function getTaughtOfferings(signal?: AbortSignal): Promise<TaughtOffering[]> {
  const response = await apiClient.get<TaughtOffering[]>('/faculty/me/course-offerings', { signal })
  return response.data
}

export async function getClassList(courseOfferingId: number, signal?: AbortSignal): Promise<ClassList> {
  const response = await apiClient.get<ClassList>(`/faculty/me/course-offerings/${courseOfferingId}/students`, { signal })
  return response.data
}
