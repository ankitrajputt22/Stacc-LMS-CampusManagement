import { useParams } from 'react-router'
import { CalendarSection } from '../admin/CalendarSection'
import { DepartmentHeadsSection } from '../admin/DepartmentHeadsSection'
import { EnrollmentMonitorSection } from '../admin/EnrollmentMonitorSection'
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

const GROUPS = [
  { id: 'structure', label: 'Structure' },
  { id: 'teaching', label: 'Teaching' },
  { id: 'people', label: 'People' },
  { id: 'calendar', label: 'Calendar' },
] as const

const SECTIONS = [
  { path: 'departments', group: 'structure', label: 'Departments', Section: DepartmentsSection },
  { path: 'programs', group: 'structure', label: 'Programs', Section: ProgramsSection },
  { path: 'sessions', group: 'structure', label: 'Academic Sessions', Section: AcademicSessionsSection },
  { path: 'semesters', group: 'structure', label: 'Semesters', Section: SemestersSection },
  { path: 'sections', group: 'structure', label: 'Sections', Section: SectionsSection },
  { path: 'courses', group: 'structure', label: 'Courses', Section: CoursesSection },
  { path: 'offerings', group: 'teaching', label: 'Course Offerings', Section: OfferingsSection },
  { path: 'faculty-assignments', group: 'teaching', label: 'Faculty Assignments', Section: FacultyAssignmentsSection },
  { path: 'enrollments', group: 'people', label: 'Enrollments', Section: EnrollmentsSection },
  { path: 'course-enrollments', group: 'people', label: 'Course Enrollments', Section: EnrollmentMonitorSection },
  { path: 'students', group: 'people', label: 'Students', Section: StudentProfilesSection },
  { path: 'faculty', group: 'people', label: 'Faculty', Section: FacultyProfilesSection },
  { path: 'department-heads', group: 'people', label: 'Department Heads', Section: DepartmentHeadsSection },
  { path: 'calendar', group: 'calendar', label: 'Academic Calendar', Section: CalendarSection },
] as const

const ID_PATTERN = /^[1-9]\d{0,17}$/

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
        title="Academic Admin"
        description="Manage academic structure, teaching, students and faculty, enrollment, and the academic calendar."
      />
      {isAdmin ? (
        <>
          <LinkTabs
            variant="pill"
            label="Academic admin areas"
            tabs={GROUPS.map((group) => ({
              to: `/admin/academic/${SECTIONS.find((candidate) => candidate.group === group.id)?.path}`,
              label: group.label,
              current: group.id === current.group,
            }))}
          />
          {SECTIONS.filter((candidate) => candidate.group === current.group).length > 1 && (
            <div className="border-b border-line">
              <LinkTabs
                variant="pill"
                label="Academic admin sections"
                tabs={SECTIONS.filter((candidate) => candidate.group === current.group).map((candidate) => ({
                  to: `/admin/academic/${candidate.path}`,
                  label: candidate.label,
                }))}
              />
            </div>
          )}
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
