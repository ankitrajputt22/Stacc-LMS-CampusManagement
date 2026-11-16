import type { DegreeProgress, MyTimetable, Standing } from '../types/erp'
import { apiClient } from './apiClient'

/** The signed-in account's weekly classes: GET /api/v1/timetable/me. */
export async function getMyTimetable(signal?: AbortSignal): Promise<MyTimetable> {
  return (await apiClient.get<MyTimetable>('/timetable/me', { signal })).data
}

/** The signed-in student's degree progress: GET /api/v1/academic/me/degree-progress. */
export async function getMyDegreeProgress(signal?: AbortSignal): Promise<DegreeProgress> {
  return (await apiClient.get<DegreeProgress>('/academic/me/degree-progress', { signal })).data
}

/** The signed-in student's grades, SGPA, and CGPA: GET /api/v1/results/student/me/standing. */
export async function getMyStanding(signal?: AbortSignal): Promise<Standing> {
  return (await apiClient.get<Standing>('/results/student/me/standing', { signal })).data
}
