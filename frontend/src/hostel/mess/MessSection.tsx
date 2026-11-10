import { getHostelOccupancy } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import { Card } from '../../ui'
import { MessManager } from './Mess'

/** Hostel Management, Mess: the hostel office's menus and feedback for every hostel. */
export function MessSection() {
  const hostels = useApiQuery(queryKeys.hostel.occupancy('admin'), (signal) => getHostelOccupancy('admin', signal))
  if (hostels.status !== 'loaded') {
    return (
      <Card title="Mess">
        <SectionState request={hostels} what="hostels" />
      </Card>
    )
  }
  if (hostels.data.length === 0) {
    return (
      <Card title="Mess">
        <EmptyState title="No hostels yet." description="Add a hostel under Hostels & Rooms first." />
      </Card>
    )
  }
  return <MessManager hostels={hostels.data} />
}
