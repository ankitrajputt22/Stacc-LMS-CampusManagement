import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as contentApi from '../api/lmsContentApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { CourseFilesPage } from './CourseFilesPage'

vi.mock('../api/lmsContentApi', () => ({ getLmsCourseFiles: vi.fn(), downloadMaterialFile: vi.fn() }))

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

const file = (id: number, title: string, pageTitle: string) => ({
  id, title, description: null, originalFileName: `${title.toLowerCase().replace(/ /g, '-')}.pdf`,
  contentType: 'application/pdf', sizeBytes: 2048, uploadedAt: '2026-10-14T09:30:00Z', pageId: id + 30, pageTitle,
  moduleTitle: 'Unit 1',
})

describe('CourseFilesPage', () => {
  it('lists the published files with their pages and narrows them by search', async () => {
    vi.mocked(contentApi.getLmsCourseFiles).mockResolvedValue([file(1, 'Lecture notes', 'Arrays'), file(2, 'Lab sheet', 'Stacks')])
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={testQueryClient()}>
        <AuthContext.Provider value={auth}>
          <MemoryRouter initialEntries={['/courses/42/files']}>
            <Routes>
              <Route path="/courses/:lmsCourseId/files" element={<CourseFilesPage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('Lecture notes')).length).toBeGreaterThan(0)
    expect(screen.getAllByRole('link', { name: 'Arrays' })[0]).toHaveAttribute('href', '/courses/42/pages/31')
    expect(contentApi.getLmsCourseFiles).toHaveBeenCalledWith(42, expect.anything())

    await user.type(screen.getByRole('searchbox', { name: 'Search files' }), 'stacks')
    expect(screen.queryAllByText('Lecture notes')).toHaveLength(0)
    expect(screen.getAllByText('Lab sheet').length).toBeGreaterThan(0)
    expect(screen.getByText('1 of 2 files')).toBeInTheDocument()
  })
})
