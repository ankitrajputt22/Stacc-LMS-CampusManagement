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
  HostelBedMap,
  HostelOccupancy,
  HostelQueuePlace,
  HostelRoom,
  HostelWardenAssignment,
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

export async function applyForHostel(request: {
  academicSessionId: number
  note: string | null
  preferredHostelId?: number | null
  preferenceNote?: string | null
}) {
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

// Structure, wardens, occupancy, bed map, check-in (v0.10.1, v0.10.2, v0.10.12).

export async function getHostelOccupancy(side: 'admin' | 'warden', signal?: AbortSignal) {
  return (await apiClient.get<HostelOccupancy[]>(side === 'admin' ? '/hostel/admin/occupancy' : '/hostel/warden/occupancy', { signal })).data
}

export async function getHostelBedMap(side: 'admin' | 'warden', hostelId: number, signal?: AbortSignal) {
  return (await apiClient.get<HostelBedMap>(`/hostel/${side}/hostels/${hostelId}/bed-map`, { signal })).data
}

export async function checkInAllocation(side: 'admin' | 'warden', allocationId: number, note: string | null) {
  await apiClient.post(`/hostel/${side}/allocations/${allocationId}/check-in`, { note })
}

export async function vacateAllocationWithNote(allocationId: number, note: string | null) {
  await apiClient.post(`/hostel/admin/allocations/${allocationId}/vacate-with-note`, { note })
}

export async function setBedMaintenance(bedId: number, underMaintenance: boolean) {
  await apiClient.put(`/hostel/admin/beds/${bedId}/maintenance`, { underMaintenance })
}

export async function describeHostelRoom(
  roomId: number,
  details: { block: string | null; floorNumber: number | null; roomType: string | null; notes: string | null },
) {
  await apiClient.put(`/hostel/admin/rooms/${roomId}/details`, details)
}

export async function getHostelWardens(hostelId: number, signal?: AbortSignal) {
  return (await apiClient.get<HostelWardenAssignment[]>(`/hostel/admin/hostels/${hostelId}/wardens`, { signal })).data
}

export async function assignHostelWarden(hostelId: number, loginId: string, block: string | null) {
  return (await apiClient.post<HostelWardenAssignment>(`/hostel/admin/hostels/${hostelId}/wardens`, { loginId, block })).data
}

export async function endHostelWarden(assignmentId: number) {
  return (await apiClient.post<HostelWardenAssignment>(`/hostel/admin/wardens/${assignmentId}/end`)).data
}

export async function getHostelQueuePlace(applicationId: number, signal?: AbortSignal) {
  return (await apiClient.get<HostelQueuePlace>(`/hostel/student/applications/${applicationId}/queue`, { signal })).data
}
