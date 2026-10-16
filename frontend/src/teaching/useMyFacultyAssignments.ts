import { getMyFacultyAssignments } from '../api/lmsAssignmentsApi'
import { useApiRequest } from '../api/useApiRequest'

/** The assignments of every course the signed-in faculty member teaches, as the backend lists them. */
export function useMyFacultyAssignments() {
  return useApiRequest(getMyFacultyAssignments)
}
