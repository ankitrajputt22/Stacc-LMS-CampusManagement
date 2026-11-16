import { useState } from 'react'
import { getHostelOccupancy } from '../api/hostelApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { NavIcon } from '../components/navigation/navIcons'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { BedMap } from '../hostel/shared/BedMap'
import { OccupancyTable } from '../hostel/shared/OccupancyTable'
import { WardenOutings } from '../hostel/outings/WardenOutings'
import { MessManager } from '../hostel/mess/Mess'
import { HostelNoticeManager } from '../hostel/notices/HostelNotices'
import { WardenRollCall } from '../hostel/rollcall/WardenRollCall'
import { Card, LinkTabs, PageHeader } from '../ui'
import { Link } from 'react-router'

export type WardenSection = 'overview' | 'roll-call' | 'notices' | 'mess' | 'bed-map'

const TABS = [
  { to: '/warden', label: 'Overview', end: true },
  { to: '/warden/roll-call', label: 'Roll call' },
  { to: '/warden/notices', label: 'Notices' },
  { to: '/warden/mess', label: 'Mess' },
  { to: '/warden/bed-map', label: 'Bed map' },
]

/**
 * /warden: the warden's own hostels, one section per page: Overview (occupancy and requests to decide), Roll call,
 * Notices, Mess, and the Bed map, where students are checked in. The backend lists only the hostels the account is an
 * active warden of and checks each hostel again.
 */
export function WardenPage({ section = 'overview' }: { section?: WardenSection }) {
  const { account } = useAuth()
  const occupancy = useApiQuery(queryKeys.hostel.occupancy('warden'), (signal) => getHostelOccupancy('warden', signal))
  const [chosen, setChosen] = useState<number | null>(null)
  const hostels = occupancy.status === 'loaded' ? occupancy.data : []
  const selected = chosen ?? hostels[0]?.hostelId ?? null

  let content
  if (occupancy.status !== 'loaded') {
    content = (
      <Card title="Your Hostels" icon="hostel">
        <SectionState request={occupancy} what="your hostels" />
      </Card>
    )
  } else if (hostels.length === 0) {
    content = (
      <Card title="Your Hostels" icon="hostel">
        <EmptyState title="You are not a warden of any hostel yet." description="The hostel office assigns wardens." />
      </Card>
    )
  } else if (section === 'roll-call') {
    content = <WardenRollCall hostels={hostels} />
  } else if (section === 'notices') {
    content = <HostelNoticeManager hostels={hostels} />
  } else if (section === 'mess') {
    content = <MessManager hostels={hostels} />
  } else if (section === 'bed-map') {
    content = (
      <div className="space-y-6">
        {hostels.length > 1 && <OccupancyTable rows={hostels} selected={selected} onSelect={setChosen} />}
        {selected !== null && <BedMap key={selected} side="warden" hostelId={selected} />}
      </div>
    )
  } else {
    content = (
      <div className="space-y-6">
        <Card title="Your Hostels" icon="hostel" padding="none">
          <OccupancyTable rows={hostels} selected={null} onSelect={() => undefined} linkTo="/warden/bed-map" />
        </Card>
        <WardenOutings />
        <nav aria-label="Warden quick links" className="flex flex-wrap items-center gap-x-6 gap-y-3 rounded-panel border border-line bg-surface px-5 py-4">
          <span className="font-semibold text-ink">Quick Links:</span>
          <Link to="/warden/notices" className="flex items-center gap-2 text-stacc-primary hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary"><NavIcon name="campus" />Hostel notices</Link>
          <Link to="/warden/mess" className="flex items-center gap-2 text-stacc-primary hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary"><NavIcon name="todo" />Mess</Link>
          <Link to="/warden/roll-call" className="flex items-center gap-2 text-stacc-primary hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary"><NavIcon name="attendance" />Night roll call</Link>
        </nav>
      </div>
    )
  }

  return (
    <>
      <title>Warden · Stacc</title>
      <PageHeader
        title="Warden Dashboard"
        description={[hostels.map((hostel) => `${hostel.name} (${hostel.code})`).join(', '), account ? `Warden: ${account.loginId}` : null].filter(Boolean).join(' · ')}
      />
      <LinkTabs label="Warden sections" tabs={TABS} />
      {content}
    </>
  )
}
