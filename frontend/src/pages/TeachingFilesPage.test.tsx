import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as contentApi from '../api/lmsContentApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import type { LmsFacultyCourseFile } from '../types/lms'
import { TeachingFilesPage } from './TeachingFilesPage'

vi.mock('../api/lmsContentApi')

const auth: AuthContextValue = {
  isChecking: false,
  isAuthenticated: true,
  account: { accountId: 9, loginId: 'emp1021', roles: ['FACULTY'] },
  passwordChangeRequired: false,
  passwordChanged: vi.fn(),
  signedOutByUser: false,
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

const file = (excludedFromAi: boolean): LmsFacultyCourseFile => ({
  id: 7, title: 'Lecture notes', description: null, originalFileName: 'lecture-notes.pdf', contentType: 'application/pdf',
  sizeBytes: 2048, uploadedAt: '2026-10-14T09:30:00Z', status: 'PUBLISHED', pageId: 31, pageTitle: 'Arrays',
  pageStatus: 'PUBLISHED', moduleTitle: 'Unit 1', moduleStatus: 'PUBLISHED', excludedFromAi,
})

const course = (excludedFromAi: boolean) => ({
  lmsCourseId: 42, courseCode: 'CS301', courseName: 'Databases', lmsCourseStatus: 'PUBLISHED' as const,
  files: [file(excludedFromAi)],
})

describe('TeachingFilesPage', () => {
  it('keeps a file out of AI knowledge and brings it back', async () => {
    vi.mocked(contentApi.getFacultyCourseFiles).mockResolvedValueOnce(course(false)).mockResolvedValue(course(true))
    vi.mocked(contentApi.getFacultyCourseContent).mockResolvedValue({ modules: [] } as never)
    vi.mocked(contentApi.changeMaterialAiExclusion).mockResolvedValue({} as never)
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={testQueryClient()}>
        <AuthContext.Provider value={auth}>
          <MemoryRouter initialEntries={['/teaching/42/files']}>
            <Routes>
              <Route path="/teaching/:lmsCourseId/files" element={<TeachingFilesPage />} />
            </Routes>
          </MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )

    await user.click((await screen.findAllByRole('button', { name: /^Exclude from AI/ }))[0])
    expect(contentApi.changeMaterialAiExclusion).toHaveBeenCalledWith(7, true)
    expect((await screen.findAllByText('Kept out')).length).toBeGreaterThan(0)

    await user.click(screen.getAllByRole('button', { name: /^Include in AI/ })[0])
    expect(contentApi.changeMaterialAiExclusion).toHaveBeenLastCalledWith(7, false)
  })
})
