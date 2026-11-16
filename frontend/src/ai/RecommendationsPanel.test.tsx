import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import { RecommendationsPanel } from './RecommendationsPanel'

vi.mock('../api/aiApi')

describe('RecommendationsPanel', () => {
  it('lists each suggestion with its reason and a link to the page or assignment', async () => {
    vi.mocked(api.getRecommendations).mockResolvedValue([
      { kind: 'PRACTISE_WEAK', targetId: 2, title: 'Practise Indexes', reason: 'Your recent answers here average 30%.' },
      { kind: 'ASSIGNMENT_SOON', targetId: 9, title: 'Prepare for Lab 2', reason: 'It is due Wed 18 Nov.' },
      { kind: 'REVIEW_FLASHCARDS', targetId: 5, title: 'Review Unit 2 cards', reason: '3 cards are due today.' },
    ])
    render(
      <QueryClientProvider client={testQueryClient()}>
        <MemoryRouter><RecommendationsPanel lmsCourseId={3} /></MemoryRouter>
      </QueryClientProvider>,
    )
    expect(await screen.findByRole('link', { name: 'Practise Indexes' })).toHaveAttribute('href', '/courses/3/pages/2')
    expect(screen.getByRole('link', { name: 'Prepare for Lab 2' })).toHaveAttribute('href', '/courses/3/assignments/9')
    expect(screen.getByText('3 cards are due today.')).toBeInTheDocument()
  })
})
