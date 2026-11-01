import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { testQueryClient } from '../test/queryTestUtils'
import { MasterSection, PAGE_SIZE } from './MasterSection'

type Row = { id: number; code: string; status: 'ACTIVE' | 'INACTIVE' }

const rows: Row[] = Array.from({ length: 25 }, (_, index) => ({
  id: index + 1,
  code: `DEP${String(index + 1).padStart(2, '0')}`,
  status: index % 5 === 0 ? 'INACTIVE' : 'ACTIVE',
}))

function renderSection(data: Row[] = rows, blockedReason: string | null = null) {
  const renderCreate = vi.fn(() => <p>New department form</p>)
  render(
    <QueryClientProvider client={testQueryClient()}>
      <MasterSection<Row>
        title="Departments"
        singular="department"
        intro="The college’s departments."
        request={{ status: 'loaded', data, retry: vi.fn(), refresh: vi.fn() }}
        columns={[
          { header: 'Code', cell: (row) => row.code },
          { header: 'Status', cell: (row) => row.status },
        ]}
        rowKey={(row) => row.id}
        rowName={(row) => `department ${row.code}`}
        searchText={(row) => row.code}
        rowStatus={(row) => row.status}
        blockedReason={blockedReason}
        renderCreate={renderCreate}
        renderEdit={() => <p>Edit form</p>}
      />
    </QueryClientProvider>,
  )
  return { renderCreate }
}

// jsdom's name calculation drops the space before a screen-reader-only word ("Previouspage"); browsers keep it.
const named = (text: string) => new RegExp(`^${text.replace(' ', '\\s*')}$`)

/** The codes in the wide-screen table, in order. */
function shownCodes() {
  const table = screen.getByRole('table', { name: 'Departments' })
  return within(table)
    .getAllByRole('rowheader')
    .map((cell) => cell.textContent)
}

describe('MasterSection', () => {
  it('shows one page at a time and moves between pages', async () => {
    renderSection()

    expect(shownCodes()).toHaveLength(PAGE_SIZE)
    expect(screen.getByText('Showing 1–20 of 25 departments')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: named('Previous page') })).toBeDisabled()

    await userEvent.setup().click(screen.getByRole('button', { name: named('Next page') }))

    expect(shownCodes()).toEqual(['DEP21', 'DEP22', 'DEP23', 'DEP24', 'DEP25'])
    expect(screen.getByText('Showing 21–25 of 25 departments')).toBeInTheDocument()
  })

  it('searches and filters by status, going back to the first page', async () => {
    const user = userEvent.setup()
    renderSection()
    await user.click(screen.getByRole('button', { name: named('Next page') }))

    await user.selectOptions(screen.getByLabelText('Status'), 'INACTIVE')
    expect(shownCodes()).toEqual(['DEP01', 'DEP06', 'DEP11', 'DEP16', 'DEP21'])
    expect(screen.queryByRole('navigation', { name: /Pages of/ })).not.toBeInTheDocument()

    await user.type(screen.getByLabelText('Search departments'), 'dep1')
    expect(shownCodes()).toEqual(['DEP11', 'DEP16'])
  })

  it('says when nothing matches, and offers editing by name for each row', async () => {
    const user = userEvent.setup()
    renderSection()

    expect(screen.getAllByRole('button', { name: named('Edit department DEP01') }).length).toBeGreaterThan(0)
    await user.type(screen.getByLabelText('Search departments'), 'nothing like this')

    expect(screen.getByText('No departments match.')).toBeInTheDocument()
  })

  it('opens the form from Add, and explains instead when adding is blocked', async () => {
    const user = userEvent.setup()
    const { renderCreate } = renderSection(rows.slice(0, 3))
    await user.click(screen.getByRole('button', { name: 'Add department' }))
    expect(renderCreate).toHaveBeenCalled()
    expect(screen.getByText('New department form')).toBeInTheDocument()
  })

  it('keeps Add from opening the form while it is blocked', async () => {
    const user = userEvent.setup()
    const { renderCreate } = renderSection(rows.slice(0, 3), 'Add a department first.')
    const add = screen.getByRole('button', { name: 'Add department' })

    expect(add).toHaveAttribute('aria-disabled', 'true')
    expect(add).toHaveAccessibleDescription('Add a department first.')
    await user.click(add)
    expect(renderCreate).not.toHaveBeenCalled()
  })

  it('offers the first Add in the empty state', () => {
    renderSection([])

    expect(screen.getByText('No departments yet.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Add department' })).toBeInTheDocument()
  })
})
