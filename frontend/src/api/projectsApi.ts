import type { Project, ProjectStatus } from '../types/projects'
import { apiClient } from './apiClient'

// The student or faculty member is always the signed-in account; the backend checks the lead and each invitation.

export const getProjects = async (signal?: AbortSignal) => (await apiClient.get<Project[]>('/projects', { signal })).data
export const createProject = async (input: { title: string; summary: string; maxMembers: number }) =>
  (await apiClient.post<Project>('/projects', input)).data
export const setProjectStatus = async (projectId: number, status: ProjectStatus) =>
  (await apiClient.post<Project>(`/projects/${projectId}/status`, { status })).data
export const askToJoinProject = async (projectId: number, message: string | null) =>
  (await apiClient.post<Project>(`/projects/${projectId}/join`, { message })).data
export const decideJoinRequest = async (memberId: number, accept: boolean) =>
  (await apiClient.post<Project>(`/projects/members/${memberId}/decision`, { accept })).data
export const leaveProject = async (memberId: number) => (await apiClient.post<Project>(`/projects/members/${memberId}/leave`)).data
export const inviteProjectMentor = async (projectId: number, loginId: string) =>
  (await apiClient.post<Project>(`/projects/${projectId}/mentors`, { loginId })).data
export const answerProjectMentorship = async (mentorId: number, accept: boolean) =>
  (await apiClient.post<Project>(`/projects/mentors/${mentorId}/answer`, { accept })).data
export const stepDownAsMentor = async (mentorId: number) => (await apiClient.post<Project>(`/projects/mentors/${mentorId}/step-down`)).data
