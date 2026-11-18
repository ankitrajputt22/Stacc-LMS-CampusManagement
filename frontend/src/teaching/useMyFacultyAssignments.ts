import { getMyFacultyAssignments } from '../api/lmsAssignmentsApi'
import { useApiRequest } from '../api/useApiRequest'

export function useMyFacultyAssignments() {
  return useApiRequest(getMyFacultyAssignments)
}
