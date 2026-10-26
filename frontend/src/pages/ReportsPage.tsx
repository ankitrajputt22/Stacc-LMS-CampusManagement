import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { AdminReportsSection } from '../reports/AdminReportsSection'
import { FacultyReportsSection } from '../reports/FacultyReportsSection'

/**
 * Reports at /reports: one page, with a part for each role the account holds, in the fixed order Faculty, Admin. Each
 * part asks its own API, which decides access; showing a part protects nothing. Students have no report part.
 */
export function ReportsPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const faculty = hasAnyRole(roles, ['FACULTY'])
  const admin = hasAnyRole(roles, ['ADMIN'])
  const intro = faculty && admin
    ? 'Teaching, academic, and operational reports.'
    : faculty
      ? 'Teaching and course activity reports.'
      : 'Academic and operational reports.'

  return (
    <>
      <title>Reports · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Reports</h1>
      {faculty || admin ? (
        <>
          <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">{intro}</p>
          <div className="mt-6 space-y-10">
            {faculty && <FacultyReportsSection />}
            {admin && <AdminReportsSection />}
          </div>
        </>
      ) : (
        <div className="mt-6">
          <StatePanel>
            <AccessDeniedState title="Reports are for faculty and admin accounts." />
          </StatePanel>
        </div>
      )}
    </>
  )
}
