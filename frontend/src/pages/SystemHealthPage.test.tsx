import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/systemApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { SystemHealthPage } from './SystemHealthPage'

vi.mock('../api/systemApi')

const auth: AuthContextValue = {
  isChecking: false, isAuthenticated: true, account: { accountId: 1, loginId: 'adm1001', roles: ['ADMIN'] },
  passwordChangeRequired: false, passwordChanged: vi.fn(), signedOutByUser: false, login: vi.fn(), logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

describe('SystemHealthPage', () => {
  it('shows AI health as numbers in words', async () => {
    vi.mocked(api.getSystemHealth).mockResolvedValue({
      status: 'UP', parts: { db: 'UP' }, schemaVersion: '133', migrations: 133, mailEnabled: false, aiEnabled: true,
      metricsEnabled: false, profiles: [], startedAt: '2026-11-15T05:00:00Z', uptimeMinutes: 5, heapUsedMb: 100, heapMaxMb: 512,
      processors: 4, threads: 40,
    })
    vi.mocked(api.getAiHealth).mockResolvedValue({
      enabled: true, reachable: true, courseIndexes: { READY: 3 }, teacherIndexes: {}, jobs: { SUCCEEDED: 2 }, running: 1,
      waiting: 0, requestsByOutcome: { BUSY: 1, SUCCEEDED: 10 }, requestsByKind: { ASK: 11 }, averageMillis: 2400,
      coursesWithStudentAiOff: 1,
    })
    render(
      <QueryClientProvider client={testQueryClient()}>
        <AuthContext.Provider value={auth}>
          <SystemHealthPage />
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
    expect(await screen.findByText('Answering')).toBeInTheDocument()
    const now = screen.getByRole('region', { name: 'Requests now' })
    expect(within(now).getByText('Running')).toBeInTheDocument()
    expect(within(now).getByText('Waiting')).toBeInTheDocument()
    const outcomes = screen.getByRole('region', { name: 'Requests in the last 24 hours' })
    expect(within(outcomes).getByText('Busy')).toBeInTheDocument()
    expect(within(outcomes).getByText('10')).toBeInTheDocument()
    expect(screen.getByText('2.4 s')).toBeInTheDocument()
  })
})
