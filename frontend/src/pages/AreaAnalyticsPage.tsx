import { getAreaAnalytics } from '../api/analyticsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { SectionState } from '../courses/SectionState'
import { Card, PageHeader } from '../ui'

type Figure = [label: string, value: number | null, suffix?: string]

function words(status: string): string {
  return status.charAt(0) + status.slice(1).toLowerCase().replaceAll('_', ' ')
}

function counts(map: Record<string, number>): Figure[] {
  return Object.entries(map).map(([status, value]) => [words(status), value])
}

/** One area as a list of figures; a missing rate shows a dash with the reason. */
function Area({ title, figures, empty }: { title: string; figures: Figure[]; empty?: string }) {
  return (
    <Card title={title}>
      {figures.length === 0 ? (
        <p className="text-sm text-ink-muted">{empty ?? 'Nothing recorded yet.'}</p>
      ) : (
        <dl className="grid grid-cols-2 gap-x-6 gap-y-3 sm:grid-cols-3">
          {figures.map(([label, value, suffix]) => (
            <div key={label}>
              <dt className="text-xs font-medium text-ink-muted">{label}</dt>
              <dd className="text-xl font-semibold text-ink">{value === null ? '—' : `${value}${suffix ?? ''}`}</dd>
            </div>
          ))}
        </dl>
      )}
    </Card>
  )
}

/** /areas: live counts per campus area; the backend includes only the areas the account manages. */
export function AreaAnalyticsPage() {
  const { account } = useAuth()
  const allowed = hasAnyRole(account?.roles ?? [], ['ADMIN', 'LIBRARIAN', 'PLACEMENT_OFFICER'])
  const areas = useApiQuery(allowed ? queryKeys.analytics.areas() : null, (signal) => getAreaAnalytics(signal))
  return (
    <>
      <title>Area analytics · Stacc</title>
      <PageHeader title="Area analytics" description="Live counts for the campus areas you manage. Nothing here is stored." />
      <div className="mt-6 space-y-6">
        {!allowed ? (
          <StatePanel><AccessDeniedState title="Area analytics are for the people who run each area." /></StatePanel>
        ) : areas.status !== 'loaded' ? (
          <SectionState request={areas} what="area analytics" />
        ) : (
          <>
            <p className="text-sm text-ink-muted">Rates and averages: {areas.data.note}</p>
            {areas.data.hostel && (
              <Area title="Hostel" figures={[
                ['Beds in service', areas.data.hostel.bedsInService], ['Occupied', areas.data.hostel.occupiedBeds],
                ['Occupancy', areas.data.hostel.occupancyPercent, '%'], ['Under maintenance', areas.data.hostel.bedsUnderMaintenance],
              ]} />
            )}
            {areas.data.helpdesk && (
              <Area title="Helpdesk" figures={[
                ...counts(areas.data.helpdesk.ticketsByStatus), ['Rated tickets', areas.data.helpdesk.ratedTickets],
                ['Average rating (of 5)', areas.data.helpdesk.averageRating],
              ]} />
            )}
            {areas.data.engagement && (
              <Area title="Campus engagement" figures={[
                ['Active groups', areas.data.engagement.activeGroups], ['Active memberships', areas.data.engagement.activeMemberships],
                ['Coming events', areas.data.engagement.comingEvents], ['Past events', areas.data.engagement.pastEvents],
                ['Registered at past events', areas.data.engagement.pastRegistrations], ['Checked in', areas.data.engagement.checkedIn],
                ['Attendance', areas.data.engagement.attendancePercent, '%'],
              ]} />
            )}
            {areas.data.placement && (
              <Area title="Placement" figures={[
                ['Open opportunities', areas.data.placement.openOpportunities],
                ...counts(areas.data.placement.applicationsByStatus).map(([label, value]): Figure => [`Applications: ${label.toLowerCase()}`, value]),
                ...counts(areas.data.placement.offersByStatus).map(([label, value]): Figure => [`Offers: ${label.toLowerCase()}`, value]),
              ]} />
            )}
            {areas.data.library && (
              <Area title="Library" figures={[
                ['Titles', areas.data.library.activeTitles], ['Copies in service', areas.data.library.copiesInService],
                ['On loan', areas.data.library.openLoans], ['Overdue', areas.data.library.overdueLoans], ['Holds waiting', areas.data.library.activeHolds],
              ]} />
            )}
            {areas.data.ai && (
              <Area title="AI course indexes" figures={counts(areas.data.ai.courseIndexesByStatus)}
                empty="No course has built an AI index yet. AI questions are never stored, so only the indexes are counted." />
            )}
          </>
        )}
      </div>
    </>
  )
}
