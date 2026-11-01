import { useParams } from 'react-router'
import { CalendarSection } from '../admin/CalendarSection'
import { EnrollmentsSection } from '../admin/EnrollmentsSection'
import { FacultyAssignmentsSection } from '../admin/FacultyAssignmentsSection'
import { OfferingRoster } from '../admin/OfferingRoster'
import { OfferingsSection } from '../admin/OfferingsSection'
import { FacultyProfilesSection, StudentProfilesSection } from '../admin/ProfilesSections'
import { SectionsSection } from '../admin/SectionsSection'
import {
  AcademicSessionsSection,
  CoursesSection,
  DepartmentsSection,
  ProgramsSection,
  SemestersSection,
} from '../admin/MasterSections'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { LinkTabs, PageHeader } from '../ui'
import { NotFoundPage } from './NotFoundPage'

const SECTIONS = [
  { path: 'departments', label: 'Departments', Section: DepartmentsSection },
  { path: 'programs', label: 'Programs', Section: ProgramsSection },
  { path: 'sessions', label: 'Academic Sessions', Section: AcademicSessionsSection },
  { path: 'semesters', label: 'Semesters', Section: SemestersSection },
  { path: 'sections', label: 'Sections', Section: SectionsSection },
  { path: 'courses', label: 'Courses', Section: CoursesSection },
  { path: 'offerings', label: 'Course Offerings', Section: OfferingsSection },
  { path: 'faculty-assignments', label: 'Faculty Assignments', Section: FacultyAssignmentsSection },
  { path: 'enrollments', label: 'Enrollments', Section: EnrollmentsSection },
  { path: 'students', label: 'Students', Section: StudentProfilesSection },
  { path: 'faculty', label: 'Faculty', Section: FacultyProfilesSection },
  { path: 'calendar', label: 'Academic Calendar', Section: CalendarSection },
] as const

const ID_PATTERN = /^[1-9]\d{0,17}$/

/**
 * The admin workspace for academic data, at /admin/academic/:section; /admin/academic opens the departments. The
 * reusable masters can be added and edited, course offerings added and moved through their status, students enrolled
 * in semesters and course offerings, and official calendar events prepared, published, and cancelled; nothing is
 * deleted. An offering's roster is at /admin/academic/offerings/:courseOfferingId/enrollments. Accounts without the
 * admin role see an explanation instead and no request is made; the backend checks the role and the permissions on
 * every request.
 */
export function AcademicAdminPage({ roster = false }: { roster?: boolean }) {
  const { account } = useAuth()
  const { section, courseOfferingId } = useParams()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const current = SECTIONS.find((candidate) => candidate.path === (roster ? 'offerings' : section))

  if (current === undefined) {
    return <NotFoundPage />
  }

  return (
    <div className="max-w-6xl">
      <title>{`${roster ? 'Offering Roster' : current.label} · Academic Admin · Stacc`}</title>
      <PageHeader
        eyebrow="ERP"
        title="Academic Admin"
        description="Manage academic masters, course offerings, official student enrollments, and the academic calendar."
      />
      {isAdmin ? (
        <>
          <LinkTabs
            label="Academic admin sections"
            tabs={SECTIONS.map((candidate) => ({ to: `/admin/academic/${candidate.path}`, label: candidate.label }))}
          />
          {roster ? (
            <OfferingRoster
              courseOfferingId={courseOfferingId && ID_PATTERN.test(courseOfferingId) ? Number(courseOfferingId) : null}
            />
          ) : (
            <current.Section />
          )}
        </>
      ) : (
        <StatePanel>
          <AccessDeniedState
            title="This page is for admin accounts."
            message="If you think you should, contact the college administration."
          />
        </StatePanel>
      )}
    </div>
  )
}
