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
import { useId } from 'react'
import { DataTable, PageHeader } from '../ui'

function SubHeading({ children }: { children: string }) {
  return (
    <div className="px-4 sm:px-5">
      <h3 className="text-xl font-bold text-ink">{children}</h3>
      <span aria-hidden="true" className="mt-1.5 block h-0.5 w-12 rounded bg-portal-cta" />
    </div>
  )
}

function Department({ department }: { department: DepartmentAnalytics }) {
  const id = useId()
  return (
    <section aria-labelledby={id} className="rounded-panel border border-line bg-surface">
      <div className="px-4 pt-4 sm:px-5">
        <h2 id={id} className="text-2xl font-bold text-ink">
          <span className="mr-3">{department.code}</span>
          {department.name}
          <span className="sr-only"> ({department.code})</span>
        </h2>
        <span className="mt-2 inline-block rounded-sm bg-sky-100 px-3 py-1 text-sm font-bold text-portal-navy">Current session</span>
      </div>
      <div className="m-3 space-y-5 rounded-panel border border-line py-4 sm:m-4">
        <SubHeading>Students by program</SubHeading>
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
        <SubHeading>Offerings</SubHeading>
        <DataTable<DepartmentOfferingLine>
          caption={`Course offerings of ${department.code}`}
          rows={department.offerings}
          rowKey={(row) => row.courseOfferingId}
          empty={<p className="px-4 text-sm text-ink-muted">No offerings in the current session.</p>}
          columns={[
            { key: 'code', header: 'Code', primary: true, cell: (row) => row.courseCode },
            { key: 'course', header: 'Course', cell: (row) => row.courseName },
            { key: 'program', header: 'Program', cell: (row) => row.programCode },
            { key: 'semester', header: 'Semester', align: 'right', cell: (row) => row.semesterNumber },
            { key: 'enrolled', header: 'Enrolled', align: 'right', cell: (row) => row.enrolled },
            { key: 'attendance', header: 'Attendance', align: 'right', cell: (row) => (row.attendancePercent === null ? '—' : `${row.attendancePercent}%`) },
            { key: 'results', header: 'Results published', align: 'right', cell: (row) => row.publishedResults },
          ]}
        />
        <p className="px-4 text-[0.95rem] text-ink sm:px-5">
          <span className="font-bold">Note:</span> {department.note}
        </p>
      </div>
    </section>
  )
}

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
