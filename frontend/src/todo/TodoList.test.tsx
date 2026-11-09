import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/todoApi'
import { TodoList } from './TodoList'

vi.mock('../api/todoApi')

describe('TodoList', () => {
  it('lists items with their links and dismisses one by its key', async () => {
    vi.mocked(api.getTodo).mockResolvedValue([
      { key: 'ASSIGNMENT:4', kind: 'ASSIGNMENT', title: 'Submit: Lab 2', context: 'BCS301', dueAt: '2099-01-01T10:00:00Z', actionPath: '/courses/1/assignments/4' },
      { key: 'APPROVAL:9', kind: 'APPROVAL', title: 'Approve: Hostel', context: 'Hostel · from student1', dueAt: null, actionPath: null },
    ])
    vi.mocked(api.dismissTodo).mockResolvedValue()
    render(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <TodoList />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    expect(await screen.findByRole('link', { name: 'Submit: Lab 2' })).toHaveAttribute('href', '/courses/1/assignments/4')
    expect(screen.getByText('Approve: Hostel', { selector: 'span.font-semibold' })).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: /Approve: Hostel/ }))
    expect(api.dismissTodo).toHaveBeenCalledWith('APPROVAL:9')
  })
})
