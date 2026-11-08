import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as bankApi from '../api/lmsQuestionBankApi'
import * as api from '../api/lmsQuizzesApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { LmsQuestion, LmsQuiz, LmsQuizQuestion } from '../types/lms'
import { QuizEditor } from './QuizEditor'

vi.mock('../api/lmsQuizzesApi', () => ({
  getFacultyQuiz: vi.fn(),
  updateQuiz: vi.fn(),
  addQuizQuestion: vi.fn(),
  changeQuizQuestionMarks: vi.fn(),
  removeQuizQuestion: vi.fn(),
  reorderQuizQuestions: vi.fn(),
  publishQuiz: vi.fn(),
  archiveQuiz: vi.fn(),
}))
vi.mock('../api/lmsQuestionBankApi', () => ({ getQuestions: vi.fn() }))

function quizQuestion(id: number, position: number, text: string): LmsQuizQuestion {
  return {
    id,
    position,
    marks: 2,
    questionId: id + 100,
    type: 'TRUE_FALSE',
    text,
    textFormat: 'PLAIN',
    topic: null,
    difficulty: 'EASY',
    bankMarks: 2,
    reviewStatus: 'REVIEWED',
    practiceAllowed: false,
    status: 'ACTIVE',
  }
}

const quiz: LmsQuiz = {
  id: 501,
  lmsCourseId: 8,
  title: 'Unit test',
  instructions: null,
  instructionsFormat: 'PLAIN',
  type: 'GRADED',
  timeLimitMinutes: 20,
  availableFrom: null,
  dueAt: null,
  availableUntil: null,
  maxAttempts: 1,
  shuffleQuestions: false,
  shuffleOptions: false,
  negativeMarkPercent: 0,
  status: 'DRAFT',
  publishedAt: null,
  totalMarks: 4,
  questions: [quizQuestion(601, 1, 'Keys are unique.'), quizQuestion(602, 2, 'Views store data.')],
}

const bankQuestion = {
  id: 301,
  type: 'TRUE_FALSE',
  text: 'A primary key may be null.',
  textFormat: 'PLAIN',
  topic: 'Keys',
  difficulty: 'EASY',
  marks: 1,
  reviewStatus: 'REVIEWED',
  practiceAllowed: false,
  status: 'ACTIVE',
} as LmsQuestion

function renderEditor() {
  return render(
    <QueryClientProvider client={testQueryClient()}>
      <MemoryRouter>
        <QuizEditor lmsCourseId={8} quizId={501} />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('QuizEditor', () => {
  beforeEach(() => {
    vi.mocked(bankApi.getQuestions).mockResolvedValue([bankQuestion, { ...bankQuestion, id: 701, status: 'ARCHIVED', text: 'Gone.' }])
  })

  it('adds a bank question, moves questions, and publishes a draft after confirming', async () => {
    vi.mocked(api.getFacultyQuiz).mockResolvedValue(quiz)
    vi.mocked(api.addQuizQuestion).mockResolvedValue(quiz)
    vi.mocked(api.reorderQuizQuestions).mockResolvedValue(quiz)
    vi.mocked(api.publishQuiz).mockResolvedValue({ ...quiz, status: 'PUBLISHED' })
    const user = userEvent.setup()
    renderEditor()

    expect(await screen.findByText('Keys are unique.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^Move up\s*question 1$/ })).toBeDisabled()
    expect(await screen.findByText('A primary key may be null.')).toBeInTheDocument()
    expect(screen.queryByText('Gone.')).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /^Add\s*“A primary key may be null\.” to the quiz$/ }))
    expect(api.addQuizQuestion).toHaveBeenCalledWith(501, 301)

    await user.click(screen.getByRole('button', { name: /^Move down\s*question 1$/ }))
    expect(api.reorderQuizQuestions).toHaveBeenCalledWith(501, [602, 601])

    await user.click(screen.getByRole('button', { name: 'Publish quiz' }))
    expect(api.publishQuiz).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Publish' }))
    expect(api.publishQuiz).toHaveBeenCalledWith(501)
  })

  it('checks marks before saving them', async () => {
    vi.mocked(api.getFacultyQuiz).mockResolvedValue(quiz)
    vi.mocked(api.changeQuizQuestionMarks).mockResolvedValue(quiz)
    const user = userEvent.setup()
    renderEditor()

    const marks = await screen.findByLabelText('Marks for question 2')
    await user.clear(marks)
    await user.type(marks, '0')
    await user.click(screen.getByRole('button', { name: /^Save marks\s*for question 2$/ }))
    expect(screen.getByText('Enter marks greater than zero, with at most 2 decimal places.')).toBeInTheDocument()
    expect(api.changeQuizQuestionMarks).not.toHaveBeenCalled()

    await user.clear(marks)
    await user.type(marks, '2.5')
    await user.click(screen.getByRole('button', { name: /^Save marks\s*for question 2$/ }))
    expect(api.changeQuizQuestionMarks).toHaveBeenCalledWith(501, 602, 2.5)
  })

  it('shows a published quiz with its questions fixed and no bank', async () => {
    vi.mocked(api.getFacultyQuiz).mockResolvedValue({ ...quiz, status: 'PUBLISHED', publishedAt: '2026-11-10T05:00:00Z' })
    renderEditor()

    expect(await screen.findByText('Keys are unique.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Move up/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Publish quiz' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Archive quiz' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'Add from the question bank' })).not.toBeInTheDocument()
    expect(bankApi.getQuestions).not.toHaveBeenCalled()
  })
})
