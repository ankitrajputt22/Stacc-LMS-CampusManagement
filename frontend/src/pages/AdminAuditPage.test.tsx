import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as auditApi from '../api/auditApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { AdminAuditPage } from './AdminAuditPage'

vi.mock('../api/auditApi', () => ({ searchAuditEvents: vi.fn() }))

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

describe('AdminAuditPage', () => {
  it('shows recorded actions in words and sends the chosen filters', async () => {
    vi.mocked(auditApi.searchAuditEvents).mockResolvedValue({
      items: [
        {
          auditEventId: 9, occurredAt: '2026-11-03T10:00:00Z', actorLoginId: 'admin1', action: 'USER_ROLES_CHANGED',
          targetType: 'USER_ACCOUNT', targetId: 57, targetLoginId: '25CSE104', details: 'STUDENT to STUDENT, FACULTY.',
          requestId: 'req-1',
        },
      ],
      page: 0, size: 20, totalElements: 1, totalPages: 1,
    })
    render(
      <QueryClientProvider client={testQueryClient()}>
        <MemoryRouter>
          <AuthContext value={auth}>
            <AdminAuditPage />
          </AuthContext>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const table = await screen.findByRole('table', { name: 'Audit log' })
    expect(within(table).getByText('Roles changed')).toBeInTheDocument()
    expect(within(table).getByRole('link', { name: '25CSE104' })).toHaveAttribute('href', '/admin/users/57')

    await userEvent.setup().selectOptions(screen.getByLabelText('Action'), 'USER_STATUS_CHANGED')
    expect(auditApi.searchAuditEvents).toHaveBeenLastCalledWith(
      expect.objectContaining({ action: 'USER_STATUS_CHANGED', page: 0 }),
      expect.anything(),
    )
  })
})
