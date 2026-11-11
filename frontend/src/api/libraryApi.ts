import type { DeskLoan, LibraryCopy, LibraryLoan, LibraryResource, LibraryTitle, MyLibrary } from '../types/library'
import { apiClient } from './apiClient'

// Members and librarians are always the signed-in account; the backend checks loans, holds, and the queue.

export const searchLibrary = async (q: string, signal?: AbortSignal) => (await apiClient.get<LibraryTitle[]>('/library/titles', { params: { q }, signal })).data
export const getMyLibrary = async (signal?: AbortSignal) => (await apiClient.get<MyLibrary>('/library/me', { signal })).data
export async function placeLibraryHold(titleId: number) {
  await apiClient.post(`/library/titles/${titleId}/hold`)
}
export async function cancelLibraryHold(holdId: number) {
  await apiClient.post(`/library/holds/${holdId}/cancel`)
}
export async function renewLibraryLoan(loanId: number) {
  await apiClient.post(`/library/loans/${loanId}/renew`)
}
export const getLibraryResources = async (signal?: AbortSignal) => (await apiClient.get<LibraryResource[]>('/library/resources', { signal })).data

export const searchDesk = async (q: string, signal?: AbortSignal) => (await apiClient.get<LibraryTitle[]>('/library/desk/titles', { params: { q }, signal })).data
export const addLibraryTitle = async (input: { title: string; authors: string | null; isbn: string | null; publisher: string | null; publishedYear: number | null; subject: string | null }) =>
  (await apiClient.post<LibraryTitle>('/library/desk/titles', input)).data
export const getLibraryCopies = async (titleId: number, signal?: AbortSignal) => (await apiClient.get<LibraryCopy[]>(`/library/desk/titles/${titleId}/copies`, { signal })).data
export async function addLibraryCopy(titleId: number, barcode: string, shelf: string | null) {
  await apiClient.post(`/library/desk/titles/${titleId}/copies`, { barcode, shelf })
}
export const lendCopy = async (barcode: string, loginId: string) => (await apiClient.post<LibraryLoan>('/library/desk/issue', { barcode, loginId })).data
/** Takes a copy back; answers the borrower's login ID. */
export const takeBackCopy = async (barcode: string) => (await apiClient.post<string>('/library/desk/return', { barcode })).data
export const getOpenLoans = async (signal?: AbortSignal) => (await apiClient.get<DeskLoan[]>('/library/desk/loans', { signal })).data
export const getAllLibraryResources = async (signal?: AbortSignal) => (await apiClient.get<LibraryResource[]>('/library/desk/resources', { signal })).data
export const addLibraryResource = async (input: { title: string; description: string | null; url: string }) =>
  (await apiClient.post<LibraryResource>('/library/desk/resources', input)).data
export async function setLibraryResourceActive(resourceId: number, active: boolean) {
  await apiClient.post(`/library/desk/resources/${resourceId}/active`, { active })
}
