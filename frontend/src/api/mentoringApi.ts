import type { ActionOwner, MentoringPairing, MentoringPairingDetail } from '../types/mentoring'
import { apiClient } from './apiClient'

// The mentor and the student are always the signed-in account; the backend checks each pairing.

export const getMyPairings = async (signal?: AbortSignal) => (await apiClient.get<MentoringPairing[]>('/mentoring/me', { signal })).data
export const getPairing = async (assignmentId: number, signal?: AbortSignal) =>
  (await apiClient.get<MentoringPairingDetail>(`/mentoring/pairings/${assignmentId}`, { signal })).data
export const addMeeting = async (assignmentId: number, input: { heldAt: string; title: string; sharedNotes: string | null; privateNotes: string | null }) =>
  (await apiClient.post<MentoringPairingDetail>(`/mentoring/pairings/${assignmentId}/meetings`, input)).data
export const addActionItem = async (meetingId: number, input: { description: string; owner: ActionOwner; dueAt: string | null }) =>
  (await apiClient.post<MentoringPairingDetail>(`/mentoring/meetings/${meetingId}/action-items`, input)).data
export const completeActionItem = async (itemId: number) =>
  (await apiClient.post<MentoringPairingDetail>(`/mentoring/action-items/${itemId}/complete`)).data

export const getAllPairings = async (signal?: AbortSignal) => (await apiClient.get<MentoringPairing[]>('/admin/mentoring/pairings', { signal })).data
export const pairMentor = async (mentorLoginId: string, menteeLoginId: string) =>
  (await apiClient.post<MentoringPairing>('/admin/mentoring/pairings', { mentorLoginId, menteeLoginId })).data
export const endPairing = async (assignmentId: number) => (await apiClient.post<MentoringPairing>(`/admin/mentoring/pairings/${assignmentId}/end`)).data
