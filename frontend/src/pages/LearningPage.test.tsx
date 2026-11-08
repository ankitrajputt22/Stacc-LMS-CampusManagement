import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AxiosError, type AxiosResponse } from 'axios'
import * as contentApi from '../api/lmsContentApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { LearningPage } from './LearningPage'

vi.mock('../api/lmsContentApi', () => ({ getLmsCoursePage: vi.fn(), downloadMaterialFile: vi.fn() }))

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

function show(path: string) {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/courses/:lmsCourseId/pages/:pageId" element={<LearningPage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

describe('LearningPage', () => {
  it('shows the page with its formatted text and the pages around it', async () => {
    vi.mocked(contentApi.getLmsCoursePage).mockResolvedValue({
      id: 32, title: 'Singly linked lists', content: '## Insertion\n\nAdd after the **head**.', contentFormat: 'FORMATTED',
      moduleId: 12, moduleTitle: 'Unit 1', materials: [],
      previous: { id: 31, title: 'Arrays', moduleTitle: 'Unit 1' }, next: null,
    })
    show('/courses/42/pages/32')

    expect(await screen.findByRole('heading', { level: 1, name: 'Singly linked lists' }, { timeout: 5000 })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { level: 3, name: 'Insertion' }, { timeout: 5000 })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Arrays/ })).toHaveAttribute('href', '/courses/42/pages/31')
    expect(contentApi.getLmsCoursePage).toHaveBeenCalledWith(42, 32, expect.anything())
  })

  it('says a hidden or missing page is unavailable, and asks nothing for an impossible address', async () => {
    vi.mocked(contentApi.getLmsCoursePage).mockReset().mockRejectedValue(
      new AxiosError('missing', 'ERR_BAD_REQUEST', undefined, undefined, { status: 404, data: {} } as unknown as AxiosResponse),
    )
    show('/courses/42/pages/99')
    expect(await screen.findByRole('heading', { level: 1, name: 'Page unavailable' }, { timeout: 5000 })).toBeInTheDocument()

    vi.mocked(contentApi.getLmsCoursePage).mockClear()
    show('/courses/42/pages/abc')
    expect(contentApi.getLmsCoursePage).not.toHaveBeenCalled()
  })
})
