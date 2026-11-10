import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { addTeamPlayer, changeTeamRole, createTeam, deactivateTeam, getGroupTeams, removeTeamPlayer } from '../api/engagementApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { SectionState } from '../courses/SectionState'
import type { CampusTeam, TeamRole } from '../types/engagement'
import { Badge, Button, SelectField, TextField } from '../ui'
import { failureText, TEAM_ROLE_LABEL } from './engagementText'

const ROLES: TeamRole[] = ['PLAYER', 'CAPTAIN', 'VICE_CAPTAIN']

type Notify = (text: string, error?: boolean) => void

function Team({ team, canManage, notify }: { team: CampusTeam; canManage: boolean; notify: Notify }) {
  const [loginId, setLoginId] = useState('')
  const [role, setRole] = useState<TeamRole>('PLAYER')
  const run = (action: () => Promise<unknown>, done: string, fallback: string) =>
    void action()
      .then(() => notify(done))
      .catch((caught: unknown) => notify(failureText(caught, fallback) ?? '', true))
  const editable = canManage && team.active
  return (
    <section aria-label={`${team.name} ${team.season}`} className="space-y-2 rounded-control border border-line p-3">
      <div className="flex flex-wrap items-center gap-2 text-sm">
        <span className="font-semibold text-ink">{team.name}</span>
        <span className="text-ink-muted">{team.season}</span>
        {!team.active && <Badge tone="neutral">Inactive</Badge>}
        {editable && (
          <Button size="sm" variant="secondary" onClick={() => run(() => deactivateTeam(team.teamId), 'Team made inactive. Its roster is kept.', 'Couldn’t change the team.')}>
            Make inactive<span className="sr-only">{` ${team.name} ${team.season}`}</span>
          </Button>
        )}
      </div>
      {team.players.length === 0 ? (
        <p className="text-sm text-ink-muted">No players yet.</p>
      ) : (
        <ul className="space-y-1 text-sm text-ink">
          {team.players.map((player) => (
            <li key={player.playerId} className="flex flex-wrap items-center gap-2">
              <span className="font-mono">{player.loginId}</span>
              {player.role !== 'PLAYER' && <Badge tone="info">{TEAM_ROLE_LABEL[player.role]}</Badge>}
              {editable && player.role !== 'CAPTAIN' && (
                <Button size="sm" variant="secondary" onClick={() => run(() => changeTeamRole(player.playerId, 'CAPTAIN'), `${player.loginId} is now captain.`, 'Couldn’t change the role.')}>
                  Make captain<span className="sr-only">{` ${player.loginId}`}</span>
                </Button>
              )}
              {editable && (
                <Button size="sm" variant="secondary" onClick={() => run(() => removeTeamPlayer(player.playerId), `${player.loginId} taken off the team.`, 'Couldn’t change the roster.')}>
                  Remove<span className="sr-only">{` ${player.loginId}`}</span>
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
      {editable && (
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            if (loginId.trim() !== '') {
              run(() => addTeamPlayer(team.teamId, loginId.trim(), role), `${loginId.trim()} added to ${team.name}.`, 'Couldn’t add the player.')
              setLoginId('')
            }
          }}
        >
          <TextField label={`Player’s college ID for ${team.name}`} value={loginId} maxLength={100} onChange={(event) => setLoginId(event.target.value)} />
          <SelectField label="Role" value={role} onChange={(event) => setRole(event.target.value as TeamRole)}>
            {ROLES.map((value) => <option key={value} value={value}>{TEAM_ROLE_LABEL[value]}</option>)}
          </SelectField>
          <Button type="submit" size="sm">Add player</Button>
        </form>
      )}
    </section>
  )
}

function Teams({ groupId, groupName }: { groupId: number; groupName: string }) {
  const queryClient = useQueryClient()
  const teams = useApiQuery(queryKeys.engagement.teams(groupId), (signal) => getGroupTeams(groupId, signal))
  const [form, setForm] = useState({ name: '', season: '' })
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)
  const notify: Notify = (text, error = false) => {
    setMessage({ tone: error ? 'error' : 'success', text })
    void queryClient.invalidateQueries({ queryKey: queryKeys.engagement.teams(groupId) })
  }
  if (teams.status !== 'loaded') {
    return <SectionState request={teams} what="teams" />
  }
  return (
    <div role="region" aria-label={`Teams of ${groupName}`} className="space-y-3">
      <p role="status" className={`text-sm empty:hidden ${message?.tone === 'error' ? 'text-danger' : 'text-success'}`}>
        {message?.text ?? ''}
      </p>
      {teams.data.teams.length === 0 && <p className="text-sm text-ink-muted">No teams yet.</p>}
      {teams.data.teams.map((team) => <Team key={team.teamId} team={team} canManage={teams.data.canManage} notify={notify} />)}
      {teams.data.canManage && (
        <form
          className="flex flex-wrap items-end gap-2"
          onSubmit={(event) => {
            event.preventDefault()
            void createTeam(groupId, form.name.trim(), form.season.trim())
              .then(() => {
                setForm({ name: '', season: '' })
                notify('Team added.')
              })
              .catch((caught: unknown) => notify(failureText(caught, 'Couldn’t add the team.') ?? '', true))
          }}
        >
          <TextField label="Team name" value={form.name} maxLength={150} onChange={(event) => setForm({ ...form, name: event.target.value })} />
          <TextField label="Season" hint="For example 2026-27." value={form.season} maxLength={30} onChange={(event) => setForm({ ...form, season: event.target.value })} />
          <Button type="submit" size="sm">Add team</Button>
        </form>
      )}
    </div>
  )
}

/** A sports group's teams and rosters, opened on demand; officers and the campus office manage them. */
export function TeamsPanel({ groupId, groupName }: { groupId: number; groupName: string }) {
  const [open, setOpen] = useState(false)
  if (!open) {
    return (
      <Button size="sm" variant="secondary" onClick={() => setOpen(true)}>
        Teams<span className="sr-only">{` of ${groupName}`}</span>
      </Button>
    )
  }
  return <Teams groupId={groupId} groupName={groupName} />
}
