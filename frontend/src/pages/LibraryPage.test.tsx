import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/libraryApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { LibraryPage } from './LibraryPage'

vi.mock('../api/libraryApi')

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

describe('LibraryPage', () => {
  it('shows overdue loans without Renew and lets a member join a queue, with no lending desk', async () => {
    vi.mocked(api.getMyLibrary).mockResolvedValue({
      loans: [{ loanId: 1, title: 'Algorithms', barcode: 'LIB-1', issuedAt: '2026-10-01T00:00:00Z', dueAt: '2026-10-15T00:00:00Z', renewed: false, overdue: true }],
      holds: [],
    })
    vi.mocked(api.searchLibrary).mockResolvedValue([
      { titleId: 4, title: 'Compilers', authors: 'Aho', isbn: null, publisher: null, publishedYear: 2006, subject: null, active: true, copies: 1, available: 0, holdsWaiting: 2, myHoldPosition: null },
    ])
    vi.mocked(api.getLibraryResources).mockResolvedValue([])
    vi.mocked(api.placeLibraryHold).mockResolvedValue()
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <AuthContext value={auth}>
          <LibraryPage />
        </AuthContext>
      </QueryClientProvider>,
    )
    expect(await screen.findByText('Overdue')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /^Renew/ })).not.toBeInTheDocument()
    await userEvent.click(await screen.findByRole('button', { name: /^Join queue\s*for Compilers$/ }))
    expect(api.placeLibraryHold).toHaveBeenCalledWith(4)
    expect(screen.queryByRole('region', { name: 'Lending desk' })).not.toBeInTheDocument()
  })
})
