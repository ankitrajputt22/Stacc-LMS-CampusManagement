import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/helpdeskApi'
import { SuggestedArticles } from './SuggestedArticles'

vi.mock('../api/helpdeskApi')

function renderSuggestions(categoryId: number | null, subject: string) {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <SuggestedArticles categoryId={categoryId} subject={subject} />
    </QueryClientProvider>,
  )
}

describe('SuggestedArticles', () => {
  it('asks for the chosen category and subject and opens an article in place', async () => {
    vi.mocked(api.getHelpArticles).mockResolvedValue([
      { articleId: 3, categoryId: 4, categoryName: 'Technical issues', title: 'Campus Wi-Fi keeps disconnecting', body: 'Forget the network.', publishedAt: '2026-11-10T10:00:00Z' },
    ])
    renderSuggestions(4, 'wifi drops')
    await userEvent.click(await screen.findByText('Campus Wi-Fi keeps disconnecting'))
    expect(screen.getByText('Forget the network.')).toBeVisible()
    expect(api.getHelpArticles).toHaveBeenCalledWith(4, 'wifi drops', expect.anything())
  })

  it('asks nothing before a category is chosen', () => {
    vi.mocked(api.getHelpArticles).mockClear()
    renderSuggestions(null, '')
    expect(api.getHelpArticles).not.toHaveBeenCalled()
    expect(screen.queryByRole('heading')).not.toBeInTheDocument()
  })
})
