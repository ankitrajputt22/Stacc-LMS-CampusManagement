import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/grievanceApi'
import type { GrievanceCase } from '../types/grievances'
import { CommitteeGrievance, MyGrievance } from './GrievanceCaseView'

vi.mock('../api/grievanceApi')

const base: GrievanceCase = {
  caseId: 9, caseNumber: 'GRV-000009', categoryName: 'Ragging', subject: 'Hostel B', details: 'What happened.', status: 'SUBMITTED',
  submittedAt: '2026-11-10T05:00:00Z', respondBy: '2026-11-17T05:00:00Z', overdue: false, reviewStartedAt: null, resolution: null,
  resolvedAt: null, appealReason: null, appealedAt: null, appealRespondBy: null, appealDecision: null, closedAt: null, appealUntil: null,
  complainantLoginId: 'student1',
}

function renderWith(node: React.ReactNode) {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{node}</QueryClientProvider>)
}

describe('grievance case views', () => {
  it('lets the committee resolve a submitted case with its text', async () => {
    vi.mocked(api.getCommitteeGrievance).mockResolvedValue(base)
    vi.mocked(api.resolveGrievance).mockResolvedValue({ ...base, status: 'RESOLVED' })
    renderWith(<CommitteeGrievance caseId={9} notify={vi.fn()} />)
    expect(await screen.findByText('student1')).toBeInTheDocument()
    await userEvent.type(screen.getByRole('textbox', { name: 'Resolution' }), 'Warning issued.')
    await userEvent.click(screen.getByRole('button', { name: 'Resolve' }))
    expect(api.resolveGrievance).toHaveBeenCalledWith(9, 'Warning issued.')
  })

  it('offers the complainant Accept and Appeal only once resolved', async () => {
    vi.mocked(api.getMyGrievance).mockResolvedValue({ ...base, complainantLoginId: null, status: 'RESOLVED', resolution: 'Warning issued.', appealUntil: '2026-11-30T05:00:00Z' })
    renderWith(<MyGrievance caseId={9} notify={vi.fn()} />)
    expect(await screen.findByRole('button', { name: 'Accept resolution' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Appeal' })).toBeInTheDocument()
    expect(screen.queryByText('student1')).not.toBeInTheDocument()
  })
})
