import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/analyticsApi'
import { CourseAnalyticsView } from './CourseAnalyticsView'

vi.mock('../api/analyticsApi')

describe('CourseAnalyticsView', () => {
  it('shows counts but no percentages for a small class, and lists no students', async () => {
    vi.mocked(api.getMyExports).mockResolvedValue([])
    vi.mocked(api.getCourseAnalytics).mockResolvedValue({
      lmsCourseId: 1, enrolled: 3, smallGroup: true, note: 'Shown for groups of at least 5 students.', attendancePercent: null, sessions: [],
      assignments: [{ assignmentId: 4, title: 'Lab 2', dueAt: '2026-11-01T10:00:00Z', submitted: 2, submittedPercent: null, late: 1, graded: 2, averagePercent: null }],
      quizzes: [], requiredPages: [],
    })
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <CourseAnalyticsView lmsCourseId={1} />
      </QueryClientProvider>,
    )
    expect(await screen.findByText(/This course has 3 enrolled, so only counts are shown/)).toBeInTheDocument()
    expect(screen.getByRole('rowheader', { name: 'Lab 2' })).toBeInTheDocument()
    expect(screen.queryByText(/%/)).not.toBeInTheDocument()
  })
})
