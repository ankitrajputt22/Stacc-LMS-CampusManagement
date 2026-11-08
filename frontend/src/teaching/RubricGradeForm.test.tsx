import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/lmsRubricsApi'
import type { LmsFacultySubmissionAttempt, LmsRubric } from '../types/lms'
import { RubricGradeForm } from './RubricGradeForm'

vi.mock('../api/lmsRubricsApi', () => ({ gradeWithRubric: vi.fn() }))

const rubric: LmsRubric = {
  id: 4,
  title: 'Array exercises rubric',
  description: null,
  status: 'ACTIVE',
  totalPoints: 20,
  inUse: false,
  criteria: [
    { id: 40, title: 'Correct answers', description: null, maxPoints: 12, levels: [{ title: 'All correct', description: null, points: 12 }] },
    { id: 41, title: 'Clear working', description: null, maxPoints: 8, levels: [] },
  ],
}

const attempt = { attemptId: 301, attemptNumber: 1, grade: null } as unknown as LmsFacultySubmissionAttempt

describe('RubricGradeForm', () => {
  it('fills points from a level, checks them, and sends every criterion with the total as marks', async () => {
    vi.mocked(api.gradeWithRubric).mockResolvedValue({
      marksAwarded: 17,
      maxMarksSnapshot: 20,
      feedback: null,
      gradedAt: '2026-11-10T05:00:00Z',
      rubric: [],
    })
    const onSaved = vi.fn()
    const user = userEvent.setup()
    render(<RubricGradeForm attempt={attempt} rubric={rubric} onSaved={onSaved} />)

    await user.click(screen.getByRole('button', { name: 'All correct (12)' }))
    expect(screen.getByLabelText('Points for Correct answers')).toHaveValue('12')
    await user.type(screen.getByLabelText('Points for Clear working'), '9')
    await user.click(screen.getByRole('button', { name: 'Save grade' }))
    expect(screen.getByText('Clear working: enter points from 0 to 8, with at most 2 decimal places.')).toBeInTheDocument()
    expect(api.gradeWithRubric).not.toHaveBeenCalled()

    await user.clear(screen.getByLabelText('Points for Clear working'))
    await user.type(screen.getByLabelText('Points for Clear working'), '5')
    await user.type(screen.getByLabelText('Comment on Clear working (optional)'), 'Show step 3.')
    expect(screen.getByText('Total: 17 / 20')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save grade' }))
    expect(api.gradeWithRubric).toHaveBeenCalledWith(
      301,
      [
        { criterionId: 40, points: 12, comment: null },
        { criterionId: 41, points: 5, comment: 'Show step 3.' },
      ],
      null,
    )
    expect(onSaved).toHaveBeenCalled()
  })
})
