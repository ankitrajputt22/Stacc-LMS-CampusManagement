import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as authApi from '../api/authApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { ChangePasswordPage } from './ChangePasswordPage'

vi.mock('../api/authApi', () => ({ changePassword: vi.fn() }))

function refusal(message: string): AxiosError {
  const response = { status: 400, data: { status: 400, message, fieldErrors: [] } } as unknown as AxiosResponse
  return new AxiosError(message, 'ERR_BAD_REQUEST', undefined, undefined, response)
}

describe('ChangePasswordPage', () => {
  let auth: AuthContextValue

  function renderPage(required = false) {
    render(
      <MemoryRouter>
        <AuthContext value={auth}>
          <ChangePasswordPage required={required} />
        </AuthContext>
      </MemoryRouter>,
    )
  }

  async function fill(current: string, next: string, again = next) {
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/current password|temporary password/i), current)
    await user.type(screen.getByLabelText('New password', { exact: false, selector: '#change-password-newPassword' }), next)
    await user.type(screen.getByLabelText('New password again'), again)
    await user.click(screen.getByRole('button', { name: 'Change password' }))
  }

  beforeEach(() => {
    vi.mocked(authApi.changePassword).mockReset()
    auth = {
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
  })

  it('checks the new password before sending anything', async () => {
    renderPage()

    await fill('old river lamp', 'short', 'short')
    expect(screen.getByText('Use at least 10 characters.')).toBeInTheDocument()

    expect(authApi.changePassword).not.toHaveBeenCalled()
  })

  it('refuses two different new passwords', async () => {
    renderPage()

    await fill('old river lamp', 'new cocoa window 77', 'new cocoa window 78')

    expect(screen.getByText('The two new passwords do not match.')).toBeInTheDocument()
    expect(authApi.changePassword).not.toHaveBeenCalled()
  })

  it('sends the passwords exactly as typed, then clears the form and says other devices were signed out', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue()
    renderPage()

    await fill(' old river lamp ', 'new cocoa window 77')

    expect(authApi.changePassword).toHaveBeenCalledWith(' old river lamp ', 'new cocoa window 77')
    expect(await screen.findByText('Password changed.')).toBeInTheDocument()
    expect(screen.getByLabelText('New password again')).toHaveValue('')
    expect(auth.passwordChanged).toHaveBeenCalledTimes(1)
  })

  it('shows the backend refusal on the field it concerns', async () => {
    vi.mocked(authApi.changePassword).mockRejectedValue(refusal('Your current password is not correct.'))
    renderPage()

    await fill('wrong one here', 'new cocoa window 77')

    expect(await screen.findByText('Your current password is not correct.')).toBeInTheDocument()
    expect(screen.getByLabelText('Current password')).toHaveAttribute('aria-invalid', 'true')
  })

  it('offers only signing out besides the change when the college requires one', async () => {
    renderPage(true)

    expect(screen.getByText(/asked you to choose a new password/)).toBeInTheDocument()
    expect(screen.getByLabelText('Temporary password')).toBeInTheDocument()
    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign out' }))
    expect(auth.logout).toHaveBeenCalledTimes(1)
  })
})
