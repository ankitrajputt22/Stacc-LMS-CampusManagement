import { useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { MyRequestsSection } from '../campus/MyRequestsSection'
import { NoticesSection } from '../campus/NoticesSection'
import { ServicesSection } from '../campus/ServicesSection'
import { EventOrganizer, EventsSection } from '../engagement/EventsSection'
import { FacilitiesSection } from '../engagement/FacilitiesSection'
import { GroupsSection } from '../engagement/GroupsSection'
import { LinkTabs, PageHeader } from '../ui'
import { NotFoundPage } from './NotFoundPage'

const TABS = ['happening', 'clubs', 'sports', 'events', 'facilities', 'services', 'notices'] as const
type CampusTab = (typeof TABS)[number]
const LABELS: Record<CampusTab, string> = {
  happening: 'Happening',
  clubs: 'Clubs',
  sports: 'Sports',
  events: 'Events',
  facilities: 'Facilities',
  services: 'Services',
  notices: 'Notices',
}

/**
 * The campus hub at /campus/:tab, one place for every role held: what is happening soon, clubs and sports to join,
 * events to register for, facilities to book, services to request, and notices. The backend decides what each account
 * may see and do; hiding a button here protects nothing.
 */
export function CampusPage() {
  const { account } = useAuth()
  const { tab = 'happening' } = useParams()
  const roles = account?.roles ?? []
  const member = hasAnyRole(roles, ['STUDENT', 'FACULTY'])
  const admin = hasAnyRole(roles, ['ADMIN'])
  if (!(TABS as readonly string[]).includes(tab)) {
    return <NotFoundPage />
  }
  const current = tab as CampusTab
  const tabs = TABS.filter((name) => member || name === 'notices' || name === 'happening' || name === 'events' || name === 'clubs' || name === 'sports' || name === 'facilities')
  return (
    <>
      <title>{`${LABELS[current]} · Campus · Stacc`}</title>
      <PageHeader title="Campus" description="What’s on, clubs and sports, events, facilities, services, and notices." />
      <div className="mt-6">
        <LinkTabs label="Campus sections" tabs={tabs.map((name) => ({ to: `/campus/${name}`, label: LABELS[name], current: name === current }))} />
        <div className="space-y-8">
          {current === 'happening' && (
            <>
              <EventsSection canRegister={member} withinDays={7} />
              <NoticesSection />
            </>
          )}
          {current === 'clubs' && <GroupsSection kind="CLUB" canJoin={member} />}
          {current === 'sports' && <GroupsSection kind="SPORT" canJoin={member} />}
          {current === 'events' && (
            <>
              <EventsSection canRegister={member} />
              <EventOrganizer admin={admin} />
            </>
          )}
          {current === 'facilities' && <FacilitiesSection canBook={member} />}
          {current === 'services' && member && (
            <>
              <ServicesSection />
              <MyRequestsSection />
            </>
          )}
          {current === 'notices' && <NoticesSection />}
        </div>
      </div>
    </>
  )
}
