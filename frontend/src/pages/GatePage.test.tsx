import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/outingsApi'
import { GatePage } from './GatePage'

vi.mock('../api/outingsApi')

describe('GatePage', () => {
  it('checks a typed code and records the student going out', async () => {
    vi.mocked(api.getGateMovements).mockResolvedValue([])
    const check = {
      valid: true, message: 'Valid. Record the student going out.', studentLoginId: 'student1', hostel: 'Main Hostel', room: 'A-101',
      kind: 'GATE_PASS' as const, leaveFrom: '2026-11-12T08:00:00Z', leaveUntil: '2026-11-12T18:00:00Z', outside: false, outingId: 4,
    }
    vi.mocked(api.checkPass).mockResolvedValue(check)
    vi.mocked(api.recordMovement).mockResolvedValue({ ...check, outside: true, message: 'Valid. The student is outside: record the return.' })
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <GatePage />
      </QueryClientProvider>,
    )
    await userEvent.type(screen.getByRole('textbox', { name: 'Pass code' }), 'ABCD-EFGH-JKLM-NPQR')
    await userEvent.click(screen.getByRole('button', { name: 'Check' }))
    expect(await screen.findByText('student1')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Record going out' }))
    expect(api.recordMovement).toHaveBeenCalledWith('ABCD-EFGH-JKLM-NPQR', 'OUT')
    expect(await screen.findByRole('button', { name: 'Record coming back' })).toBeInTheDocument()
  })
})
