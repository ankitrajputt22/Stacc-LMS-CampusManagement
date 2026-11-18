import { useMemo } from 'react'
import { getFacultyAnnouncements } from '../api/lmsAnnouncementsApi'
import { useApiRequest } from '../api/useApiRequest'

export function useFacultyAnnouncements(lmsCourseId: number) {
  const request = useMemo(
    () => (signal: AbortSignal) => getFacultyAnnouncements(lmsCourseId, signal),
    [lmsCourseId],
  )

  return useApiRequest(request)
}
