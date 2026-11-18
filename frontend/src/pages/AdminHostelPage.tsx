import { useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { AllocationsSection } from '../hostel/admin/AllocationsSection'
import { ApplicationReview } from '../hostel/admin/ApplicationReview'
import { ApplicationsSection } from '../hostel/admin/ApplicationsSection'
import { InventorySection } from '../hostel/admin/InventorySection'
import { OccupancySection } from '../hostel/admin/OccupancySection'
import { WardensSection } from '../hostel/admin/WardensSection'
import { MessSection } from '../hostel/mess/MessSection'
import { VisitingHoursSection } from '../hostel/visitors/Visitors'
import { NoticesSection } from '../hostel/notices/NoticesSection'
import { RoomChangesSection } from '../hostel/rooms/RoomChanges'
import { NotFoundPage } from './NotFoundPage'
import { LinkTabs, PageHeader } from '../ui'

const SECTIONS = [
  { path: 'applications', label: 'Applications', Section: ApplicationsSection },
  { path: 'hostels', label: 'Hostels & Rooms', Section: InventorySection },
  { path: 'allocations', label: 'Allocations', Section: AllocationsSection },
  { path: 'occupancy', label: 'Occupancy & Bed Map', Section: OccupancySection },
  { path: 'wardens', label: 'Wardens', Section: WardensSection },
  { path: 'room-changes', label: 'Room changes', Section: RoomChangesSection },
  { path: 'notices', label: 'Notices', Section: NoticesSection },
  { path: 'mess', label: 'Mess', Section: MessSection },
  { path: 'visitors', label: 'Visitors', Section: VisitingHoursSection },
] as const

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
      <PageHeader title="Hostel Management" description="Manage hostel applications, allocations and hostel operations." />
      {isAdmin ? (
        <>
          <LinkTabs
            label="Hostel management sections"
            tabs={SECTIONS.map((item) => ({ to: `/admin/hostel/${item.path}`, label: item.label }))}
          />
          <div>{review ? <ApplicationReview /> : <current.Section />}</div>
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
