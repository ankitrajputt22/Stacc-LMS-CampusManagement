import { useMemo } from 'react'
import { getCourseAnnouncements } from '../api/lmsAnnouncementsApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * The published announcements of one course for the signed-in student. With no valid ID nothing is sent.
 * The backend decides access again on every request and sends published announcements only.
 */
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
