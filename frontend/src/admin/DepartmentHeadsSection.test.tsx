import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as api from '../api/departmentHeadsApi'
import { testQueryClient } from '../test/queryTestUtils'
import type { DepartmentHeadRow } from '../types/departmentHeads'
import { DepartmentHeadsSection } from './DepartmentHeadsSection'

vi.mock('../api/departmentHeadsApi', () => ({
  getDepartmentHeads: vi.fn(),
  getDepartmentHeadCandidates: vi.fn(),
  getDepartmentHeadHistory: vi.fn(),
  assignDepartmentHead: vi.fn(),
  endDepartmentHead: vi.fn(),
}))

const cse: DepartmentHeadRow = {
  departmentId: 1, departmentCode: 'CSE', departmentName: 'Computer Science and Engineering', departmentStatus: 'ACTIVE',
  head: {
    departmentHeadId: 4, userAccountId: 12, loginId: 'hod1', name: 'Lakshmi Menon', status: 'ACTIVE',
    startedAt: '2026-07-01T00:00:00Z', endedAt: null, counts: true, notCountedReason: null,
  },
}
const ece: DepartmentHeadRow = {
  departmentId: 2, departmentCode: 'ECE', departmentName: 'Electronics', departmentStatus: 'ACTIVE',
  head: {
    departmentHeadId: 5, userAccountId: 13, loginId: 'hod2', name: null, status: 'ACTIVE',
    startedAt: '2026-07-01T00:00:00Z', endedAt: null, counts: false, notCountedReason: 'The account is disabled.',
  },
}

function show() {
  render(
    <QueryClientProvider client={testQueryClient()}>
      <DepartmentHeadsSection />
    </QueryClientProvider>,
  )
}

describe('DepartmentHeadsSection', () => {
  beforeEach(() => {
    vi.mocked(api.getDepartmentHeads).mockReset().mockResolvedValue([cse, ece])
    vi.mocked(api.getDepartmentHeadCandidates).mockResolvedValue([
      { userAccountId: 12, loginId: 'hod1', name: 'Lakshmi Menon' },
      { userAccountId: 14, loginId: 'hod3', name: null },
    ])
    vi.mocked(api.getDepartmentHeadHistory).mockResolvedValue([cse.head!])
    vi.mocked(api.assignDepartmentHead).mockReset().mockResolvedValue({
      ...cse, head: { ...cse.head!, userAccountId: 14, loginId: 'hod3', name: null },
    })
    vi.mocked(api.endDepartmentHead).mockReset().mockResolvedValue({ ...cse, head: null })
  })

  it('shows each head and says when one does not count', async () => {
    show()

    expect((await screen.findAllByText('Lakshmi Menon')).length).toBeGreaterThan(0)
    expect(screen.getAllByText('Not counted').length).toBeGreaterThan(0)
    expect(screen.getAllByText('The account is disabled.').length).toBeGreaterThan(0)
  })

  it('sets a new head from the candidates and lists the earlier heads', async () => {
    const user = userEvent.setup()
    show()

    await user.click((await screen.findAllByRole('button', { name: /^Edit\s*head of CSE$/ }))[0])
    expect(await screen.findByRole('region', { name: 'Heads over time' })).toBeInTheDocument()
    expect(screen.getByText(/ends Lakshmi Menon’s term/)).toBeInTheDocument()
    await user.selectOptions(screen.getByLabelText('Head of department'), 'hod3')
    await user.click(screen.getByRole('button', { name: 'Save head' }))

    expect(api.assignDepartmentHead).toHaveBeenCalledWith(1, 14)
  })

  it('ends a term only after confirmation', async () => {
    const user = userEvent.setup()
    show()

    await user.click((await screen.findAllByRole('button', { name: /^End term\s*of the head of CSE$/ }))[0])
    expect(api.endDepartmentHead).not.toHaveBeenCalled()
    const confirmation = screen.getByText('End Lakshmi Menon’s term as head of CSE?').closest('div')!
    await user.click(within(confirmation.parentElement!).getByRole('button', { name: 'End term' }))

    expect(api.endDepartmentHead).toHaveBeenCalledWith(1)
  })
})
