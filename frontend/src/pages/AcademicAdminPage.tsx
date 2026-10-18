import { NavLink, useParams } from 'react-router'
import { EnrollmentsSection } from '../admin/EnrollmentsSection'
import { OfferingRoster } from '../admin/OfferingRoster'
import { OfferingsSection } from '../admin/OfferingsSection'
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
import { NotFoundPage } from './NotFoundPage'

const SECTIONS = [
  { path: 'departments', label: 'Departments', Section: DepartmentsSection },
  { path: 'programs', label: 'Programs', Section: ProgramsSection },
  { path: 'sessions', label: 'Academic Sessions', Section: AcademicSessionsSection },
  { path: 'semesters', label: 'Semesters', Section: SemestersSection },
  { path: 'courses', label: 'Courses', Section: CoursesSection },
  { path: 'offerings', label: 'Course Offerings', Section: OfferingsSection },
  { path: 'enrollments', label: 'Enrollments', Section: EnrollmentsSection },
] as const

const ID_PATTERN = /^[1-9]\d{0,17}$/

/** The sections as route-aware text links. The current one is marked with a bar, bold text, and aria-current. */
function SectionNavigation() {
  return (
    <nav aria-label="Academic admin sections" className="mt-6 border-b border-line">
      <ul className="-mb-px flex flex-wrap gap-x-1">
        {SECTIONS.map((section) => (
          <li key={section.path}>
            <NavLink
              to={`/admin/academic/${section.path}`}
              className={({ isActive }) =>
                `inline-flex min-h-11 items-center border-b-2 px-3 text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-stacc-primary ${
                  isActive
                    ? 'border-stacc-primary font-semibold text-stacc-primary'
                    : 'border-transparent text-ink hover:border-line-strong'
                }`
              }
            >
              {section.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

/**
 * The admin workspace for academic data, at /admin/academic/:section; /admin/academic opens the departments. The
 * reusable masters can be added and edited, course offerings added and moved through their status, and students
 * enrolled in semesters and course offerings; nothing is deleted. An offering's roster is at
 * /admin/academic/offerings/:courseOfferingId/enrollments. Accounts without the admin role see an explanation instead
 * and no request is made; the backend checks the role and the permissions on every request.
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
    <>
      <title>{`${roster ? 'Offering Roster' : current.label} · Academic Admin · Stacc`}</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Academic Admin</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        Manage academic masters, course offerings, and official student enrollments.
      </p>
      {isAdmin ? (
        <>
          <SectionNavigation />
          <div className="max-w-5xl">
            {roster ? (
              <OfferingRoster
                courseOfferingId={courseOfferingId && ID_PATTERN.test(courseOfferingId) ? Number(courseOfferingId) : null}
              />
            ) : (
              <current.Section />
            )}
          </div>
        </>
      ) : (
        <div className="mt-6">
          <StatePanel>
            <AccessDeniedState
              title="This page is for admin accounts."
              message="If you think you should, contact the college administration."
            />
          </StatePanel>
        </div>
      )}
    </>
  )
}
