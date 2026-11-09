import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { SignInForm } from './SignInForm'

function refused(status: number, message: string): AxiosError {
  const response = { status, data: { status, message, fieldErrors: [] } } as unknown as AxiosResponse
  return new AxiosError(message, 'ERR_BAD_REQUEST', undefined, undefined, response)
}

function renderWith(login: AuthContextValue['login']) {
  const auth: AuthContextValue = {
    isChecking: false,
    isAuthenticated: false,
    account: null,
    passwordChangeRequired: false,
    passwordChanged: vi.fn(),
    signedOutByUser: false,
    login,
    logout: vi.fn(),
    logoutEverywhere: vi.fn(),
  }
  render(
    <AuthContext value={auth}>
      <SignInForm autoFocus={false} />
    </AuthContext>,
  )
}

async function signIn() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('College ID / Employee ID'), 'student1')
  await user.type(screen.getByLabelText('Password', { exact: true }), 'not-the-password')
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('SignInForm', () => {
  it('shows one generic message for a wrong password', async () => {
    renderWith(vi.fn().mockRejectedValue(refused(401, 'Invalid login ID or password.')))
    await signIn()
    expect(await screen.findByText('Invalid login ID or password.')).toBeInTheDocument()
  })

  it('asks the person to wait after too many attempts, without saying whether the ID exists', async () => {
    renderWith(vi.fn().mockRejectedValue(refused(429, 'anything the server says')))
    await signIn()
    expect(await screen.findByText('Too many sign-in attempts. Wait a few minutes and try again.')).toBeInTheDocument()
  })
})
