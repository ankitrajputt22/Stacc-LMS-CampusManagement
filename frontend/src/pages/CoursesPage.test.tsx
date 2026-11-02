import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as lmsCoursesApi from '../api/lmsCoursesApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import { CoursesPage } from './CoursesPage'

vi.mock('../api/lmsCoursesApi', () => ({ getMyLmsCourses: vi.fn() }))

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

const course = (lmsCourseId: number, courseCode: string, semesterNumber: number, sectionCode: string | null, faculty: string[]) => ({
  lmsCourseId,
  courseCode,
  courseName: `Course ${courseCode}`,
  credits: 4,
  semesterNumber,
  programCode: 'BTECH-CSE',
  academicSessionCode: '2026-27',
  sectionCode,
  faculty,
})

describe('CoursesPage', () => {
  it('groups courses by term and shows each section and its teachers', async () => {
    vi.mocked(lmsCoursesApi.getMyLmsCourses).mockResolvedValue([
      course(41, 'CS301', 3, null, ['Meera Iyer']),
      course(42, 'CS306', 3, 'A', []),
      course(43, 'CS401', 4, null, ['Sanjay Verma', 'Rohan Gupta']),
    ])
    render(
      <QueryClientProvider client={testQueryClient()}>
        <MemoryRouter>
          <AuthContext value={auth}>
            <CoursesPage />
          </AuthContext>
        </MemoryRouter>
      </QueryClientProvider>,
    )

    const third = await screen.findByRole('list', { name: 'Courses of 2026-27 · Semester 3' })
    expect(within(third).getByRole('link', { name: 'Course CS301' })).toHaveAttribute('href', '/courses/41')
    expect(within(third).getByText(/Whole semester/)).toBeInTheDocument()
    expect(within(third).getByText(/Section A/)).toBeInTheDocument()
    expect(within(third).getByText('Meera Iyer', { exact: false })).toBeInTheDocument()
    const fourth = screen.getByRole('list', { name: 'Courses of 2026-27 · Semester 4' })
    expect(within(fourth).getByText(/Sanjay Verma, Rohan Gupta/)).toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)).toEqual([
      expect.stringContaining('2026-27 · Semester 3'),
      expect.stringContaining('2026-27 · Semester 4'),
    ])
  })
})
