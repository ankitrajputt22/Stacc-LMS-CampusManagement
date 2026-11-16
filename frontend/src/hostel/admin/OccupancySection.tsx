import { useState } from 'react'
import { getHostelOccupancy } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import { NavIcon } from '../../components/navigation/navIcons'
import type { HostelOccupancy } from '../../types/hostel'
import { Card } from '../../ui'
import { BedMap } from '../shared/BedMap'
import { OccupancyTable } from '../shared/OccupancyTable'

function Figure({ label, value }: { label: string; value: number }) {
  return (
    <div className="px-4 text-center sm:border-l sm:border-line">
      <p className="text-ink-muted">{label}</p>
      <p className="text-3xl font-bold text-ink">{value}</p>
    </div>
  )
}

/** One hostel's counts in a strip, as the API counts them. */
function Overview({ hostel }: { hostel: HostelOccupancy }) {
  return (
    <section aria-label={`${hostel.name} overview`} className="grid items-center gap-4 rounded-panel border border-line bg-surface px-5 py-4 sm:grid-cols-[1.6fr_repeat(4,1fr)]">
      <div className="flex items-center gap-4">
        <NavIcon name="hostel" className="size-9 text-portal-navy" />
        <div>
          <p className="text-xl font-bold text-ink">{`${hostel.name} – Overview`}</p>
          <p className="text-sm text-ink-muted">{hostel.code}{hostel.active ? '' : ' · Inactive'}</p>
        </div>
      </div>
      <Figure label="Beds" value={hostel.beds} />
      <Figure label="Occupied" value={hostel.occupied} />
      <Figure label="Maintenance" value={hostel.maintenance} />
      <Figure label="Available" value={hostel.available} />
    </section>
  )
}

/** Hostel Management → Occupancy: every hostel's counts, worked out live, and a chosen hostel's bed map. */
export function OccupancySection() {
  const occupancy = useApiQuery(queryKeys.hostel.occupancy('admin'), (signal) => getHostelOccupancy('admin', signal))
  const [chosen, setChosen] = useState<number | null>(null)
  if (occupancy.status !== 'loaded') {
    return (
      <Card title="Occupancy" icon="hostel">
        <SectionState request={occupancy} what="occupancy" />
      </Card>
    )
  }
  if (occupancy.data.length === 0) {
    return (
      <Card title="Occupancy" icon="hostel">
        <EmptyState title="No hostels yet." description="Add a hostel under Hostels & Rooms." />
      </Card>
    )
  }
  // The first hostel is shown until another is chosen, so the bed map is there at once.
  const selected = chosen ?? occupancy.data[0].hostelId
  const hostel = occupancy.data.find((row) => row.hostelId === selected) ?? occupancy.data[0]
  return (
    <div className="space-y-6">
      {occupancy.data.length > 1 && <OccupancyTable rows={occupancy.data} selected={selected} onSelect={setChosen} />}
      <Overview hostel={hostel} />
      <BedMap key={selected} side="admin" hostelId={selected} />
    </div>
  )
}
