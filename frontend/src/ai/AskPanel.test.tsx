import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { AiAnswer } from '../types/ai'
import { AskPanel } from './AskPanel'

vi.mock('../api/aiApi')

const answer = (text: string): AiAnswer => ({
  answer: text, insufficientContext: false,
  citations: [{ sourceType: 'TOPIC', sourceId: 31, title: 'Joins', fileName: null, pageNumber: null, slideNumber: null, pageId: 31 }],
})

function renderPanel(modules: { id: number; title: string }[] = []) {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <AskPanel lmsCourseId={3} modules={modules} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('AskPanel', () => {
  it('asks with the chosen search and module, and links each source to its page', async () => {
    vi.mocked(api.askCourseAi).mockResolvedValue(answer('A join combines rows.'))
    const user = userEvent.setup()
    renderPanel([{ id: 7, title: 'Unit 2: SQL' }])
    await user.click(screen.getByRole('radio', { name: /^Course \+ my notes/ }))
    await user.selectOptions(screen.getByRole('combobox', { name: 'Module' }), 'Unit 2: SQL')
    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'What is a join?')
    await user.click(screen.getByRole('button', { name: 'Ask' }))
    expect(api.askCourseAi).toHaveBeenLastCalledWith(3, 'What is a join?', expect.anything(),
      { mode: 'COURSE_AND_NOTES', moduleId: 7, history: [], style: 'NORMAL', assignmentId: null })
    expect(await screen.findByRole('link', { name: /^Open page/ })).toHaveAttribute('href', '/courses/3/pages/31')
    await user.click(screen.getByRole('radio', { name: /^My notes only/ }))
    expect(screen.queryByRole('combobox', { name: 'Module' })).not.toBeInTheDocument()
  })

  it('follows up with the earlier turns, explains simpler, and saves only on request', async () => {
    vi.mocked(api.askCourseAi)
      .mockResolvedValueOnce(answer('A join combines rows.'))
      .mockResolvedValueOnce(answer('Simply put, a join puts rows together.'))
      .mockResolvedValueOnce(answer('A left join keeps every left row.'))
    vi.mocked(api.saveAnswer).mockResolvedValue({} as never)
    const user = userEvent.setup()
    renderPanel()
    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'What is a join?')
    await user.click(screen.getByRole('button', { name: 'Ask' }))
    await screen.findByText('A join combines rows.')

    await user.click(screen.getByRole('button', { name: 'Explain simpler' }))
    expect(api.askCourseAi).toHaveBeenLastCalledWith(3, 'What is a join?', expect.anything(), {
      mode: 'COURSE', moduleId: null, history: [{ question: 'What is a join?', answer: 'A join combines rows.' }], style: 'SIMPLER', assignmentId: null,
    })
    await screen.findByText('Simply put, a join puts rows together.')

    await user.type(screen.getByRole('textbox', { name: 'Follow-up question' }), 'And a left join?')
    await user.click(screen.getByRole('button', { name: 'Ask follow-up' }))
    expect(vi.mocked(api.askCourseAi).mock.lastCall?.[3]?.history).toHaveLength(2)
    await screen.findByText('A left join keeps every left row.')

    expect(api.saveAnswer).not.toHaveBeenCalled()
    await user.click(screen.getAllByRole('button', { name: /^Save answer/ })[0])
    expect(api.saveAnswer).toHaveBeenCalledWith(3, 'What is a join?', 'A join combines rows.', answer('').citations)
    expect(await screen.findByText('Saved. You can find it under Saved answers.')).toBeInTheDocument()
  })
})

describe('AskPanel hints', () => {
  it('asks for hints only when the question is about an assignment', async () => {
    vi.mocked(api.askCourseAi).mockResolvedValue(answer('Hint: look at Joins.'))
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={testQueryClient()}>
        <MemoryRouter><AskPanel lmsCourseId={3} assignments={[{ id: 9, title: 'Lab 2' }]} /></MemoryRouter>
      </QueryClientProvider>,
    )
    await user.selectOptions(screen.getByRole('combobox', { name: /^Help with an assignment/ }), 'Lab 2')
    expect(screen.getByText(/hints and guiding questions only/)).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Question' }), 'How do I start?')
    await user.click(screen.getByRole('button', { name: 'Ask' }))
    expect(vi.mocked(api.askCourseAi).mock.lastCall?.[3]).toMatchObject({ assignmentId: 9 })
  })
})
