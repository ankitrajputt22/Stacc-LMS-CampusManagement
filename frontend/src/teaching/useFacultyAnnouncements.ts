import { useMemo } from 'react'
import { getFacultyAnnouncements } from '../api/lmsAnnouncementsApi'
import { useApiRequest } from '../api/useApiRequest'

/**
 * The announcements of a course the signed-in faculty member teaches. refresh() reloads them quietly
 * after a change. The backend decides access from official teaching assignments on every request.
 */
export function useFacultyAnnouncements(lmsCourseId: number) {
  const request = useMemo(
    () => (signal: AbortSignal) => getFacultyAnnouncements(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
