import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { StudyHomePage } from './StudyHomePage'

vi.mock('../api/aiApi')

const auth: AuthContextValue = {
  isChecking: false, isAuthenticated: true, account: { accountId: 7, loginId: 'cse24001', roles: ['STUDENT'] },
  passwordChangeRequired: false, passwordChanged: vi.fn(), signedOutByUser: false, login: vi.fn(), logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

describe('StudyHomePage', () => {
  it('shows each course’s mastery, flashcards due, and next step, with a link to its study area', async () => {
    vi.mocked(api.getStudyHome).mockResolvedValue([{
      lmsCourseId: 3, courseCode: 'CS301', courseName: 'Databases',
      pagesByLevel: { NOT_STARTED: 2, LEARNING: 1, PRACTISING: 1, MASTERED: 3 }, flashcardsDue: 4,
      nextStep: { kind: 'PRACTISE_WEAK', targetId: 31, title: 'Practise Joins', reason: 'Your recent answers here average 30%.' },
      assistantOn: true,
    }])
    render(
      <QueryClientProvider client={testQueryClient()}>
        <AuthContext.Provider value={auth}>
          <MemoryRouter><StudyHomePage /></MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
    expect(await screen.findByText('3 mastered · 1 practising · 1 learning · 2 not started · 4 flashcards due today')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Practise Joins' })).toHaveAttribute('href', '/courses/3/pages/31')
    expect(screen.getByRole('link', { name: /^Open study area/ })).toHaveAttribute('href', '/courses/3/ai')
    expect(screen.queryByText(/turned off for this course/)).not.toBeInTheDocument()
  })

  it('says when the course’s teachers have turned the assistant off', async () => {
    vi.mocked(api.getStudyHome).mockResolvedValue([{
      lmsCourseId: 3, courseCode: 'CS301', courseName: 'Databases',
      pagesByLevel: { NOT_STARTED: 1, LEARNING: 0, PRACTISING: 0, MASTERED: 0 }, flashcardsDue: 0, nextStep: null, assistantOn: false,
    }])
    render(
      <QueryClientProvider client={testQueryClient()}>
        <AuthContext.Provider value={auth}>
          <MemoryRouter><StudyHomePage /></MemoryRouter>
        </AuthContext.Provider>
      </QueryClientProvider>,
    )
    expect(await screen.findByText(/turned off for this course by its teachers/)).toBeInTheDocument()
  })
})
