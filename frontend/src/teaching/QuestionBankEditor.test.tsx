import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as outcomesApi from '../api/lmsOutcomesApi'
import * as api from '../api/lmsQuestionBankApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { LmsQuestion } from '../types/lms'
import { QuestionBankEditor } from './QuestionBankEditor'

vi.mock('../api/lmsQuestionBankApi', () => ({
  getQuestions: vi.fn(),
  addQuestion: vi.fn(),
  editQuestion: vi.fn(),
  markQuestionReviewed: vi.fn(),
  allowQuestionPractice: vi.fn(),
  archiveQuestion: vi.fn(),
}))
vi.mock('../api/lmsOutcomesApi', () => ({ getFacultyOutcomes: vi.fn() }))

const question: LmsQuestion = {
  id: 301,
  type: 'TRUE_FALSE',
  text: 'A primary key may be null.',
  textFormat: 'PLAIN',
  explanation: null,
  options: [],
  trueFalseAnswer: false,
  answerGuidance: null,
  topic: 'SQL',
  difficulty: 'EASY',
  marks: 1,
  bloomLevel: 'REMEMBER',
  outcome: null,
  reviewStatus: 'NEEDS_REVIEW',
  practiceAllowed: false,
  status: 'ACTIVE',
  updatedAt: '2026-11-08T10:00:00Z',
}

function renderEditor() {
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <QuestionBankEditor lmsCourseId={8} />
    </QueryClientProvider>,
  )
}

describe('QuestionBankEditor', () => {
  beforeEach(() => {
    vi.mocked(outcomesApi.getFacultyOutcomes).mockResolvedValue([
      { id: 91, code: 'CO1', description: 'Model data.', status: 'ACTIVE' },
      { id: 92, code: 'CO2', description: 'Old.', status: 'ARCHIVED' },
    ])
  })

  it('adds a single-choice question with its correct option and outcome', async () => {
    vi.mocked(api.getQuestions).mockResolvedValue([])
    vi.mocked(api.addQuestion).mockResolvedValue({ ...question, id: 302 })
    const user = userEvent.setup()
    renderEditor()

    expect(await screen.findByText('No questions yet.')).toBeInTheDocument()
    await user.type(screen.getByLabelText('Question'), 'Which normal form removes partial dependencies?')
    await user.type(screen.getByLabelText('Option 1'), '1NF')
    await user.type(screen.getByLabelText('Option 2'), '2NF')
    await user.click(screen.getByRole('radio', { name: /Correct\s*answer: option 2/ }))
    await user.selectOptions(screen.getByLabelText('Course outcome (optional)'), '91')
    expect(within(screen.getByLabelText('Course outcome (optional)')).queryByText(/CO2/)).not.toBeInTheDocument()
    await user.clear(screen.getByLabelText('Marks'))
    await user.type(screen.getByLabelText('Marks'), '2')
    await user.click(screen.getByRole('button', { name: 'Add question' }))

    expect(api.addQuestion).toHaveBeenCalledWith(8, {
      type: 'SINGLE_CHOICE',
      text: 'Which normal form removes partial dependencies?',
      textFormat: 'FORMATTED',
      explanation: null,
      options: [
        { text: '1NF', correct: false },
        { text: '2NF', correct: true },
      ],
      trueFalseAnswer: null,
      answerGuidance: null,
      topic: null,
      difficulty: 'MEDIUM',
      marks: 2,
      bloomLevel: null,
      outcomeId: 91,
    })
  })

  it('stops a choice question without a correct option before sending', async () => {
    vi.mocked(api.getQuestions).mockResolvedValue([])
    const user = userEvent.setup()
    renderEditor()

    await screen.findByText('No questions yet.')
    await user.selectOptions(screen.getByLabelText('Question type'), 'MULTIPLE_CHOICE')
    await user.type(screen.getByLabelText('Question'), 'Pick the keys.')
    await user.type(screen.getByLabelText('Option 1'), 'A')
    await user.type(screen.getByLabelText('Option 2'), 'B')
    await user.click(screen.getByRole('checkbox', { name: /option 1/ }))
    await user.click(screen.getByRole('button', { name: 'Add question' }))

    expect(screen.getByRole('alert')).toHaveTextContent('Mark at least one correct option.')
    expect(api.addQuestion).not.toHaveBeenCalled()
  })

  it('shows the answers, marks a question reviewed, and archives only after confirmation', async () => {
    vi.mocked(api.getQuestions).mockResolvedValue([question])
    vi.mocked(api.markQuestionReviewed).mockResolvedValue({ ...question, reviewStatus: 'REVIEWED' })
    vi.mocked(api.archiveQuestion).mockResolvedValue({ ...question, status: 'ARCHIVED' })
    const user = userEvent.setup()
    renderEditor()

    expect(await screen.findByText('A primary key may be null.')).toBeInTheDocument()
    expect(screen.getByText('Answer: False')).toBeInTheDocument()
    expect(screen.getByText('Needs review')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /OK for practice/ })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /Mark reviewed/ }))
    expect(api.markQuestionReviewed).toHaveBeenCalledWith(301)

    await user.click(screen.getByRole('button', { name: /^Archive\s*question/ }))
    expect(api.archiveQuestion).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Archive question' }))
    expect(api.archiveQuestion).toHaveBeenCalledWith(301)
  })

  it('filters by search and finds nothing when no question matches', async () => {
    vi.mocked(api.getQuestions).mockResolvedValue([question])
    const user = userEvent.setup()
    renderEditor()

    await screen.findByText('A primary key may be null.')
    await user.type(screen.getByLabelText('Search questions'), 'foreign')
    expect(screen.getByText('No questions match these filters.')).toBeInTheDocument()
  })
})
