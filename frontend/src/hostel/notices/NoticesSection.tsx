import { getHostelOccupancy } from '../../api/hostelApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import { Card } from '../../ui'
import { HostelNoticeManager } from './HostelNotices'

/** Hostel Management, Notices: the hostel office's notices for every hostel. */
export function NoticesSection() {
  const hostels = useApiQuery(queryKeys.hostel.occupancy('admin'), (signal) => getHostelOccupancy('admin', signal))
  if (hostels.status !== 'loaded') {
    return (
      <Card title="Hostel notices">
        <SectionState request={hostels} what="hostels" />
      </Card>
    )
  }
  if (hostels.data.length === 0) {
    return (
      <Card title="Hostel notices">
        <EmptyState title="No hostels yet." description="Add a hostel under Hostels & Rooms first." />
      </Card>
    )
  }
  return <HostelNoticeManager hostels={hostels.data} />
}
