// Global search exactly as the backend sends it (SearchResponses in com.stacc.backend.search). Results are plain
// text; the action path is an internal route that the destination page checks again.

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
  /** An internal route such as /courses/42; the page it opens checks access again. */
  actionPath: string
  occurredAt: string | null
}

export type SearchSection = {
  type: SearchResultType
  label: string
  /** How many results of this kind the signed-in account may see. */
  totalCount: number
  /** At most 5. */
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
