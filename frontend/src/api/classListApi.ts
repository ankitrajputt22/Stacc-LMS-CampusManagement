import type { ClassList, TaughtOffering } from '../types/classList'
import { apiClient } from './apiClient'

export async function getTaughtOfferings(signal?: AbortSignal): Promise<TaughtOffering[]> {
  const response = await apiClient.get<TaughtOffering[]>('/faculty/me/course-offerings', { signal })
  return response.data
}

export async function getClassList(courseOfferingId: number, signal?: AbortSignal): Promise<ClassList> {
  const response = await apiClient.get<ClassList>(`/faculty/me/course-offerings/${courseOfferingId}/students`, { signal })
  return response.data
}
