import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/engagementApi'
import type { CampusEvent } from '../types/engagement'
import { EventsSection } from './EventsSection'

vi.mock('../api/engagementApi')

const base: CampusEvent = {
  eventId: 1, title: 'Coding night', description: null, groupId: 2, groupName: 'Coding Club', organizerLoginId: 'student1', venue: 'Lab 3',
  startsAt: '2099-01-01T12:00:00Z', endsAt: '2099-01-01T14:00:00Z', capacity: 1, registrationOpensAt: null, registrationClosesAt: null,
  forStudents: true, forFaculty: false, membersOnly: false, status: 'PUBLISHED', registered: 1, waitlisted: 0, myRegistration: null,
  myWaitlistPosition: null, cannotRegisterBecause: [], canManage: false,
}

function renderEvents(events: CampusEvent[]) {
  vi.mocked(api.getEvents).mockResolvedValue(events)
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <EventsSection canRegister />
    </QueryClientProvider>,
  )
}

describe('EventsSection', () => {
  it('offers the waitlist for a full event and registers through the API', async () => {
    vi.mocked(api.registerForEvent).mockResolvedValue({ ...base, myRegistration: 'WAITLISTED', myWaitlistPosition: 1 })
    renderEvents([base])
    await userEvent.click(await screen.findByRole('button', { name: /Join the waitlist/ }))
    expect(api.registerForEvent).toHaveBeenCalledWith(1)
  })

  it('shows the backend’s reasons instead of a button when registration is not possible', async () => {
    renderEvents([{ ...base, eventId: 2, cannotRegisterBecause: ['This event is for members of Coding Club.'] }])
    expect(await screen.findByText('This event is for members of Coding Club.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Register|waitlist/ })).not.toBeInTheDocument()
  })
})
