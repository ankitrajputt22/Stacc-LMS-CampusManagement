import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as contentApi from '../api/lmsContentApi'
import { testQueryClient } from '../test/queryTestUtils'
import { CourseProgressCard } from './CourseProgressCard'

vi.mock('../api/lmsContentApi', () => ({ getCourseProgress: vi.fn() }))

function show() {
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <CourseProgressCard lmsCourseId={42} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('CourseProgressCard', () => {
  it('shows the required pages completed and where to continue', async () => {
    vi.mocked(contentApi.getCourseProgress).mockResolvedValue({
      requiredPages: 4, completedRequiredPages: 1, next: { id: 33, title: 'Arrays', moduleTitle: 'Unit 1' },
    })
    show()

    expect(await screen.findByText('1 of 4 required pages completed')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: 'Required pages completed' })).toHaveAttribute('aria-valuetext', '25%')
    expect(screen.getByRole('link', { name: 'Arrays' })).toHaveAttribute('href', '/courses/42/pages/33')
  })

  it('shows nothing for a course without required pages', async () => {
    vi.mocked(contentApi.getCourseProgress).mockResolvedValue({ requiredPages: 0, completedRequiredPages: 0, next: null })
    const { container } = show()

    await vi.waitFor(() => expect(contentApi.getCourseProgress).toHaveBeenCalled())
    expect(container).toBeEmptyDOMElement()
  })
})
