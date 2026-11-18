import { Link } from 'react-router'
import { getMyMess } from '../../api/hostelApi'
import { getMyOutings } from '../../api/outingsApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { NavIcon } from '../../components/navigation/navIcons'
import { formatDateTime } from '../../courses/dates'
import type { HostelApplicationDetail } from '../../types/hostel'
import { Badge, ButtonLink, Card } from '../../ui'
import { allocationStatusLabel, approvalStatusLabel } from '../hostelText'
import { ResidentHostelNotices } from '../notices/HostelNotices'
import { KIND_LABEL, outingStatusLabel } from '../outings/outingText'

function localDay(): string {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function Allocation({ detail }: { detail: HostelApplicationDetail | null }) {
  const allocation = detail?.activeAllocation ?? null
  return (
    <Card title="Your allocation" icon="hostel">
      {!detail ? (
        <div className="space-y-3">
          <p className="text-[0.95rem] text-ink-muted">You have not applied for a hostel place yet.</p>
          <ButtonLink to="/hostel/applications">Apply for hostel</ButtonLink>
        </div>
      ) : (
        <>
          {allocation ? (
            <div className="flex items-start justify-between gap-3 border-l-4 border-success pl-4">
              <div>
                <p className="text-2xl font-bold text-ink">{allocation.hostelName}</p>
                <p className="text-ink-muted">{`Room ${allocation.roomNumber} · Bed ${allocation.bedNumber} · ${detail.academicSessionCode}`}</p>
              </div>
              <Badge tone="success">{allocationStatusLabel(allocation.status)}</Badge>
            </div>
          ) : (
            <p className="text-[0.95rem] text-ink-muted">No bed has been allocated yet. The hostel office allocates beds after approval.</p>
          )}
          <dl className="mt-4 divide-y divide-line border-y border-line text-[0.95rem]">
            <div className="flex items-center justify-between gap-3 py-2.5">
              <dt className="text-ink-muted">Application</dt>
              <dd className="flex items-center gap-3"><span className="font-medium">{detail.applicationNumber}</span>
                <Badge tone={detail.approvalStatus === 'APPROVED' ? 'success' : detail.approvalStatus === 'PENDING' ? 'warning' : 'neutral'}>{approvalStatusLabel(detail.approvalStatus)}</Badge></dd>
            </div>
            {allocation && (
              <div className="flex items-center justify-between gap-3 py-2.5">
                <dt className="text-ink-muted">Allocation date</dt>
                <dd>{formatDateTime(allocation.allocatedAt)}</dd>
              </div>
            )}
          </dl>
          <div className="mt-4 flex flex-wrap gap-3">
            <ButtonLink to="/hostel/applications" variant="secondary">View application</ButtonLink>
            {allocation && <ButtonLink to="/hostel/room-changes" variant="secondary">Ask to move</ButtonLink>}
            {allocation && <ButtonLink to="/hostel/outings">Gate passes & leave</ButtonLink>}
          </div>
        </>
      )}
    </Card>
  )
}

function TonightsDinner() {
  const mess = useApiQuery(queryKeys.hostel.myMess(), (signal) => getMyMess(signal))
  if (mess.status !== 'loaded' || !mess.data.hostelName) {
    return null
  }
  const today = mess.data.week.find((day) => day.date === localDay())
  const dinner = today?.meals.find((meal) => meal.meal === 'DINNER')
  return (
    <Card title="Tonight’s dinner" icon="todo">
      <p className="text-lg text-ink">{dinner ? dinner.items : 'No dinner is on today’s menu.'}</p>
      <Link to="/hostel/mess" className="mt-3 inline-flex items-center gap-1 rounded-md bg-portal-bar-strong/60 px-3 py-1.5 font-medium text-portal-navy hover:bg-portal-bar-strong focus-visible:outline-2 focus-visible:outline-stacc-primary">
        View weekly menu<NavIcon name="chevronRight" className="size-4" />
      </Link>
    </Card>
  )
}

function LatestOuting() {
  const outings = useApiQuery(queryKeys.hostel.outings(), (signal) => getMyOutings(signal))
  if (outings.status !== 'loaded' || outings.data.length === 0) {
    return null
  }
  const latest = outings.data[0]
  return (
    <Card title="Your latest outing" icon="campus">
      <div className="flex flex-wrap items-center justify-between gap-3 border-l-4 border-success pl-4">
        <div>
          <p className="text-xl font-bold text-ink">{KIND_LABEL[latest.kind]}</p>
          <p className="text-ink-muted">
            {[outingStatusLabel(latest.approvalStatus), latest.outside ? 'Outside now' : null, latest.overdue ? 'Overdue' : null,
              `Return ${formatDateTime(latest.leaveUntil)}`].filter(Boolean).join(' · ')}
          </p>
        </div>
        {latest.canShowPass && <ButtonLink to="/hostel/outings">Show pass</ButtonLink>}
      </div>
    </Card>
  )
}

export function StudentHostelOverview({ detail }: { detail: HostelApplicationDetail | null }) {
  return (
    <div className="space-y-6">
      <div className="grid items-start gap-6 xl:grid-cols-2">
        <Allocation detail={detail} />
        <ResidentHostelNotices />
      </div>
      <div className="grid items-start gap-6 xl:grid-cols-2">
        <TonightsDinner />
        <LatestOuting />
      </div>
      <nav aria-label="Hostel quick links" className="flex flex-wrap items-center gap-x-6 gap-y-3 rounded-panel border border-line bg-surface px-5 py-4">
        <span className="font-semibold text-ink">Quick Links:</span>
        <Link to="/hostel/visitors" className="flex items-center gap-2 text-stacc-primary hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary"><NavIcon name="profile" />Visitors</Link>
        <Link to="/hostel/room-problem" className="flex items-center gap-2 text-stacc-primary hover:underline focus-visible:outline-2 focus-visible:outline-stacc-primary"><NavIcon name="warning" />Room problem</Link>
      </nav>
    </div>
  )
}
