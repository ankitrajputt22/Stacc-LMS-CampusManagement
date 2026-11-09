import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse } from 'axios'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as registrationApi from '../api/registrationApi'
import { AuthContext, type AuthContextValue } from '../auth/AuthContext'
import { testQueryClient } from '../test/queryTestUtils'
import type { RegistrationOverview } from '../types/registration'
import { CourseRegistrationPage } from './CourseRegistrationPage'

vi.mock('../api/registrationApi', () => ({
  getMyRegistration: vi.fn(),
  registerForCourses: vi.fn(),
  dropCourse: vi.fn(),
}))

const overview: RegistrationOverview = {
  profileActive: true,
  terms: [
    {
      academicSessionCode: '2026-27',
      programCode: 'BTECH-CSE',
      programName: 'B.Tech. CSE',
      semesterNumber: 3,
      sectionCode: 'A',
      registrationOpensAt: '2026-07-01T03:30:00Z',
      registrationClosesAt: '2026-07-15T11:30:00Z',
      dropDeadline: '2026-07-31T11:30:00Z',
      registrationOpen: true,
      dropOpen: true,
      maxCredits: 24,
      registeredCredits: 14,
      offerings: [
        {
          courseOfferingId: 105, courseCode: 'CS305', courseName: 'Software Engineering', credits: 3,
          courseType: 'ELECTIVE', sectionCode: null, faculty: ['Rohan Gupta'], capacity: 60, availableSeats: 59,
          canRegister: true, reason: null,
        },
        {
          courseOfferingId: 106, courseCode: 'CS306', courseName: 'Web Technologies', credits: 4,
          courseType: 'ELECTIVE', sectionCode: 'A', faculty: [], capacity: 2, availableSeats: 0,
          canRegister: false, reason: 'This course is full.',
        },
      ],
      registered: [
        {
          courseEnrollmentId: 501, courseOfferingId: 101, courseCode: 'CS301', courseName: 'Data Structures', credits: 4,
          sectionCode: null, faculty: ['Meera Iyer'], status: 'ENROLLED', registeredAt: '2026-06-20T05:00:00Z',
          droppedAt: null, canDrop: true,
        },
      ],
    },
  ],
}

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

function renderPage() {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <AuthContext value={auth}>
          <CourseRegistrationPage />
        </AuthContext>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function conflict(message: string): AxiosError {
  const response = { status: 409, data: { status: 409, message, fieldErrors: [] } } as unknown as AxiosResponse
  return new AxiosError(message, 'ERR_BAD_REQUEST', undefined, undefined, response)
}

describe('CourseRegistrationPage', () => {
  beforeEach(() => {
    vi.mocked(registrationApi.getMyRegistration).mockReset().mockResolvedValue(overview)
    vi.mocked(registrationApi.registerForCourses).mockReset()
    vi.mocked(registrationApi.dropCourse).mockReset()
  })

  it('shows open offerings, and a full one cannot be chosen', async () => {
    renderPage()

    expect(await screen.findByRole('checkbox', { name: 'CS305 Software Engineering' })).toBeEnabled()
    expect(screen.getByRole('checkbox', { name: 'CS306 Web Technologies' })).toBeDisabled()
    expect(screen.getByText('This course is full.')).toBeInTheDocument()
    expect(screen.getByText(/59 of 60 seats free/)).toBeInTheDocument()
  })

  it('registers only after the summary is confirmed', async () => {
    const user = userEvent.setup()
    vi.mocked(registrationApi.registerForCourses).mockResolvedValue({
      registered: [{ ...overview.terms[0].registered[0], courseEnrollmentId: 502, courseCode: 'CS305' }],
      totalCourses: 1,
      totalCredits: 3,
    })
    renderPage()

    await user.click(await screen.findByRole('checkbox', { name: 'CS305 Software Engineering' }))
    expect(screen.getByText('Credits after registration').nextElementSibling).toHaveTextContent('17 of at most 24')
    await user.click(screen.getByRole('button', { name: 'Review registration' }))
    expect(screen.getByText(/You are registering for 1 course with a total of 3 credits: CS305/)).toBeInTheDocument()
    expect(registrationApi.registerForCourses).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))

    expect(registrationApi.registerForCourses).toHaveBeenCalledWith([105])
    expect(await screen.findByText(/Registered for 1 course \(3 credits\): CS305/)).toBeInTheDocument()
  })

  it('shows the backend’s reason when registration is refused', async () => {
    const user = userEvent.setup()
    vi.mocked(registrationApi.registerForCourses).mockRejectedValue(conflict('CS305 is full: all 60 places are taken.'))
    renderPage()

    await user.click(await screen.findByRole('checkbox', { name: 'CS305 Software Engineering' }))
    await user.click(screen.getByRole('button', { name: 'Review registration' }))
    await user.click(screen.getByRole('button', { name: 'Confirm registration' }))

    expect(await screen.findByText('CS305 is full: all 60 places are taken.')).toBeInTheDocument()
  })

  it('drops a course only after confirming', async () => {
    const user = userEvent.setup()
    vi.mocked(registrationApi.dropCourse).mockResolvedValue({
      ...overview.terms[0].registered[0],
      status: 'DROPPED',
      droppedAt: '2026-07-05T06:00:00Z',
      canDrop: false,
    })
    renderPage()

    const mine = await screen.findByRole('region', { name: 'My registered courses' })
    await user.click(within(mine).getByRole('button', { name: /^Drop\s*CS301$/ }))
    expect(registrationApi.dropCourse).not.toHaveBeenCalled()
    await user.click(within(mine).getByRole('button', { name: 'Drop course' }))

    expect(registrationApi.dropCourse).toHaveBeenCalledWith(501)
    expect(await screen.findByText('CS301 Data Structures dropped.')).toBeInTheDocument()
  })
})
