import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/portfolioApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { PortfolioPage } from './PortfolioPage'

vi.mock('../api/portfolioApi')

const auth: AuthContextValue = {
  isChecking: false,
  isAuthenticated: true,
  account: { accountId: 7, loginId: 'student1', roles: ['STUDENT'] },
  passwordChangeRequired: false,
  passwordChanged: vi.fn(),
  signedOutByUser: false,
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

describe('PortfolioPage', () => {
  it('shows records as sent and links only to https addresses', async () => {
    vi.mocked(api.getMyPortfolio).mockResolvedValue({
      records: [{ source: 'EVENT_ATTENDED', title: 'Open Source Day', detail: 'Attended', at: '2026-11-01T10:00:00Z' }],
      items: [
        { itemId: 1, kind: 'PROJECT', title: 'Campus app', description: null, occurredOn: null, link: 'https://example.org/app',
          verification: 'VERIFIED', verifierLoginId: 'faculty1', verificationNote: null, decidedAt: '2026-11-02T10:00:00Z' },
        { itemId: 2, kind: 'OTHER', title: 'Odd link', description: null, occurredOn: null, link: 'javascript:alert(1)',
          verification: 'NONE', verifierLoginId: null, verificationNote: null, decidedAt: null },
      ],
    })
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthContext value={auth}>
          <PortfolioPage />
        </AuthContext>
      </QueryClientProvider>,
    )
    expect(await screen.findByText('Open Source Day')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'https://example.org/app' })).toHaveAttribute('href', 'https://example.org/app')
    expect(screen.queryByRole('link', { name: 'javascript:alert(1)' })).not.toBeInTheDocument()
    expect(screen.getByText(/Verified · faculty1/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Verification requests' })).not.toBeInTheDocument()
  })
})
