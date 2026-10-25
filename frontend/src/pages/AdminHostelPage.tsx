import { NavLink, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { AllocationsSection } from '../hostel/admin/AllocationsSection'
import { ApplicationReview } from '../hostel/admin/ApplicationReview'
import { ApplicationsSection } from '../hostel/admin/ApplicationsSection'
import { InventorySection } from '../hostel/admin/InventorySection'
import { NotFoundPage } from './NotFoundPage'

const SECTIONS = [
  { path: 'applications', label: 'Applications', Section: ApplicationsSection },
  { path: 'hostels', label: 'Hostels & Rooms', Section: InventorySection },
  { path: 'allocations', label: 'Allocations', Section: AllocationsSection },
] as const

/**
 * Hostel Management at /admin/hostel/:section for the hostel office: applications (with review at
 * /admin/hostel/applications/:applicationId), the hostel inventory, and allocations. Approving goes through the
 * approval engine. Accounts without ADMIN see an explanation and no request is made; the backend checks every request.
 */
export function AdminHostelPage({ review = false }: { review?: boolean }) {
  const { account } = useAuth()
  const { section } = useParams()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const current = SECTIONS.find((candidate) => candidate.path === (review ? 'applications' : section))

  if (current === undefined) {
    return <NotFoundPage />
  }

  return (
    <>
      <title>{`${current.label} · Hostel Management · Stacc`}</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Hostel Management</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        Review hostel applications, manage hostels, rooms, and beds, and allocate approved students.
      </p>
      {isAdmin ? (
        <>
          <nav aria-label="Hostel management sections" className="mt-6 border-b border-line">
            <ul className="-mb-px flex flex-wrap gap-x-1">
              {SECTIONS.map((item) => (
                <li key={item.path}>
                  <NavLink
                    to={`/admin/hostel/${item.path}`}
                    className={({ isActive }) =>
                      `inline-flex min-h-11 items-center border-b-2 px-3 text-sm focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-stacc-primary ${
                        isActive ? 'border-stacc-primary font-semibold text-stacc-primary' : 'border-transparent text-ink hover:border-line-strong'
                      }`
                    }
                  >
                    {item.label}
                  </NavLink>
                </li>
              ))}
            </ul>
          </nav>
          <div className="mt-6">{review ? <ApplicationReview /> : <current.Section />}</div>
        </>
      ) : (
        <div className="mt-6">
          <StatePanel>
            <AccessDeniedState title="Hostel Management is for admin accounts." />
          </StatePanel>
        </div>
      )}
    </>
  )
}
