import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { MyRequestsSection } from '../campus/MyRequestsSection'
import { NoticesSection } from '../campus/NoticesSection'
import { ServicesSection } from '../campus/ServicesSection'

/**
 * The Campus page at /campus, one page for every role the account holds: services and the account's own requests for
 * accounts holding STUDENT or FACULTY, and the notice feed for STUDENT, FACULTY, or ADMIN. The backend decides which
 * services and notices each account gets; hiding a section here protects nothing.
 */
export function CampusPage() {
  const { account } = useAuth()
  const roles = account?.roles ?? []
  const requester = hasAnyRole(roles, ['STUDENT', 'FACULTY'])

  return (
    <>
      <title>Campus · Stacc</title>
      <h1 className="text-2xl font-semibold tracking-tight text-ink">Campus</h1>
      <p className="mt-2 text-sm leading-6 text-ink-muted sm:text-base">Campus services and important notices.</p>
      <div className="mt-6 space-y-8">
        {requester && <ServicesSection />}
        {requester && <MyRequestsSection />}
        <NoticesSection />
      </div>
    </>
  )
}
