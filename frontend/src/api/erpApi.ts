import type { DegreeProgress, ExamSession, ExamSlot, GradeScaleEntry, MyExam, MyTimetable, RequestKind, RequestStatus, Standing, StudentRequest } from '../types/erp'
import { apiClient } from './apiClient'

export async function getMyTimetable(signal?: AbortSignal): Promise<MyTimetable> {
  return (await apiClient.get<MyTimetable>('/timetable/me', { signal })).data
}

export async function getMyDegreeProgress(signal?: AbortSignal): Promise<DegreeProgress> {
  return (await apiClient.get<DegreeProgress>('/academic/me/degree-progress', { signal })).data
}

export async function getMyStanding(signal?: AbortSignal): Promise<Standing> {
  return (await apiClient.get<Standing>('/results/student/me/standing', { signal })).data
}

export async function getMyRequests(signal?: AbortSignal): Promise<StudentRequest[]> {
  return (await apiClient.get<StudentRequest[]>('/erp/requests', { signal })).data
}

export async function requestMedicalLeave(body: { fromDate: string; toDate: string; reason: string }): Promise<StudentRequest> {
  return (await apiClient.post<StudentRequest>('/erp/requests/medical-leave', body)).data
}

export async function requestCorrection(body: { attendanceRecordId: number; reason: string }): Promise<StudentRequest> {
  return (await apiClient.post<StudentRequest>('/erp/requests/attendance-correction', body)).data
}

export async function requestRevaluation(body: { courseResultId: number; reason: string }): Promise<StudentRequest> {
  return (await apiClient.post<StudentRequest>('/erp/requests/revaluation', body)).data
}

export async function cancelRequest(requestId: number): Promise<StudentRequest> {
  return (await apiClient.post<StudentRequest>(`/erp/requests/${requestId}/cancel`)).data
}

export async function getRequestQueue(
  filters: { kind: RequestKind | ''; status: RequestStatus | ''; q: string },
  signal?: AbortSignal,
): Promise<StudentRequest[]> {
  const params = Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== ''))
  return (await apiClient.get<StudentRequest[]>('/admin/erp/requests', { params, signal })).data
}

export async function approveRequest(requestId: number, body: { comment: string | null; newMarks: number | null }): Promise<StudentRequest> {
  return (await apiClient.post<StudentRequest>(`/admin/erp/requests/${requestId}/approve`, body)).data
}

export async function rejectRequest(requestId: number, comment: string): Promise<StudentRequest> {
  return (await apiClient.post<StudentRequest>(`/admin/erp/requests/${requestId}/reject`, { comment, newMarks: null })).data
}

export async function getMyExams(signal?: AbortSignal): Promise<MyExam[]> {
  return (await apiClient.get<MyExam[]>('/erp/exams/me', { signal })).data
}

export async function getGradeScale(signal?: AbortSignal): Promise<GradeScaleEntry[]> {
  return (await apiClient.get<GradeScaleEntry[]>('/academic/grade-scale', { signal })).data
}

export async function getExamSessions(semesterId: number, signal?: AbortSignal): Promise<ExamSession[]> {
  return (await apiClient.get<ExamSession[]>('/admin/erp/exams', { params: { semesterId }, signal })).data
}

export async function createExamSession(semesterId: number, name: string): Promise<ExamSession> {
  return (await apiClient.post<ExamSession>('/admin/erp/exams', { semesterId, name })).data
}

export async function addExam(examSessionId: number, body: {
  courseOfferingId: number; examDate: string; startsAt: string; endsAt: string; hall: string; hallSeats: number
}): Promise<ExamSlot> {
  return (await apiClient.post<ExamSlot>(`/admin/erp/exams/${examSessionId}/exams`, body)).data
}

export async function removeExam(slotId: number): Promise<void> {
  await apiClient.post(`/admin/erp/exams/slots/${slotId}/remove`)
}

export async function generateSeatPlan(slotId: number): Promise<{ slotId: number; hall: string; hallSeats: number; seatsGiven: number; added: number }> {
  return (await apiClient.post(`/admin/erp/exams/slots/${slotId}/seat-plan`)).data
}

export async function publishExamSession(examSessionId: number): Promise<ExamSession> {
  return (await apiClient.post<ExamSession>(`/admin/erp/exams/${examSessionId}/publish`)).data
}
