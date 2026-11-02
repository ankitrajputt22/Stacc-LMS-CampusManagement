import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as personApi from '../api/personApi'
import * as sessionsApi from '../api/sessionsApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { AccountSettingsPage } from './AccountSettingsPage'

vi.mock('../api/personApi', () => ({ getMyProfile: vi.fn(), updateMyProfile: vi.fn(), getMyPhoto: vi.fn() }))
vi.mock('../api/sessionsApi', () => ({ getMySessions: vi.fn(), endMySession: vi.fn() }))

const logoutEverywhere = vi.fn().mockResolvedValue(undefined)
const auth: AuthContextValue = {
  isChecking: false,
  isAuthenticated: true,
  account: { accountId: 7, loginId: 'student1', roles: ['STUDENT'] },
  passwordChangeRequired: false,
  passwordChanged: vi.fn(),
  signedOutByUser: false,
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere,
}

const profile = {
  loginId: 'student1', displayName: 'Aarav Sharma', recorded: true, fullName: 'Aarav Sharma', preferredName: null,
  email: 'aarav@college.example', phone: null, hasPhoto: false,
}

function renderPage() {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <AuthContext value={auth}>
          <AccountSettingsPage />
        </AuthContext>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AccountSettingsPage', () => {
  beforeEach(() => {
    vi.mocked(personApi.getMyProfile).mockResolvedValue(profile)
    vi.mocked(personApi.updateMyProfile).mockResolvedValue({ ...profile, preferredName: 'Aarav', displayName: 'Aarav' })
    vi.mocked(sessionsApi.getMySessions).mockResolvedValue([
      { sessionId: 'a', device: 'Chrome on macOS', signedInAt: '2026-11-03T08:00:00Z', lastSeenAt: '2026-11-03T09:00:00Z', expiresAt: '2026-11-03T20:00:00Z', current: true },
      { sessionId: 'b', device: 'Safari on iPhone', signedInAt: '2026-11-02T08:00:00Z', lastSeenAt: '2026-11-02T09:00:00Z', expiresAt: '2026-11-02T20:00:00Z', current: false },
    ])
    vi.mocked(sessionsApi.endMySession).mockReset().mockResolvedValue(undefined)
  })

  it('shows the official details and saves only the preferred name and phone', async () => {
    const user = userEvent.setup()
    renderPage()

    expect(await screen.findByText('aarav@college.example')).toBeInTheDocument()
    await user.type(screen.getByLabelText(/Preferred name/), 'Aarav')
    await user.click(screen.getByRole('button', { name: 'Save details' }))

    expect(personApi.updateMyProfile).toHaveBeenCalledWith('Aarav', null)
    expect(await screen.findByText('Your details are saved.')).toBeInTheDocument()
  })

  it('signs out another browser only after confirming, and never offers it for this one', async () => {
    const user = userEvent.setup()
    renderPage()

    const list = await screen.findByRole('list')
    expect(within(list).getByText('This browser')).toBeInTheDocument()
    expect(within(list).getAllByRole('button', { name: /^Sign out/ })).toHaveLength(1)
    await user.click(within(list).getByRole('button', { name: /Sign out\s*Safari on iPhone/ }))
    expect(sessionsApi.endMySession).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Sign out session' }))

    expect(sessionsApi.endMySession).toHaveBeenCalledWith('b')
    expect(await screen.findByText('That session is signed out.')).toBeInTheDocument()
  })
})
