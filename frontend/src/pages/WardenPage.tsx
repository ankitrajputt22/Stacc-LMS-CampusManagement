import { useState } from 'react'
import { getHostelOccupancy } from '../api/hostelApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { BedMap } from '../hostel/shared/BedMap'
import { OccupancyTable } from '../hostel/shared/OccupancyTable'
import { WardenOutings } from '../hostel/outings/WardenOutings'
import { HostelNoticeManager } from '../hostel/notices/HostelNotices'
import { WardenRollCall } from '../hostel/rollcall/WardenRollCall'
import { Card, PageHeader } from '../ui'

/**
 * /warden: the warden's own hostels with their occupancy, and a chosen hostel's bed map, where students are checked in.
 * The backend lists only the hostels the account is an active warden of and checks each hostel again.
 */
export function WardenPage() {
  const occupancy = useApiQuery(queryKeys.hostel.occupancy('warden'), (signal) => getHostelOccupancy('warden', signal))
  const [selected, setSelected] = useState<number | null>(null)
  return (
    <div className="max-w-5xl">
      <title>Warden · Stacc</title>
      <PageHeader title="Warden dashboard" description="Your hostels, their occupancy, and who is still to check in." />
      <div className="mt-6 space-y-6">
        <WardenOutings />
        {occupancy.status !== 'loaded' ? (
          <Card title="Your hostels">
            <SectionState request={occupancy} what="your hostels" />
          </Card>
        ) : occupancy.data.length === 0 ? (
          <Card title="Your hostels">
            <EmptyState title="You are not a warden of any hostel yet." description="The hostel office assigns wardens." />
          </Card>
        ) : (
          <>
            <WardenRollCall hostels={occupancy.data} />
            <HostelNoticeManager hostels={occupancy.data} />
            <OccupancyTable rows={occupancy.data} selected={selected} onSelect={setSelected} />
            {selected !== null && <BedMap key={selected} side="warden" hostelId={selected} />}
          </>
        )}
      </div>
    </div>
  )
}
