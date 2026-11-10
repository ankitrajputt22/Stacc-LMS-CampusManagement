import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/engagementApi'
import type { GroupTeams } from '../types/engagement'
import { TeamsPanel } from './TeamsPanel'

vi.mock('../api/engagementApi')

const teams: GroupTeams = {
  canManage: false,
  teams: [{ teamId: 1, name: 'First XI', season: '2026-27', active: true, players: [{ playerId: 5, loginId: 'student2', role: 'CAPTAIN', joinedAt: '2026-11-01T00:00:00Z' }] }],
}

function renderTeams(answer: GroupTeams) {
  vi.mocked(api.getGroupTeams).mockResolvedValue(answer)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <TeamsPanel groupId={3} groupName="Cricket Team" />
    </QueryClientProvider>,
  )
}

describe('TeamsPanel', () => {
  it('shows the roster read-only to members', async () => {
    renderTeams(teams)
    await userEvent.click(screen.getByRole('button', { name: /^Teams\s*of Cricket Team$/ }))
    expect(await screen.findByText('Captain')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Add player' })).not.toBeInTheDocument()
  })

  it('lets a manager add a player with a role', async () => {
    vi.mocked(api.addTeamPlayer).mockResolvedValue(teams.teams[0])
    renderTeams({ ...teams, canManage: true })
    await userEvent.click(screen.getByRole('button', { name: /^Teams\s*of Cricket Team$/ }))
    await userEvent.type(await screen.findByRole('textbox', { name: /college ID for First XI/ }), 'student1')
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Role' }), 'VICE_CAPTAIN')
    await userEvent.click(screen.getByRole('button', { name: 'Add player' }))
    expect(api.addTeamPlayer).toHaveBeenCalledWith(1, 'student1', 'VICE_CAPTAIN')
  })
})
