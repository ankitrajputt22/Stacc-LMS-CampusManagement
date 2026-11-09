import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as syllabusApi from '../api/lmsSyllabusApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { TeachingSyllabusPage } from './TeachingSyllabusPage'

vi.mock('../api/lmsSyllabusApi', () => ({
  getFacultySyllabus: vi.fn(),
  saveSyllabusDraft: vi.fn(),
  publishSyllabus: vi.fn(),
}))

const auth: AuthContextValue = {
  isChecking: false,
  isAuthenticated: true,
  account: { accountId: 21, loginId: 'faculty1', roles: ['FACULTY'] },
  passwordChangeRequired: false,
  passwordChanged: vi.fn(),
  signedOutByUser: false,
  login: vi.fn(),
  logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

const none = {
  draftContent: null, draftUpdatedAt: null, publishedContent: null, firstPublishedAt: null, lastPublishedAt: null,
  unpublishedChanges: false,
}

function show() {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/teaching/42/syllabus']}>
          <Routes>
            <Route path="/teaching/:lmsCourseId/syllabus" element={<TeachingSyllabusPage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

describe('TeachingSyllabusPage', () => {
  beforeEach(() => {
    vi.mocked(syllabusApi.getFacultySyllabus).mockReset().mockResolvedValue(none)
    vi.mocked(syllabusApi.saveSyllabusDraft).mockReset().mockResolvedValue({ ...none, draftContent: 'x', unpublishedChanges: true })
    vi.mocked(syllabusApi.publishSyllabus).mockReset().mockResolvedValue(none)
  })

  it('starts a new syllabus from the usual sections and saves a draft', async () => {
    const user = userEvent.setup()
    show()

    const text = await screen.findByLabelText('Syllabus')
    expect((text as HTMLTextAreaElement).value).toContain('## Learning objectives')
    expect(screen.getByText('No syllabus yet. Start from the sections below.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Text format')).toBeNull()

    await user.clear(text)
    await user.type(text, '## Overview')
    await user.click(screen.getByRole('button', { name: 'Save draft' }))
    expect(syllabusApi.saveSyllabusDraft).toHaveBeenCalledWith(42, '## Overview')
    expect(syllabusApi.publishSyllabus).not.toHaveBeenCalled()
  })

  it('publishes only after confirmation, saving the text first', async () => {
    const user = userEvent.setup()
    show()

    await user.click(await screen.findByRole('button', { name: 'Publish' }))
    expect(syllabusApi.publishSyllabus).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Publish syllabus' }))

    expect(syllabusApi.saveSyllabusDraft).toHaveBeenCalledWith(42, expect.stringContaining('## Course overview'))
    expect(syllabusApi.publishSyllabus).toHaveBeenCalledWith(42)
  })
})
