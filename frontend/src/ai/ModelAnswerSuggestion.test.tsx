import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { ModelAnswerSuggestion } from './ModelAnswerSuggestion'
import { rubricDraftOf } from './rubricDraftOf'

vi.mock('../api/aiApi')

describe('ModelAnswerSuggestion', () => {
  it('shows a model answer only on request, with its sources, and saves nothing', async () => {
    vi.mocked(api.suggestModelAnswer).mockResolvedValue({
      answer: '3NF removes transitive dependencies.', keyPoints: ['Transitive dependency'], insufficientContext: false,
      citations: [{ sourceType: 'TOPIC', sourceId: 2, title: 'Normal forms', fileName: null, pageNumber: null, slideNumber: null }],
    })
    const user = userEvent.setup()
    render(<MemoryRouter><ModelAnswerSuggestion questionId={41} /></MemoryRouter>)
    expect(api.suggestModelAnswer).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Suggest a model answer' }))
    expect(api.suggestModelAnswer).toHaveBeenCalledWith(41, expect.anything())
    expect(await screen.findByText('3NF removes transitive dependencies.')).toBeInTheDocument()
    expect(screen.getByText('Normal forms')).toBeInTheDocument()
    expect(screen.getByText(/Nothing is saved/)).toBeInTheDocument()
  })
})

describe('rubricDraftOf', () => {
  it('turns a suggestion into the rubric form draft', () => {
    expect(rubricDraftOf({
      title: 'Rubric: Essay', maxMarks: 10, insufficientContext: false, citations: [],
      criteria: [{ title: 'Analysis', description: null, maxPoints: 10, levels: [{ title: 'Top', description: null, points: 10 }] }],
    })).toEqual({
      title: 'Rubric: Essay', description: '',
      criteria: [{ title: 'Analysis', description: '', maxPoints: '10', levels: [{ title: 'Top', description: '', points: '10' }] }],
    })
  })
})
