import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/engagementApi'
import type { CampusFest, FestShift } from '../types/engagement'
import { FestsSection } from './FestsSection'

vi.mock('../api/engagementApi')

const shift: FestShift = {
  shiftId: 3, roleTitle: 'Help desk', startsAt: '2099-02-01T04:00:00Z', endsAt: '2099-02-01T06:00:00Z', places: 2, accepted: 0,
  myApplicationId: null, myApplication: null,
}
const fest: CampusFest = {
  festId: 1, name: 'Tech Fest', description: null, startsOn: '2099-02-01', endsOn: '2099-02-02', status: 'PUBLISHED', schedule: [], shifts: [shift],
}

function renderFests(fests: CampusFest[]) {
  vi.mocked(api.getFests).mockResolvedValue(fests)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <FestsSection canVolunteer />
    </QueryClientProvider>,
  )
}

describe('FestsSection', () => {
  it('applies for a shift with the note through the API', async () => {
    vi.mocked(api.applyForFestShift).mockResolvedValue(fest)
    renderFests([fest])
    await userEvent.type(await screen.findByRole('textbox', { name: /Note for Help desk/ }), 'Mornings')
    await userEvent.click(screen.getByRole('button', { name: 'Volunteer' }))
    expect(api.applyForFestShift).toHaveBeenCalledWith(3, 'Mornings')
  })

  it('offers no application for a full shift', async () => {
    renderFests([{ ...fest, shifts: [{ ...shift, accepted: 2 }] }])
    expect(await screen.findByText('2 of 2 places taken')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Volunteer' })).not.toBeInTheDocument()
  })
})
