import { useState } from 'react'
import { getHostelOccupancy } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import { Card } from '../../ui'
import { BedMap } from '../shared/BedMap'
import { OccupancyTable } from '../shared/OccupancyTable'

/** Hostel Management → Occupancy: every hostel's counts, worked out live, and a chosen hostel's bed map. */
export function OccupancySection() {
  const occupancy = useApiQuery(queryKeys.hostel.occupancy('admin'), (signal) => getHostelOccupancy('admin', signal))
  const [selected, setSelected] = useState<number | null>(null)
  if (occupancy.status !== 'loaded') {
    return (
      <Card title="Occupancy">
        <SectionState request={occupancy} what="occupancy" />
      </Card>
    )
  }
  if (occupancy.data.length === 0) {
    return (
      <Card title="Occupancy">
        <EmptyState title="No hostels yet." description="Add a hostel under Hostels & Rooms." />
      </Card>
    )
  }
  return (
    <div className="space-y-6">
      <OccupancyTable rows={occupancy.data} selected={selected} onSelect={setSelected} />
      {selected !== null && <BedMap key={selected} side="admin" hostelId={selected} />}
    </div>
  )
}
