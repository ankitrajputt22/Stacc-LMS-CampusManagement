import type {
  Authenticity,
  CareerApplication,
  CareerProfile,
  CertificateRequest,
  CertificateType,
  Company,
  IdCard,
  IdCheck,
  Opportunity,
  OpportunityKind,
  VaultDocument,
} from '../types/career'
import { apiClient } from './apiClient'
import { FILE_TIMEOUT_MS } from './lmsContentApi'

// Who is asking always comes from the session; files download through the API only.

export const openIdCard = async () => (await apiClient.post<IdCard>('/me/id-card')).data
export const checkIdCard = async (code: string) => (await apiClient.post<IdCheck>('/id-cards/check', { code })).data

export const getMyDocuments = async (signal?: AbortSignal) => (await apiClient.get<VaultDocument[]>('/me/documents', { signal })).data
export async function uploadDocument(title: string, file: File) {
  const form = new FormData()
  form.append('title', title)
  form.append('file', file)
  return (await apiClient.post<VaultDocument>('/me/documents', form, { timeout: FILE_TIMEOUT_MS })).data
}
export const downloadDocument = async (documentId: number) =>
  (await apiClient.get<Blob>(`/me/documents/${documentId}/download`, { responseType: 'blob', timeout: FILE_TIMEOUT_MS })).data
export const getMyCertificateRequests = async (signal?: AbortSignal) =>
  (await apiClient.get<CertificateRequest[]>('/me/certificate-requests', { signal })).data
export const requestCertificate = async (type: CertificateType, purpose: string) =>
  (await apiClient.post<CertificateRequest>('/me/certificate-requests', { type, purpose })).data
export const cancelCertificateRequest = async (requestId: number) =>
  (await apiClient.post<CertificateRequest>(`/me/certificate-requests/${requestId}/cancel`)).data
export const getCertificateQueue = async (status: string | null, signal?: AbortSignal) =>
  (await apiClient.get<CertificateRequest[]>('/admin/certificate-requests', { params: status ? { status } : {}, signal })).data
export async function issueCertificate(requestId: number, title: string, file: File) {
  const form = new FormData()
  form.append('title', title)
  form.append('file', file)
  return (await apiClient.post<CertificateRequest>(`/admin/certificate-requests/${requestId}/issue`, form, { timeout: FILE_TIMEOUT_MS })).data
}
export const refuseCertificate = async (requestId: number, note: string) =>
  (await apiClient.post<CertificateRequest>(`/admin/certificate-requests/${requestId}/refuse`, { note })).data
export const verifyDocument = async (code: string) =>
  (await apiClient.get<Authenticity>(`/public/documents/verify/${encodeURIComponent(code)}`)).data

export const getCareerProfile = async (signal?: AbortSignal) => (await apiClient.get<CareerProfile>('/career/profile', { signal })).data
export const saveCareerProfile = async (body: { headline: string | null; skills: string | null; links: string | null }) =>
  (await apiClient.put<CareerProfile>('/career/profile', body)).data
export async function addResume(label: string, file: File) {
  const form = new FormData()
  form.append('label', label)
  form.append('file', file)
  return (await apiClient.post<CareerProfile>('/career/resumes', form, { timeout: FILE_TIMEOUT_MS })).data
}
export const archiveResume = async (resumeId: number) => (await apiClient.post<CareerProfile>(`/career/resumes/${resumeId}/archive`)).data
export const downloadResume = async (resumeId: number) =>
  (await apiClient.get<Blob>(`/career/resumes/${resumeId}/download`, { responseType: 'blob', timeout: FILE_TIMEOUT_MS })).data
export const getOpenOpportunities = async (signal?: AbortSignal) => (await apiClient.get<Opportunity[]>('/career/opportunities', { signal })).data
export const applyToOpportunity = async (opportunityId: number, body: { resumeId: number | null; coverNote: string | null; shareConsent: boolean }) =>
  (await apiClient.post<CareerApplication>(`/career/opportunities/${opportunityId}/applications`, body)).data
export const getMyApplications = async (signal?: AbortSignal) => (await apiClient.get<CareerApplication[]>('/career/applications/mine', { signal })).data
export const withdrawApplication = async (applicationId: number) =>
  (await apiClient.post<CareerApplication>(`/career/applications/${applicationId}/withdraw`)).data
export const answerOffer = async (applicationId: number, accept: boolean) =>
  (await apiClient.post<CareerApplication>(`/career/applications/${applicationId}/offer/answer`, { accept })).data

export const getCompanies = async (signal?: AbortSignal) => (await apiClient.get<Company[]>('/career/manage/companies', { signal })).data
export const addCompany = async (body: { name: string; website: string | null; description: string | null }) =>
  (await apiClient.post<Company>('/career/manage/companies', body)).data
export const getManagedOpportunities = async (signal?: AbortSignal) => (await apiClient.get<Opportunity[]>('/career/manage/opportunities', { signal })).data
export const createOpportunity = async (body: {
  kind: OpportunityKind
  title: string
  companyId: number | null
  organization: string | null
  description: string | null
  programIds: number[]
  deadline: string
  stages: string[]
}) => (await apiClient.post<Opportunity>('/career/manage/opportunities', body)).data
export const setOpportunityOpen = async (opportunityId: number, open: boolean) =>
  (await apiClient.post<Opportunity>(`/career/manage/opportunities/${opportunityId}/${open ? 'open' : 'close'}`)).data
export const scheduleRound = async (opportunityId: number, body: { stageIndex: number; startsAt: string; venue: string | null }) =>
  (await apiClient.put<Opportunity>(`/career/manage/opportunities/${opportunityId}/rounds`, body)).data
export const getApplicants = async (opportunityId: number, signal?: AbortSignal) =>
  (await apiClient.get<CareerApplication[]>(`/career/manage/opportunities/${opportunityId}/applications`, { signal })).data
export const moveApplicant = async (applicationId: number, advance: boolean) =>
  (await apiClient.post<CareerApplication>(`/career/manage/applications/${applicationId}/move`, { advance })).data
export const makeOffer = async (applicationId: number, body: { roleTitle: string; compensation: string | null; respondBy: string | null }) =>
  (await apiClient.post<CareerApplication>(`/career/manage/applications/${applicationId}/offer`, body)).data
export const getPlacementPolicy = async (signal?: AbortSignal) =>
  (await apiClient.get<{ oneAcceptedPlacement: boolean }>('/career/manage/policy', { signal })).data
export const setPlacementPolicy = async (oneAcceptedPlacement: boolean) =>
  (await apiClient.put<{ oneAcceptedPlacement: boolean }>('/career/manage/policy', { oneAcceptedPlacement })).data
export const exportApplicants = async (opportunityId: number) =>
  (await apiClient.get<Blob>(`/career/manage/opportunities/${opportunityId}/applications/export.csv`, {
    responseType: 'blob',
    headers: { Accept: 'text/csv' },
  })).data
