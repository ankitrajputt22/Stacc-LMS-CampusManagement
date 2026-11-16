import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import type { AiEvaluation } from '../types/ai'
import { PracticePanel } from './PracticePanel'

vi.mock('../api/aiApi')

const set = {
  insufficientContext: false,
  questions: [
    { question: 'What does commit do?', suggestedAnswer: 'Makes changes durable.', citations: [] },
    { question: 'What does rollback do?', suggestedAnswer: 'Undoes changes.', citations: [] },
  ],
}
const marked = (marks: number, extra: Partial<AiEvaluation> = {}): AiEvaluation => ({
  marks, maxMarks: 4, verdict: 'PARTLY_CORRECT', feedback: 'You missed durability.', followUpQuestion: null, citations: [],
  insufficientContext: false, ...extra,
})

function renderPanel() {
  render(<MemoryRouter><PracticePanel lmsCourseId={3} topics={[]} /></MemoryRouter>)
}

describe('PracticePanel', () => {
  it('checks one answer at a time with feedback and the model answer', async () => {
    vi.mocked(api.generateCoursePracticeQuestions).mockResolvedValue(set)
    vi.mocked(api.evaluatePracticeAnswer).mockResolvedValue(marked(2))
    const user = userEvent.setup()
    renderPanel()
    await user.click(screen.getByRole('button', { name: 'Start' }))
    expect(await screen.findByText('What does commit do?')).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: /^Your answer/ }), 'Saves data.')
    await user.click(screen.getByRole('button', { name: 'Check answer' }))
    expect(api.evaluatePracticeAnswer).toHaveBeenCalledWith(3, {
      question: 'What does commit do?', referenceAnswer: 'Makes changes durable.', studentAnswer: 'Saves data.', maxMarks: 4, mode: 'PRACTICE',
    })
    expect(await screen.findByText('Partly correct · 2 of 4 practice marks')).toBeInTheDocument()
    expect(screen.getByText('You missed durability.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next question' }))
    expect(screen.getByText('What does rollback do?')).toBeInTheDocument()
  })

  it('marks a mock exam only when it is handed in, with a total', async () => {
    vi.mocked(api.generateCoursePracticeQuestions).mockResolvedValue(set)
    vi.mocked(api.evaluatePracticeAnswer).mockResolvedValueOnce(marked(4, { verdict: 'CORRECT' })).mockResolvedValueOnce(marked(1))
    const user = userEvent.setup()
    renderPanel()
    await user.selectOptions(screen.getByRole('combobox', { name: 'Mode' }), 'Mock exam')
    await user.click(screen.getByRole('button', { name: 'Start' }))
    const boxes = await screen.findAllByRole('textbox', { name: /^Your answer/ })
    await user.type(boxes[0], 'Makes changes durable.')
    await user.type(boxes[1], 'Deletes rows.')
    expect(api.evaluatePracticeAnswer).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Hand in mock exam' }))
    expect(await screen.findByText('Mock exam score: 5 of 8 practice marks')).toBeInTheDocument()
    expect(vi.mocked(api.evaluatePracticeAnswer).mock.calls.map((call) => call[1].mode)).toEqual(['MOCK_EXAM', 'MOCK_EXAM'])
  })
})
