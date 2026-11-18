import { useMemo } from 'react'
import { getCourseAnnouncements } from '../api/lmsAnnouncementsApi'
import { useApiRequest } from '../api/useApiRequest'

export function useCourseAnnouncements(lmsCourseId: number | null) {
  const request = useMemo(
    () =>
      lmsCourseId === null
        ? null
        : (signal: AbortSignal) => getCourseAnnouncements(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
