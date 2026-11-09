import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../../api/hostelApi'
import { BedMap } from './BedMap'

vi.mock('../../api/hostelApi')

describe('BedMap', () => {
  it('shows each bed state in words and lets a warden check a student in, with no maintenance controls', async () => {
    vi.mocked(api.getHostelBedMap).mockResolvedValue({
      hostelId: 1,
      code: 'MAIN',
      name: 'Main Hostel',
      rooms: [
        {
          roomId: 3, roomNumber: 'A-101', block: 'A', floorNumber: 1, roomType: 'Double', active: true,
          beds: [
            { bedId: 7, bedNumber: 'B1', state: 'OCCUPIED', studentLoginId: 'student1', allocationId: 9, checkedIn: false },
            { bedId: 8, bedNumber: 'B2', state: 'MAINTENANCE', studentLoginId: null, allocationId: null, checkedIn: false },
          ],
        },
      ],
    })
    vi.mocked(api.checkInAllocation).mockResolvedValue()
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <BedMap side="warden" hostelId={1} />
      </QueryClientProvider>,
    )
    expect(await screen.findByText('Block A · Floor 1 · Double')).toBeInTheDocument()
    expect(screen.getByText('Maintenance')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Back in use/ })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Check in/ }))
    expect(api.checkInAllocation).toHaveBeenCalledWith('warden', 9, null)
  })
})
