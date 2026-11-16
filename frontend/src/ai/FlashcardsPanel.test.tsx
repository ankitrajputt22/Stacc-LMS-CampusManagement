import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { AiFlashcard } from '../types/ai'
import { FlashcardsPanel } from './FlashcardsPanel'

vi.mock('../api/aiApi')

const choices = { modules: [{ id: 7, title: 'Unit 2' }], topics: [{ id: 31, title: 'Joins', moduleTitle: 'Unit 2' }], files: [] }
const card = (id: number, dueOn: string): AiFlashcard => ({
  id, position: id, front: `Front ${id}`, back: `Back ${id}`, dueOn, intervalDays: 0, repetitions: 0,
  source: { sourceType: 'TOPIC', sourceId: 31, title: 'Joins', fileName: null, pageNumber: null, slideNumber: null, pageId: 31 },
})

function renderPanel() {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <FlashcardsPanel lmsCourseId={3} choices={choices} />
    </QueryClientProvider>,
  )
}

describe('FlashcardsPanel', () => {
  it('makes a set from a page', async () => {
    vi.mocked(api.getFlashcardSets).mockResolvedValue([])
    vi.mocked(api.createFlashcardSet).mockResolvedValue({ id: 5, title: 'Joins', cards: [card(1, '2000-01-01')], today: '2026-11-16' })
    const user = userEvent.setup()
    renderPanel()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Make cards from' }), 'A page')
    await user.click(screen.getByRole('button', { name: 'Make flashcards' }))
    expect(api.createFlashcardSet).toHaveBeenCalledWith(3, { scope: 'TOPIC', id: 31, cardCount: 10 }, expect.anything())
    expect(await screen.findByText('Made “Joins” with 1 card.')).toBeInTheDocument()
  })

  it('reviews only due cards, showing the answer before the rating', async () => {
    vi.mocked(api.getFlashcardSets).mockResolvedValue([{ id: 5, title: 'Joins', cards: 2, dueToday: 1 }])
    vi.mocked(api.getFlashcardSet).mockResolvedValue({ id: 5, title: 'Joins', cards: [card(1, '2000-01-01'), card(2, '2999-01-01')], today: '2026-11-16' })
    vi.mocked(api.reviewFlashcard).mockResolvedValue(card(1, '2999-01-01'))
    const user = userEvent.setup()
    renderPanel()
    await user.click(await screen.findByRole('button', { name: /^Review/ }))
    expect(await screen.findByText('Front 1')).toBeInTheDocument()
    expect(screen.queryByText('Front 2')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Good' })).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Show answer' }))
    expect(screen.getByText('Back 1')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Good' }))
    expect(api.reviewFlashcard).toHaveBeenCalledWith(3, 5, 1, 'GOOD')
    expect(await screen.findByText(/Done for today: you made 1 review\./)).toBeInTheDocument()
  })
})
