import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Tournament } from '../types/engagement'
import { TournamentTables } from './TournamentTables'

const tournament: Tournament = {
  tournamentId: 1, name: 'Inter-college cup', sport: 'Football', season: '2026-27', status: 'PUBLISHED',
  fixtures: [
    { fixtureId: 1, home: { teamId: 4, name: 'First XI (2026-27)', ours: true }, away: { teamId: null, name: 'City College', ours: false },
      startsAt: '2026-11-20T10:00:00Z', venue: 'Main ground', status: 'PLAYED', homeScore: '2', awayScore: '1', result: 'HOME_WIN' },
    { fixtureId: 2, home: { teamId: null, name: 'City College', ours: false }, away: { teamId: 4, name: 'First XI (2026-27)', ours: true },
      startsAt: '2026-11-27T10:00:00Z', venue: null, status: 'CANCELLED', homeScore: null, awayScore: null, result: null },
  ],
  standings: [
    { side: 'First XI (2026-27)', ours: true, played: 1, won: 1, drawn: 0, lost: 0, points: 3 },
    { side: 'City College', ours: false, played: 1, won: 0, drawn: 0, lost: 1, points: 0 },
  ],
}

describe('TournamentTables', () => {
  it('shows text scores with the winner, cancelled matches in words, and the standings as sent', () => {
    render(<TournamentTables tournament={tournament} />)
    expect(screen.getAllByText('2 – 1 · First XI (2026-27) won').length).toBeGreaterThan(0)
    expect(screen.getAllByText('Cancelled').length).toBeGreaterThan(0)
    expect(screen.getByRole('table', { name: 'Standings of Inter-college cup' })).toBeInTheDocument()
  })
})
