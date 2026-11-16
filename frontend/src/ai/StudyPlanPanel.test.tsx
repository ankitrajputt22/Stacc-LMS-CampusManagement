import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/aiApi'
import { StudyPlanPanel } from './StudyPlanPanel'

vi.mock('../api/aiApi')

describe('StudyPlanPanel', () => {
  it('shows the plan and adds it to the calendar or To-Do only when asked', async () => {
    vi.mocked(api.planStudy).mockResolvedValue([
      { date: '2026-11-16', busy: false, budget: 60, planned: 55, sessions: [
        { kind: 'ASSIGNMENT', targetId: 9, title: 'Work on Lab 2', minutes: 45 },
        { kind: 'FLASHCARDS', targetId: null, title: 'Review flashcards', minutes: 10 }] },
      { date: '2026-11-17', busy: true, budget: 30, planned: 0, sessions: [] },
    ])
    vi.mocked(api.addStudyPlan).mockResolvedValue(2)
    const user = userEvent.setup()
    render(<StudyPlanPanel lmsCourseId={3} />)
    await user.click(screen.getByRole('button', { name: 'Make a plan' }))
    expect(api.planStudy).toHaveBeenCalledWith(3, 7, 60)
    expect(await screen.findByText('Work on Lab 2 · 45 min')).toBeInTheDocument()
    expect(api.addStudyPlan).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'Add to my To-Do' }))
    expect(api.addStudyPlan).toHaveBeenCalledWith(3, 'todo', 7, 60)
    expect(await screen.findByText('Added 2 study sessions to your To-Do list.')).toBeInTheDocument()
  })
})
