import { getAreaAnalytics } from '../api/analyticsApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { useAuth } from '../auth/AuthContext'
import { hasAnyRole } from '../auth/roles'
import { AccessDeniedState } from '../components/states/AccessDeniedState'
import { StatePanel } from '../components/states/StatePanel'
import { SectionState } from '../courses/SectionState'
import { useId } from 'react'
import { NavIcon, type NavIconName } from '../components/navigation/navIcons'
import { PageHeader } from '../ui'

type Figure = [label: string, value: number | null, suffix?: string]

function words(status: string): string {
  return status.charAt(0) + status.slice(1).toLowerCase().replaceAll('_', ' ')
}

function counts(map: Record<string, number>): Figure[] {
  return Object.entries(map).map(([status, value]) => [words(status), value])
}

/** One area as a list of figures; a missing rate shows a dash with the reason. */
function Area({ title, icon, figures, empty }: { title: string; icon: NavIconName; figures: Figure[]; empty?: string }) {
  const id = useId()
  return (
    <section aria-labelledby={id} className="rounded-panel border border-line bg-surface p-4 sm:p-5">
      <div className="flex items-center gap-4">
        <span aria-hidden="true" className="flex size-12 shrink-0 items-center justify-center rounded-panel bg-sky-100 text-portal-navy">
          <NavIcon name={icon} className="size-6" />
        </span>
        <h2 id={id} className="text-2xl font-bold text-ink">{title}</h2>
      </div>
      {figures.length === 0 ? (
        <p className="mt-3 text-sm text-ink-muted">{empty ?? 'Nothing recorded yet.'}</p>
      ) : (
        <dl className="mt-4 flex flex-wrap gap-y-4">
          {figures.map(([label, value, suffix]) => (
            <div key={label} className="min-w-32 flex-1 border-line px-4 not-first:border-l first:pl-2">
              <dt className="text-[0.95rem] text-ink">{label}</dt>
              <dd className="mt-1 text-3xl font-bold text-ink tabular-nums">{value === null ? '—' : `${value}${suffix ?? ''}`}</dd>
            </div>
          ))}
        </dl>
      )}
    </section>
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
              <Area title="Hostel" icon="hostel" figures={[
                ['Beds in service', areas.data.hostel.bedsInService], ['Occupied', areas.data.hostel.occupiedBeds],
                ['Occupancy', areas.data.hostel.occupancyPercent, '%'], ['Under maintenance', areas.data.hostel.bedsUnderMaintenance],
              ]} />
            )}
            {areas.data.helpdesk && (
              <Area title="Helpdesk" icon="support" figures={[
                ...counts(areas.data.helpdesk.ticketsByStatus), ['Rated tickets', areas.data.helpdesk.ratedTickets],
                ['Average rating', areas.data.helpdesk.averageRating, ' of 5'],
              ]} />
            )}
            {areas.data.engagement && (
              <Area title="Campus engagement" icon="clubs" figures={[
                ['Active groups', areas.data.engagement.activeGroups], ['Active memberships', areas.data.engagement.activeMemberships],
                ['Coming events', areas.data.engagement.comingEvents], ['Past events', areas.data.engagement.pastEvents],
                ['Registered at past events', areas.data.engagement.pastRegistrations], ['Checked in', areas.data.engagement.checkedIn],
                ['Attendance', areas.data.engagement.attendancePercent, '%'],
              ]} />
            )}
            {areas.data.placement && (
              <Area title="Placement" icon="career" figures={[
                ['Open opportunities', areas.data.placement.openOpportunities],
                ...counts(areas.data.placement.applicationsByStatus).map(([label, value]): Figure => [`Applications: ${label.toLowerCase()}`, value]),
                ...counts(areas.data.placement.offersByStatus).map(([label, value]): Figure => [`Offers: ${label.toLowerCase()}`, value]),
              ]} />
            )}
            {areas.data.library && (
              <Area title="Library" icon="library" figures={[
                ['Titles', areas.data.library.activeTitles], ['Copies in service', areas.data.library.copiesInService],
                ['On loan', areas.data.library.openLoans], ['Overdue', areas.data.library.overdueLoans], ['Holds waiting', areas.data.library.activeHolds],
              ]} />
            )}
            {areas.data.ai && (
              <Area title="AI course indexes" icon="ai" figures={counts(areas.data.ai.courseIndexesByStatus)}
                empty="No course has built an AI index yet. AI questions are never stored, so only the indexes are counted." />
            )}
          </>
        )}
      </div>
    </>
  )
}
