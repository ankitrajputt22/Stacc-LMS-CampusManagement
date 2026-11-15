import { getDepartmentAnalytics } from '../api/analyticsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { EmptyState } from '../components/states/EmptyState'
import { StatePanel } from '../components/states/StatePanel'
import { SectionState } from '../courses/SectionState'
import type { DepartmentAnalytics, DepartmentOfferingLine, DepartmentProgramLine } from '../types/analytics'
import { Card, DataTable, PageHeader } from '../ui'

function Department({ department }: { department: DepartmentAnalytics }) {
  return (
    <Card title={`${department.name} (${department.code})`} description={`Current session. ${department.note}`} padding="none">
      <div className="space-y-4 py-2">
        <DataTable<DepartmentProgramLine>
          caption={`Students by program in ${department.code}`}
          rows={department.programs}
          rowKey={(row) => row.code}
          empty={<p className="px-4 text-sm text-ink-muted">No programs.</p>}
          columns={[
            { key: 'program', header: 'Program', primary: true, cell: (row) => `${row.name} (${row.code})` },
            { key: 'students', header: 'Students enrolled', align: 'right', cell: (row) => row.students },
          ]}
        />
        <DataTable<DepartmentOfferingLine>
          caption={`Course offerings of ${department.code}`}
          rows={department.offerings}
          rowKey={(row) => row.courseOfferingId}
          empty={<p className="px-4 text-sm text-ink-muted">No offerings in the current session.</p>}
          columns={[
            { key: 'course', header: 'Course', primary: true, cell: (row) => `${row.courseCode} ${row.courseName}` },
            { key: 'semester', header: 'Semester', cell: (row) => `${row.programCode} · ${row.semesterNumber}` },
            { key: 'enrolled', header: 'Enrolled', align: 'right', cell: (row) => row.enrolled },
            { key: 'attendance', header: 'Attendance', align: 'right', cell: (row) => (row.attendancePercent === null ? '—' : `${row.attendancePercent}%`) },
            { key: 'results', header: 'Results published', align: 'right', cell: (row) => row.publishedResults },
          ]}
        />
      </div>
    </Card>
  )
}

/** /department: the head of department's view of the departments they head now. The backend decides which. */
export function DepartmentAnalyticsPage() {
  const { account } = useAuth()
  const isHead = hasAnyRole(account?.roles ?? [], ['HOD'])
  const departments = useApiQuery(isHead ? queryKeys.analytics.department() : null, (signal) => getDepartmentAnalytics(signal))
  return (
    <>
      <title>Department · Stacc</title>
      <PageHeader title="Department" description="Students, offerings, attendance, and results for the departments you head." />
      <div className="mt-6 space-y-6">
        {!isHead ? (
          <StatePanel><AccessDeniedState title="This view is for heads of department." /></StatePanel>
        ) : departments.status !== 'loaded' ? (
          <SectionState request={departments} what="department analytics" />
        ) : departments.data.length === 0 ? (
          <EmptyState title="You do not head a department right now." description="The college assigns department heads." />
        ) : (
          departments.data.map((department) => <Department key={department.departmentId} department={department} />)
        )}
      </div>
    </>
  )
}
