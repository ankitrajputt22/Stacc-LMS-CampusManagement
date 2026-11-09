import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { MonthGrid } from './MonthGrid'

describe('MonthGrid', () => {
  it('shows an event on every day it covers, deadlines as links, and personal entries', () => {
    render(
      <MemoryRouter>
        <MonthGrid
          month={{ year: 2026, month: 11 }}
          calendar={{
            fromDate: '2026-11-01',
            toDate: '2026-11-30',
            timeZone: 'UTC',
            academicEvents: [
              { eventId: 1, title: 'Mid-terms', description: null, category: 'EXAM', startDate: '2026-11-09', endDate: '2026-11-10', status: 'PUBLISHED', academicSessionCode: '2026-27', semesterNumber: null, programCode: null },
            ],
            studentAssignmentDeadlines: [
              { assignmentId: 4, lmsCourseId: 2, courseCode: 'BCS301', courseName: 'DS', title: 'Lab 2', dueAt: '2026-11-12T12:00:00Z', maxMarks: 10 },
            ],
            facultyAssignmentDeadlines: [],
          }}
          personal={[{ eventId: 7, title: 'Study group', notes: null, startDate: '2026-11-15', endDate: '2026-11-15' }]}
        />
      </MemoryRouter>,
    )
    expect(screen.getAllByText('Mid-terms')).toHaveLength(2)
    expect(screen.getByRole('link', { name: 'Due: Lab 2' })).toHaveAttribute('href', '/courses/2/assignments/4')
    expect(screen.getByText('Study group')).toBeInTheDocument()
  })
})
