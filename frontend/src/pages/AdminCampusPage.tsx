import { NavLink, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { NoticesSection } from '../campus/admin/NoticesSection'
import { RequestReview } from '../campus/admin/RequestReview'
import { RequestsSection } from '../campus/admin/RequestsSection'
import { ServiceCatalogSection } from '../campus/admin/ServiceCatalogSection'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { NotFoundPage } from './NotFoundPage'

const SECTIONS = [
  { path: 'services', label: 'Service Catalog', Section: ServiceCatalogSection },
  { path: 'requests', label: 'Service Requests', Section: RequestsSection },
  { path: 'notices', label: 'Notices', Section: NoticesSection },
] as const

/**
 * Campus Management at /admin/campus/:section: the service catalog, service requests (with review at
 * /admin/campus/requests/:requestId), and notices. Approving goes through the approval engine. Accounts without ADMIN
 * see an explanation and no request is made; the backend checks every request.
 */
export function AdminCampusPage({ review = false }: { review?: boolean }) {
  const { account } = useAuth()
  const { section } = useParams()
  const isAdmin = hasAnyRole(account?.roles ?? [], ['ADMIN'])
  const current = SECTIONS.find((candidate) => candidate.path === (review ? 'requests' : section))

  if (current === undefined) {
    return <NotFoundPage />
  }

  return (
    <>
      <title>{`${current.label} · Campus Management · Stacc`}</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Campus Management</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">
        Manage the campus service catalog, handle service requests, and publish campus notices.
      </p>
      {isAdmin ? (
        <>
          <nav aria-label="Campus management sections" className="mt-6 border-b border-line">
            <ul className="-mb-px flex flex-wrap gap-x-1">
              {SECTIONS.map((item) => (
                <li key={item.path}>
                  <NavLink
                    to={`/admin/campus/${item.path}`}
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
          <div className="mt-6">{review ? <RequestReview /> : <current.Section />}</div>
        </>
      ) : (
        <div className="mt-6">
          <StatePanel>
            <AccessDeniedState title="Campus Management is for admin accounts." />
          </StatePanel>
        </div>
      )}
    </>
  )
}
