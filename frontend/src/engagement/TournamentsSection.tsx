import { getTournaments } from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { Card } from '../ui'
import { TournamentTables } from './TournamentTables'

/** Published tournaments on the campus Sports tab: matches, text scores, and standings. Read only. */
export function TournamentsSection() {
  const tournaments = useApiQuery(queryKeys.engagement.tournaments(), (signal) => getTournaments(signal))
  return (
    <section aria-labelledby="campus-tournaments" className="space-y-4 rounded-panel border border-line bg-surface px-4 py-4 sm:px-5">
      <div>
        <h2 id="campus-tournaments" className="text-xl font-bold text-portal-navy">Tournaments</h2>
        <span aria-hidden="true" className="mt-1.5 block h-0.5 w-16 rounded bg-portal-cta" />
      </div>
      {tournaments.status !== 'loaded' ? (
        <SectionState request={tournaments} what="tournaments" />
      ) : tournaments.data.length === 0 ? (
        <EmptyState title="No tournaments published yet." />
      ) : (
        tournaments.data.map((tournament) => (
          <Card key={tournament.tournamentId} level={3} title={tournament.name} description={`${tournament.sport} · ${tournament.season}`}>
            <TournamentTables tournament={tournament} />
          </Card>
        ))
      )}
    </section>
  )
}
