import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as academicApi from '../api/adminAcademicApi'
import * as enrollmentApi from '../api/adminEnrollmentApi'
import { testQueryClient } from '../test/queryTestUtils'
import { EnrollmentMonitorSection } from './EnrollmentMonitorSection'

vi.mock('../api/adminAcademicApi', () => ({
  getAcademicSessions: vi.fn(),
  getPrograms: vi.fn(),
  getSemesters: vi.fn(),
  getCourses: vi.fn(),
}))
vi.mock('../api/adminEnrollmentApi', () => ({ searchCourseEnrollments: vi.fn() }))

const row = {
  courseEnrollmentId: 501, studentProfileId: 57, loginId: 'student1', name: 'Aarav Sharma', rollNumber: '25CSE001',
  courseCode: 'CS305', courseName: 'Software Engineering', credits: 3, academicSessionCode: '2026-27',
  programCode: 'BTECH-CSE', semesterNumber: 3, sectionCode: 'A', status: 'DROPPED' as const,
  registeredAt: '2026-07-02T05:00:00Z', droppedAt: '2026-07-03T05:00:00Z',
}

describe('EnrollmentMonitorSection', () => {
  beforeEach(() => {
    vi.mocked(academicApi.getAcademicSessions).mockResolvedValue([])
    vi.mocked(academicApi.getPrograms).mockResolvedValue([])
    vi.mocked(academicApi.getSemesters).mockResolvedValue([])
    vi.mocked(academicApi.getCourses).mockResolvedValue([])
    vi.mocked(enrollmentApi.searchCourseEnrollments).mockReset()
      .mockResolvedValue({ items: [row], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  })

  it('asks the server with the chosen status and search text', async () => {
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={testQueryClient()}>
        <EnrollmentMonitorSection />
      </QueryClientProvider>,
    )

    expect((await screen.findAllByText('Aarav Sharma')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Dropped').length).toBeGreaterThan(0)

    await user.selectOptions(screen.getByLabelText('Status'), 'DROPPED')
    await user.type(screen.getByLabelText('Search course enrollments'), 'cs305')
    await user.click(screen.getByRole('button', { name: 'Find' }))

    expect(enrollmentApi.searchCourseEnrollments).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'DROPPED', q: 'cs305', page: 0, size: 20 }),
      expect.anything(),
    )
  })
})
