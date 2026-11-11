import type { Hackathon, JudgeHackathon, OfficeHackathon } from '../types/hackathons'
import { apiClient } from './apiClient'

// The student and judge are always the signed-in account; the backend checks teams, judges, and the clock.

export const getHackathons = async (signal?: AbortSignal) => (await apiClient.get<Hackathon[]>('/hackathons', { signal })).data
export async function formHackathonTeam(hackathonId: number, name: string) {
  await apiClient.post(`/hackathons/${hackathonId}/teams`, { name })
}
export async function joinHackathonTeam(teamId: number) {
  await apiClient.post(`/hackathons/teams/${teamId}/join`)
}
export async function leaveHackathonTeam(hackathonId: number) {
  await apiClient.post(`/hackathons/${hackathonId}/leave`)
}
export async function submitHackathonWork(hackathonId: number, input: { title: string; summary: string; link: string | null }) {
  await apiClient.post(`/hackathons/${hackathonId}/submission`, input)
}
export const getJudging = async (signal?: AbortSignal) => (await apiClient.get<JudgeHackathon[]>('/hackathons/judging', { signal })).data
export async function scoreSubmission(submissionId: number, score: number, comment: string | null) {
  await apiClient.post(`/hackathons/submissions/${submissionId}/score`, { score, comment })
}
export const getOfficeHackathons = async (signal?: AbortSignal) => (await apiClient.get<OfficeHackathon[]>('/admin/hackathons', { signal })).data
export async function createHackathon(input: { title: string; description: string | null; startsAt: string; endsAt: string; maxTeamSize: number }) {
  await apiClient.post('/admin/hackathons', input)
}
export async function publishHackathon(hackathonId: number) {
  await apiClient.post(`/admin/hackathons/${hackathonId}/publish`)
}
export async function addHackathonJudge(hackathonId: number, loginId: string) {
  await apiClient.post(`/admin/hackathons/${hackathonId}/judges`, { loginId })
}
export async function releaseHackathonResults(hackathonId: number) {
  await apiClient.post(`/admin/hackathons/${hackathonId}/release`)
}
