import { Link } from 'react-router'
import { getMyAcademicProfile } from '../api/academicProfileApi'
import { getMyAttendance } from '../api/attendanceApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { PageHeader } from '../ui'
import { currentCourses, totalCredits } from './erpData'

function Tile({ to, icon, title, summary }: { to: string; icon: NavIconName; title: string; summary: string | null }) {
  return (
    <Link
      to={to}
      className="group flex flex-col rounded-panel border border-line bg-surface p-6 shadow-[0_1px_2px_rgba(15,23,42,0.05)] hover:border-line-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stacc-primary"
    >
      <NavIcon name={icon} className="size-11 text-ink" />
      <span className="mt-4 text-2xl font-bold text-ink">{title}</span>
      <span className="mt-1 min-h-7 text-lg text-ink-muted">{summary ?? ''}</span>
      <span className="mt-4 flex items-center gap-1 border-t border-line pt-4 text-lg font-semibold text-portal-cta">
        Open
        <NavIcon name="chevronRight" className="size-5 transition-transform group-hover:translate-x-0.5" />
      </span>
    </Link>
  )
}

function Banner({ tone, children }: { tone: 'warning' | 'danger'; children: string }) {
  const classes = tone === 'danger' ? 'border-red-300 bg-red-50 text-danger' : 'border-amber-300 bg-amber-50 text-warning'
  return (
    <div className={`flex items-center gap-4 rounded-panel border px-5 py-3.5 ${classes}`}>
      <NavIcon name="warning" className="size-7" />
      <span aria-hidden="true" className="h-8 w-px bg-current opacity-40" />
      <p className="text-lg font-semibold text-ink">{children}</p>
    </div>
  )
}

/**
 * The ERP home: alerts that need attention, then one tile per ERP area with a live one-line summary. Every figure comes
 * from the backend's own rules (attendance below the minimum is decided there); nothing is made up.
 */
export function ErpHomePage() {
  const { account } = useAuth()
  const student = hasAnyRole(account?.roles ?? [], ['STUDENT'])
  const academic = useApiQuery(
    hasAnyRole(account?.roles ?? [], ['STUDENT', 'FACULTY']) ? queryKeys.academic.myProfile() : null,
    getMyAcademicProfile,
  )
  const attendance = useApiQuery(student ? queryKeys.attendance.mine() : null, getMyAttendance)

  const studentPart = academic.status === 'loaded' ? academic.data.student : null
  const low = attendance.status === 'loaded' ? attendance.data.courses.filter((course) => course.lowAttendance) : []
  const courses = studentPart ? currentCourses(studentPart) : []

  return (
    <>
      <title>ERP · Stacc</title>
      <PageHeader title="ERP" description="Your academic, attendance, exams and fee information in one place." />

      {low.length > 0 && (
        <div role="status" className="mb-6 space-y-3">
          {low.map((course) => (
            <Banner key={course.courseOfferingId} tone="danger">
              {`Attendance below 75% in ${course.courseCode} — ${Math.round(course.attendancePercentage ?? 0)}%`}
            </Banner>
          ))}
        </div>
      )}

      <div className="grid gap-6 md:grid-cols-2 xl:grid-cols-3">
        <Tile
          to="/erp/profile"
          icon="profile"
          title="My Profile"
          summary={studentPart ? [studentPart.rollNumber ?? studentPart.loginId, studentPart.status === 'ACTIVE' ? 'Active' : 'Inactive'].join(' · ') : (account?.loginId ?? null)}
        />
        {student && (
          <>
            <Tile
              to="/erp/academics"
              icon="academics"
              title="Academics"
              summary={studentPart?.current ? `Semester ${studentPart.current.semesterNumber} · ${totalCredits(courses)} credits` : null}
            />
            <Tile
              to="/erp/attendance"
              icon="attendance"
              title="Attendance"
              summary={
                attendance.status !== 'loaded' ? null
                  : low.length > 0 ? `${low.map((course) => course.courseCode).join(', ')} needs attention`
                  : attendance.data.courses.length > 0 ? 'All courses at 75% or above' : 'No attendance recorded yet'
              }
            />
            <Tile
              to="/erp/exams"
              icon="exams"
              title="Exams & Results"
              summary={studentPart ? `${studentPart.semesters.flatMap((semester) => semester.courses).filter((course) => course.result).length} published results` : null}
            />
          </>
        )}
      </div>
    </>
  )
}
