import { formatDateTime } from '../courses/dates'
import type { Fixture, Standing, Tournament } from '../types/engagement'
import { Badge, DataTable } from '../ui'
import { FIXTURE_STATUS_LABEL } from './engagementText'

function score(fixture: Fixture): string {
  if (fixture.status !== 'PLAYED') {
    return FIXTURE_STATUS_LABEL[fixture.status]
  }
  const outcome = fixture.result === 'DRAW' ? 'Draw' : `${fixture.result === 'HOME_WIN' ? fixture.home.name : fixture.away.name} won`
  return `${fixture.homeScore} – ${fixture.awayScore} · ${outcome}`
}

/** A tournament's matches and the standings the backend worked out from played ones. */
export function TournamentTables({ tournament }: { tournament: Tournament }) {
  return (
    <div className="space-y-4">
      <DataTable<Fixture>
        caption={`Matches of ${tournament.name}`}
        rows={tournament.fixtures}
        rowKey={(fixture) => fixture.fixtureId}
        empty={<p className="px-4 py-3 text-sm text-ink-muted">No matches yet.</p>}
        columns={[
          { key: 'match', header: 'Match', primary: true, cell: (fixture) => `${fixture.home.name} v ${fixture.away.name}` },
          { key: 'when', header: 'When', cell: (fixture) => <time dateTime={fixture.startsAt}>{formatDateTime(fixture.startsAt)}</time> },
          { key: 'venue', header: 'Venue', cell: (fixture) => fixture.venue ?? '' },
          {
            key: 'result',
            header: 'Result',
            cell: (fixture) => (fixture.status === 'CANCELLED' ? <Badge tone="warning">Cancelled</Badge> : score(fixture)),
          },
        ]}
      />
      {tournament.standings.length > 0 && (
        <DataTable<Standing>
          caption={`Standings of ${tournament.name}`}
          rows={tournament.standings}
          rowKey={(row) => row.side}
          empty={null}
          columns={[
            { key: 'side', header: 'Team', primary: true, cell: (row) => (row.ours ? <strong>{row.side}</strong> : row.side) },
            { key: 'played', header: 'Played', cell: (row) => row.played },
            { key: 'won', header: 'Won', cell: (row) => row.won },
            { key: 'drawn', header: 'Drawn', cell: (row) => row.drawn },
            { key: 'lost', header: 'Lost', cell: (row) => row.lost },
            { key: 'points', header: 'Points', cell: (row) => row.points },
          ]}
        />
      )}
    </div>
  )
}
