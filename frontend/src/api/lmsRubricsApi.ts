import type { LmsFacultyAssignment, LmsRubric, LmsRubricInput, LmsSubmissionGrade } from '../types/lms'
import { apiClient } from './apiClient'

export async function getRubrics(lmsCourseId: number, signal?: AbortSignal): Promise<LmsRubric[]> {
  return (await apiClient.get<LmsRubric[]>(`/lms/faculty/courses/${lmsCourseId}/rubrics`, { signal })).data
}

export async function createRubric(lmsCourseId: number, input: LmsRubricInput): Promise<LmsRubric> {
  return (await apiClient.post<LmsRubric>(`/lms/faculty/courses/${lmsCourseId}/rubrics`, input)).data
}

export async function editRubric(rubricId: number, input: LmsRubricInput): Promise<LmsRubric> {
  return (await apiClient.put<LmsRubric>(`/lms/faculty/rubrics/${rubricId}`, input)).data
}

export async function archiveRubric(rubricId: number): Promise<LmsRubric> {
  return (await apiClient.post<LmsRubric>(`/lms/faculty/rubrics/${rubricId}/archive`)).data
}

export async function chooseAssignmentRubric(assignmentId: number, rubricId: number | null): Promise<LmsFacultyAssignment> {
  return (await apiClient.put<LmsFacultyAssignment>(`/lms/faculty/assignments/${assignmentId}/rubric`, { rubricId })).data
}

export type RubricScoreInput = { criterionId: number; points: number; comment: string | null }

export async function gradeWithRubric(
  attemptId: number,
  scores: RubricScoreInput[],
  feedback: string | null,
): Promise<LmsSubmissionGrade> {
  return (await apiClient.put<LmsSubmissionGrade>(`/lms/faculty/submission-attempts/${attemptId}/rubric-grade`, { scores, feedback })).data
}
