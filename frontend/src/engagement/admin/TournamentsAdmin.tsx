import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import {
  addFixture,
  archiveTournament,
  cancelFixture,
  createTournament,
  getAllTournaments,
  getTournamentTeams,
  publishTournament,
  recordFixtureResult,
} from '../../api/engagementApi'
import { queryKeys } from '../../api/queryKeys'
import { useApiQuery } from '../../api/useApiQuery'
import { StatusConfirmation } from '../../components/StatusConfirmation'
import { EmptyState } from '../../components/states/EmptyState'
import { SectionState } from '../../courses/SectionState'
import type { Fixture, FixtureResult, TeamOption, Tournament } from '../../types/engagement'
import { Alert, Badge, Button, Card, SelectField, TextField } from '../../ui'
import { failureText, FEST_STATUS_LABEL, localToIso } from '../engagementText'
import { TournamentTables } from '../TournamentTables'

type Notify = (text: string, error?: boolean) => void

function attempt(action: () => Promise<unknown>, done: string, fallback: string, notify: Notify) {
  void action()
    .then(() => notify(done))
    .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
}

/** One side: one of our teams, or an external opponent typed by name. */
function SidePicker({ label, teams, value, onChange }: { label: string; teams: TeamOption[]; value: { teamId: string; name: string }; onChange: (next: { teamId: string; name: string }) => void }) {
  return (
    <>
      <SelectField label={`${label} team`} value={value.teamId} onChange={(event) => onChange({ ...value, teamId: event.target.value })}>
        <option value="">External opponent</option>
        {teams.map((team) => <option key={team.teamId} value={team.teamId}>{`${team.name} (${team.season}, ${team.groupName})`}</option>)}
      </SelectField>
      {value.teamId === '' && (
        <TextField label={`${label} opponent name`} value={value.name} maxLength={150} onChange={(event) => onChange({ ...value, name: event.target.value })} />
      )}
    </>
  )
}

function ResultForm({ fixture, notify }: { fixture: Fixture; notify: Notify }) {
  const [home, setHome] = useState(fixture.homeScore ?? '')
  const [away, setAway] = useState(fixture.awayScore ?? '')
  const [result, setResult] = useState<FixtureResult>(fixture.result ?? 'HOME_WIN')
  const match = `${fixture.home.name} v ${fixture.away.name}`
  if (fixture.status === 'CANCELLED') {
    return null
  }
  return (
    <form
      aria-label={`Result of ${match}`}
      className="flex flex-wrap items-end gap-2 rounded-control border border-line p-3"
      onSubmit={(event) => {
        event.preventDefault()
        attempt(() => recordFixtureResult(fixture.fixtureId, { homeScore: home.trim(), awayScore: away.trim(), result }), `Result of ${match} saved.`, 'Couldn’t save the result.', notify)
      }}
    >
      <TextField label={`${fixture.home.name} score`} value={home} maxLength={50} onChange={(event) => setHome(event.target.value)} />
      <TextField label={`${fixture.away.name} score`} value={away} maxLength={50} onChange={(event) => setAway(event.target.value)} />
      <SelectField label="Result" value={result} onChange={(event) => setResult(event.target.value as FixtureResult)}>
        <option value="HOME_WIN">{`${fixture.home.name} won`}</option>
        <option value="AWAY_WIN">{`${fixture.away.name} won`}</option>
        <option value="DRAW">Draw</option>
      </SelectField>
      <Button type="submit" size="sm">Save result</Button>
      {fixture.status === 'SCHEDULED' && (
        <Button size="sm" variant="secondary" onClick={() => attempt(() => cancelFixture(fixture.fixtureId), `${match} cancelled.`, 'Couldn’t cancel the match.', notify)}>
          Cancel match<span className="sr-only">{` ${match}`}</span>
        </Button>
      )}
    </form>
  )
}

function TournamentCard({ tournament, teams, notify }: { tournament: Tournament; teams: TeamOption[]; notify: Notify }) {
  const [home, setHome] = useState({ teamId: '', name: '' })
  const [away, setAway] = useState({ teamId: '', name: '' })
  const [startsAt, setStartsAt] = useState('')
  const [venue, setVenue] = useState('')
  const [archiving, setArchiving] = useState(false)
  const open = tournament.status !== 'ARCHIVED'
  return (
    <Card
      title={tournament.name}
      description={`${tournament.sport} · ${tournament.season}`}
      action={<Badge tone={tournament.status === 'PUBLISHED' ? 'success' : 'neutral'}>{FEST_STATUS_LABEL[tournament.status]}</Badge>}
    >
      <div className="space-y-4">
        <div className="flex flex-wrap gap-2">
          {tournament.status === 'DRAFT' && (
            <Button onClick={() => attempt(() => publishTournament(tournament.tournamentId), 'Tournament published.', 'Couldn’t publish.', notify)}>
              Publish {tournament.name}
            </Button>
          )}
          {open && !archiving && <Button variant="secondary" onClick={() => setArchiving(true)}>Archive {tournament.name}</Button>}
        </div>
        {archiving && (
          <StatusConfirmation
            question={`Archive ${tournament.name}?`}
            detail="Its matches and standings stay visible but can no longer be changed. This cannot be undone."
            confirmLabel="Archive"
            busyLabel="Archiving…"
            onCancel={() => setArchiving(false)}
            onConfirm={async () => {
              await archiveTournament(tournament.tournamentId)
              setArchiving(false)
              notify('Tournament archived.')
            }}
            describeRefusal={(_status, message) => message ?? 'Couldn’t archive the tournament.'}
          />
        )}
        <TournamentTables tournament={tournament} />
        {open && tournament.fixtures.some((fixture) => fixture.status !== 'CANCELLED') && (
          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-ink">Record results</h3>
            {tournament.fixtures.map((fixture) => <ResultForm key={fixture.fixtureId} fixture={fixture} notify={notify} />)}
          </div>
        )}
        {open && (
          <form
            className="grid gap-3 sm:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault()
              const start = localToIso(startsAt)
              if (start === null) {
                notify('Enter when the match starts.', true)
                return
              }
              attempt(
                () =>
                  addFixture(tournament.tournamentId, {
                    homeTeamId: home.teamId === '' ? null : Number(home.teamId), homeName: home.teamId === '' ? home.name.trim() : null,
                    awayTeamId: away.teamId === '' ? null : Number(away.teamId), awayName: away.teamId === '' ? away.name.trim() : null,
                    startsAt: start, venue: venue.trim() || null,
                  }),
                'Match added.',
                'Couldn’t add the match.',
                notify,
              )
            }}
          >
            <SidePicker label="Home" teams={teams} value={home} onChange={setHome} />
            <SidePicker label="Away" teams={teams} value={away} onChange={setAway} />
            <TextField label="Match starts" type="datetime-local" value={startsAt} onChange={(event) => setStartsAt(event.target.value)} />
            <TextField label="Match venue (optional)" value={venue} maxLength={200} onChange={(event) => setVenue(event.target.value)} />
            <div><Button type="submit" variant="secondary">Add match</Button></div>
          </form>
        )}
      </div>
    </Card>
  )
}

/** Campus Management → Tournaments: add tournaments, matches between our teams or external opponents, and results. */
export function TournamentsAdmin() {
  const queryClient = useQueryClient()
  const tournaments = useApiQuery(queryKeys.engagement.allTournaments(), (signal) => getAllTournaments(signal))
  const teams = useApiQuery(queryKeys.engagement.tournamentTeams(), (signal) => getTournamentTeams(signal))
  const [form, setForm] = useState({ name: '', sport: '', season: '' })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    void queryClient.invalidateQueries({ queryKey: queryKeys.engagement.all })
  }
  return (
    <div className="space-y-6">
      <p role="status" className="sr-only">{message?.tone === 'success' ? message.text : ''}</p>
      {message && <Alert tone={message.tone}>{message.text}</Alert>}
      <Card title="Add a tournament">
        <form
          className="grid gap-3 sm:grid-cols-3"
          onSubmit={(event) => {
            event.preventDefault()
            attempt(() => createTournament({ name: form.name.trim(), sport: form.sport.trim(), season: form.season.trim() }), 'Tournament added as a draft.', 'Couldn’t add the tournament.', notify)
          }}
        >
          <TextField label="Tournament name" value={form.name} maxLength={150} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          <TextField label="Sport" value={form.sport} maxLength={100} onChange={(event) => setForm({ ...form, sport: event.target.value })} />
          <TextField label="Season" value={form.season} maxLength={30} onChange={(event) => setForm({ ...form, season: event.target.value })} />
          <div><Button type="submit">Add tournament</Button></div>
        </form>
      </Card>
      {tournaments.status !== 'loaded' ? (
        <SectionState request={tournaments} what="tournaments" />
      ) : tournaments.data.length === 0 ? (
        <EmptyState title="No tournaments yet." description="Add one above." />
      ) : (
        tournaments.data.map((tournament) => (
          <TournamentCard key={tournament.tournamentId} tournament={tournament} teams={teams.status === 'loaded' ? teams.data : []} notify={notify} />
        ))
      )}
    </div>
  )
}
