import type {
  AllocationQuery,
  ApplicationQueueQuery,
  AvailableHostelBed,
  EligibleHostelSession,
  Hostel,
  HostelAdminApplicationDetail,
  HostelAdminApplicationSummary,
  HostelAllocation,
  HostelApplicationDetail,
  HostelApplicationSummary,
  HostelBed,
  HostelRoom,
  Page,
} from '../types/hostel'
import { apiClient } from './apiClient'

// The client's base URL already ends in /api, and only the client sets the Authorization header. No request names a
// student, profile, or acting account: those come from the access token.

// --- Student

export async function getEligibleHostelSessions(signal?: AbortSignal) {
  return (await apiClient.get<EligibleHostelSession[]>('/hostel/student/eligible-sessions', { signal })).data
}

export async function getMyHostelApplications(signal?: AbortSignal) {
  return (await apiClient.get<HostelApplicationSummary[]>('/hostel/student/applications', { signal })).data
}

export async function getMyHostelApplication(applicationId: number, signal?: AbortSignal) {
  return (await apiClient.get<HostelApplicationDetail>(`/hostel/student/applications/${applicationId}`, { signal })).data
}

export async function applyForHostel(request: { academicSessionId: number; note: string | null }) {
  return (await apiClient.post<HostelApplicationDetail>('/hostel/student/applications', request)).data
}

export async function cancelHostelApplication(applicationId: number) {
  return (await apiClient.post<HostelApplicationDetail>(`/hostel/student/applications/${applicationId}/cancel`)).data
}

// --- Admin: inventory

export async function getHostels(signal?: AbortSignal) {
  return (await apiClient.get<Hostel[]>('/hostel/admin/hostels', { signal })).data
}

export async function createHostel(request: { code: string; name: string; description: string | null }) {
  return (await apiClient.post<Hostel>('/hostel/admin/hostels', request)).data
}

export async function updateHostel(hostelId: number, request: { name: string; description: string | null; active: boolean }) {
  return (await apiClient.put<Hostel>(`/hostel/admin/hostels/${hostelId}`, request)).data
}

export async function getHostelRooms(hostelId: number, signal?: AbortSignal) {
  return (await apiClient.get<HostelRoom[]>(`/hostel/admin/hostels/${hostelId}/rooms`, { signal })).data
}

export async function createHostelRoom(hostelId: number, request: { roomNumber: string }) {
  return (await apiClient.post<HostelRoom>(`/hostel/admin/hostels/${hostelId}/rooms`, request)).data
}

export async function updateHostelRoom(roomId: number, request: { roomNumber: string; active: boolean }) {
  return (await apiClient.put<HostelRoom>(`/hostel/admin/rooms/${roomId}`, request)).data
}

export async function getHostelBeds(roomId: number, signal?: AbortSignal) {
  return (await apiClient.get<HostelBed[]>(`/hostel/admin/rooms/${roomId}/beds`, { signal })).data
}

export async function createHostelBed(roomId: number, request: { bedNumber: string }) {
  return (await apiClient.post<HostelBed>(`/hostel/admin/rooms/${roomId}/beds`, request)).data
}

export async function updateHostelBed(bedId: number, request: { bedNumber: string; active: boolean }) {
  return (await apiClient.put<HostelBed>(`/hostel/admin/beds/${bedId}`, request)).data
}

// --- Admin: applications and allocations

export async function getHostelApplications(query: ApplicationQueueQuery, signal?: AbortSignal) {
  return (await apiClient.get<Page<HostelAdminApplicationSummary>>('/hostel/admin/applications', { params: query, signal })).data
}

export async function getHostelApplication(applicationId: number, signal?: AbortSignal) {
  return (await apiClient.get<HostelAdminApplicationDetail>(`/hostel/admin/applications/${applicationId}`, { signal })).data
}

export async function getAvailableBeds(signal?: AbortSignal) {
  return (await apiClient.get<AvailableHostelBed[]>('/hostel/admin/available-beds', { signal })).data
}

export async function allocateBed(applicationId: number, bedId: number) {
  return (await apiClient.post<HostelAllocation>(`/hostel/admin/applications/${applicationId}/allocations`, { bedId })).data
}

export async function getHostelAllocations(query: AllocationQuery, signal?: AbortSignal) {
  return (await apiClient.get<Page<HostelAllocation>>('/hostel/admin/allocations', { params: query, signal })).data
}

export async function vacateAllocation(allocationId: number) {
  return (await apiClient.post<HostelAllocation>(`/hostel/admin/allocations/${allocationId}/vacate`)).data
}
