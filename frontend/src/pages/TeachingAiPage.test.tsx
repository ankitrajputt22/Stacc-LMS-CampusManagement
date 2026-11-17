import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import * as content from '../teaching/useFacultyCourseContent'
import { TeachingAiPage } from './TeachingAiPage'

vi.mock('../teaching/useFacultyCourseContent')
vi.mock('../api/aiApi')
vi.mock('../teaching/useFacultyAssignments', () => ({ useFacultyAssignments: () => ({ status: 'loading' }) }))

const auth: AuthContextValue = {
  isChecking: false, isAuthenticated: true, account: { accountId: 9, loginId: 'emp1021', roles: ['FACULTY'] },
  passwordChangeRequired: false, passwordChanged: vi.fn(), signedOutByUser: false, login: vi.fn(), logout: vi.fn(),
  logoutEverywhere: vi.fn(),
}

function renderAt(path: string) {
  vi.mocked(content.useFacultyCourseContent).mockReturnValue({
    status: 'loaded', data: { lmsCourseId: 3, courseCode: 'CS301', courseName: 'Databases', modules: [] }, retry: vi.fn(), refresh: vi.fn(),
  } as never)
  render(
    <QueryClientProvider client={testQueryClient()}>
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/teaching/:lmsCourseId/ai" element={<TeachingAiPage />} />
            <Route path="/teaching/:lmsCourseId/ai/:section" element={<TeachingAiPage />} />
          </Routes>
        </MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

describe('TeachingAiPage', () => {
  it('is one Teacher AI area with a tab per section', () => {
    renderAt('/teaching/3/ai/quizzes')
    expect(screen.getByRole('heading', { level: 1, name: 'Teacher AI' })).toBeInTheDocument()
    const tabs = screen.getByRole('navigation', { name: 'Teacher AI sections' })
    expect(tabs).toHaveTextContent('KnowledgeQuestion bankQuizzesAssignmentsMore')
    expect(screen.getByRole('link', { name: 'Quizzes' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('heading', { name: 'Build a draft quiz' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Draft into the question bank' })).not.toBeInTheDocument()
  })

  it('points to the AI help on other teaching pages', () => {
    renderAt('/teaching/3/ai/more')
    expect(screen.getByRole('link', { name: 'Rubrics' })).toHaveAttribute('href', '/teaching/3/rubrics')
    expect(screen.getByRole('link', { name: 'Exam papers' })).toHaveAttribute('href', '/teaching/3/papers')
  })
})
