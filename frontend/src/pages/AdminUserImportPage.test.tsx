import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as adminUsersApi from '../api/adminUsersApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { AdminUserImportPage } from './AdminUserImportPage'

vi.mock('../api/adminUsersApi', () => ({ importUsers: vi.fn() }))

const auth: AuthContextValue = {
  isChecking: false,
  isAuthenticated: true,
  account: { accountId: 1, loginId: 'admin1', roles: ['ADMIN'] },
  passwordChangeRequired: false,
  passwordChanged: vi.fn(),
  signedOutByUser: false,
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

function renderPage() {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <AuthContext value={auth}>
          <AdminUserImportPage />
        </AuthContext>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function chooseFileAndCheck() {
  const user = userEvent.setup()
  await user.upload(screen.getByLabelText('File to import'), new File(['login_id\n'], 'people.csv', { type: 'text/csv' }))
  await user.type(screen.getByLabelText('Temporary password for every account'), 'Bright-Lantern-2047')
  await user.click(screen.getByRole('button', { name: 'Check file' }))
  return user
}

describe('AdminUserImportPage', () => {
  beforeEach(() => vi.mocked(adminUsersApi.importUsers).mockReset())

  it('lists each row’s problems and offers no import while any row has one', async () => {
    vi.mocked(adminUsersApi.importUsers).mockResolvedValue({
      dryRun: true, created: 0, rowsWithErrors: 1,
      rows: [
        { line: 2, loginId: '26CSE001', errors: [] },
        { line: 3, loginId: 'student1', errors: ['An account with this login ID already exists.'] },
      ],
    })
    renderPage()
    await chooseFileAndCheck()

    expect(await screen.findAllByText('An account with this login ID already exists.')).not.toHaveLength(0)
    expect(screen.queryByRole('button', { name: /^Import/ })).not.toBeInTheDocument()
    expect(adminUsersApi.importUsers).toHaveBeenCalledWith(expect.any(File), 'Bright-Lantern-2047', true)
  })

  it('imports only after a clean check and a confirmation', async () => {
    vi.mocked(adminUsersApi.importUsers)
      .mockResolvedValueOnce({ dryRun: true, created: 0, rowsWithErrors: 0, rows: [{ line: 2, loginId: '26CSE001', errors: [] }] })
      .mockResolvedValueOnce({ dryRun: false, created: 1, rowsWithErrors: 0, rows: [{ line: 2, loginId: '26CSE001', errors: [] }] })
    renderPage()
    const user = await chooseFileAndCheck()

    await user.click(await screen.findByRole('button', { name: 'Import 1 accounts' }))
    await user.click(screen.getByRole('button', { name: 'Create accounts' }))

    expect(adminUsersApi.importUsers).toHaveBeenLastCalledWith(expect.any(File), 'Bright-Lantern-2047', false)
    expect(await screen.findByText(/1 account was created/)).toBeInTheDocument()
  })
})
