import { useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useState } from 'react'
import { getApiError, getHttpStatus } from '../api/apiError'
import {
  createHelpdeskTeam,
  getAdminHelpdeskCategories,
  getHelpdeskTeams,
  routeHelpdeskCategory,
  setHelpdeskTeamMembers,
  updateHelpdeskTeam,
} from '../api/helpdeskApi'
import { queryKeys } from '../api/queryKeys'
import { useApiQuery } from '../api/useApiQuery'
import { Breadcrumb } from '../components/navigation/Breadcrumb'
import { EmptyState } from '../components/states/EmptyState'
import { SectionState } from '../courses/SectionState'
import { refusalText } from '../support/helpdeskText'
import type { HelpdeskTeam } from '../types/helpdesk'
import { Alert, Badge, Button, Card, PageHeader, SelectField, TextField } from '../ui'

function failure(error: unknown, what: string): string | null {
  const status = getHttpStatus(error)
  return status === 401 ? null : refusalText(status, getApiError(error)?.message ?? null, what)
}

function TeamRow({ team, onSaved }: { team: HelpdeskTeam; onSaved: (message: string) => Promise<void> }) {
  const [members, setMembers] = useState(team.memberLoginIds.join(', '))
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function run(action: () => Promise<unknown>, done: string, what: string) {
    setBusy(true)
    setError(null)
    try {
      await action()
      await onSaved(done)
    } catch (caught) {
      setError(failure(caught, what))
    } finally {
      setBusy(false)
    }
  }

  return (
    <li className="space-y-3 px-4 py-4 sm:px-5">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold text-ink">{team.name}</span>
        <span className="font-mono text-xs text-ink-muted">{team.code}</span>
        {!team.active && <Badge tone="neutral">Inactive</Badge>}
        <Button
          variant="secondary"
          size="sm"
          disabled={busy}
          onClick={() =>
            void run(() => updateHelpdeskTeam(team.teamId, team.name, !team.active), team.active ? 'Team made inactive.' : 'Team made active.', 'change the team')
          }
        >
          {team.active ? 'Make inactive' : 'Make active'}
          <span className="sr-only">{`: ${team.name}`}</span>
        </Button>
      </div>
      <form
        className="flex flex-wrap items-end gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          const loginIds = members.split(',').map((id) => id.trim()).filter((id) => id !== '')
          void run(() => setHelpdeskTeamMembers(team.teamId, loginIds), 'Members saved.', 'save the members')
        }}
      >
        <div className="min-w-64 flex-1">
          <TextField
            label={`Members of ${team.name}`}
            hint="Login IDs separated by commas: admins or staff who work support tickets."
            value={members}
            onChange={(event) => setMembers(event.target.value)}
          />
        </div>
        <Button type="submit" variant="secondary" size="sm" disabled={busy}>
          Save members
        </Button>
      </form>
      {error && <Alert tone="error">{error}</Alert>}
    </li>
  )
}

/**
 * /support/admin/teams: support teams, their members, and which team each category's new tickets go to. Teams are made
 * inactive, never deleted. The backend checks every change.
 */
export function SupportTeamsPage() {
  const queryClient = useQueryClient()
  const teams = useApiQuery(queryKeys.helpdesk.teams(), (signal) => getHelpdeskTeams(signal))
  const categories = useApiQuery(['helpdesk', 'admin-categories'], (signal) => getAdminHelpdeskCategories(signal))
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null)

  async function saved(text: string) {
    setMessage({ tone: 'success', text })
    await queryClient.invalidateQueries({ queryKey: ['helpdesk'] })
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    try {
      await createHelpdeskTeam(code.trim().toUpperCase(), name.trim())
      setCode('')
      setName('')
      await saved('Team added.')
    } catch (caught) {
      const text = failure(caught, 'add the team')
      if (text) {
        setMessage({ tone: 'error', text })
      }
    }
  }

  return (
    <div className="max-w-4xl">
      <title>Support teams · Stacc</title>
      <PageHeader
        title="Support teams"
        breadcrumb={<Breadcrumb parent={{ to: '/support', label: 'Support' }} current="Teams" />}
        description="Teams work the tickets of the categories routed to them. Admins with every ticket still see them all."
      />
      <div className="mt-6 space-y-6">
        {message && <Alert tone={message.tone}>{message.text}</Alert>}
        <Card title="Add a team">
          <form className="grid gap-3 sm:grid-cols-[12rem_1fr_auto] sm:items-end" onSubmit={(event) => void create(event)}>
            <TextField label="Code" value={code} maxLength={50} hint="Uppercase, fixed later." onChange={(event) => setCode(event.target.value)} />
            <TextField label="Name" value={name} maxLength={150} onChange={(event) => setName(event.target.value)} />
            <Button type="submit">Add team</Button>
          </form>
        </Card>
        <Card title="Teams" padding="none">
          {teams.status !== 'loaded' ? (
            <SectionState request={teams} what="teams" />
          ) : teams.data.length === 0 ? (
            <EmptyState title="No teams yet." description="Add a team, then route categories to it." />
          ) : (
            <ul className="divide-y divide-line">
              {teams.data.map((team) => (
                <TeamRow key={`${team.teamId}-${team.memberLoginIds.join(',')}-${team.active}`} team={team} onSaved={saved} />
              ))}
            </ul>
          )}
        </Card>
        <Card title="Routing" description="Which team new tickets of each category go to." padding="none">
          {categories.status !== 'loaded' || teams.status !== 'loaded' ? (
            <SectionState request={categories} what="categories" />
          ) : (
            <ul className="divide-y divide-line">
              {categories.data.map((category) => (
                <li key={category.categoryId} className="grid gap-2 px-4 py-3 sm:grid-cols-[1fr_16rem] sm:items-end sm:px-5">
                  <span className="text-sm font-medium text-ink">{category.name}</span>
                  <SelectField
                    label={`Team for ${category.name}`}
                    defaultValue=""
                    onChange={(event) =>
                      void routeHelpdeskCategory(category.categoryId, event.target.value === '' ? null : Number(event.target.value))
                        .then(() => saved(`New ${category.name} tickets now go to the chosen team.`))
                        .catch((caught: unknown) => {
                          const text = failure(caught, 'route the category')
                          if (text) {
                            setMessage({ tone: 'error', text })
                          }
                        })
                    }
                  >
                    <option value="">Choose a team…</option>
                    {teams.data.filter((team) => team.active).map((team) => (
                      <option key={team.teamId} value={team.teamId}>
                        {team.name}
                      </option>
                    ))}
                  </SelectField>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </div>
  )
}
