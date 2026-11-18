export const SEARCH_RESULT_TYPES = [
  'COURSE',
  'TEACHING_COURSE',
  'ASSIGNMENT',
  'TEACHING_ASSIGNMENT',
  'STUDY_MATERIAL',
  'TEACHING_MATERIAL',
  'ANNOUNCEMENT',
  'TEACHING_ANNOUNCEMENT',
  'ACADEMIC_CALENDAR_EVENT',
  'CAMPUS_NOTICE',
  'HELPDESK_TICKET',
  'CAMPUS_SERVICE_REQUEST',
  'HOSTEL_APPLICATION',
  'DEPARTMENT',
  'PROGRAM',
  'ACADEMIC_SESSION',
  'SEMESTER',
  'CATALOG_COURSE',
  'COURSE_OFFERING',
] as const

export type SearchResultType = (typeof SEARCH_RESULT_TYPES)[number]

export type SearchResultItem = {
  type: SearchResultType
  title: string
  subtitle: string | null
  snippet: string | null
  actionPath: string
  occurredAt: string | null
}

export type SearchSection = {
  type: SearchResultType
  label: string
  totalCount: number
  items: SearchResultItem[]
}

export type GlobalSearchResponse = {
  query: string
  sections: SearchSection[]
}

export type SearchResultPage = {
  query: string
  type: SearchResultType
  label: string
  items: SearchResultItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}
