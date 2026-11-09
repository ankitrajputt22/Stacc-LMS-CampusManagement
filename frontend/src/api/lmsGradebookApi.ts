import type { Gradebook, GradebookColumn, GradeItemType, StudentGrades } from '../types/lms'
import { apiClient } from './apiClient'

// Faculty routes check on every request that the signed-in account teaches the course; the student route reads only the
// signed-in student's own published grades.

export async function getGradebook(lmsCourseId: number, signal?: AbortSignal): Promise<Gradebook> {
  return (await apiClient.get<Gradebook>(`/lms/faculty/courses/${lmsCourseId}/gradebook`, { signal })).data
}

export type GradeItemInput = { title: string; maxMarks: number }

/** Adds a manual item, such as a class test on paper. */
export async function addGradeItem(lmsCourseId: number, input: GradeItemInput): Promise<{ id: number }> {
  return (await apiClient.post<{ id: number }>(`/lms/faculty/courses/${lmsCourseId}/gradebook/items`, input)).data
}

export async function editGradeItem(itemId: number, input: GradeItemInput): Promise<{ id: number }> {
  return (await apiClient.put<{ id: number }>(`/lms/faculty/gradebook/items/${itemId}`, input)).data
}

/** Shows a column's grades to students from now on; nothing unpublishes. */
export async function publishGradeColumn(lmsCourseId: number, type: GradeItemType, sourceId: number): Promise<GradebookColumn> {
  return (await apiClient.post<GradebookColumn>(`/lms/faculty/courses/${lmsCourseId}/gradebook/publish`, { type, sourceId })).data
}

/** Enters one student's marks for a manual item, replacing earlier ones. */
export async function enterGrade(itemId: number, courseEnrollmentId: number, marks: number, feedback: string | null): Promise<void> {
  await apiClient.put(`/lms/faculty/gradebook/items/${itemId}/entries/${courseEnrollmentId}`, { marks, feedback })
}

export async function getMyGrades(lmsCourseId: number, signal?: AbortSignal): Promise<StudentGrades> {
  return (await apiClient.get<StudentGrades>(`/lms/courses/${lmsCourseId}/grades`, { signal })).data
}
