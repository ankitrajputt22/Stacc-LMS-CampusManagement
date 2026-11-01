import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import { Tabs } from './Tabs'

type Section = 'home' | 'modules' | 'grades'

const sections: { value: Section; label: string }[] = [
  { value: 'home', label: 'Home' },
  { value: 'modules', label: 'Modules' },
  { value: 'grades', label: 'Grades' },
]

function Example() {
  const [selected, setSelected] = useState<Section>('home')
  return (
    <Tabs label="Course sections" tabs={sections} selected={selected} onSelect={setSelected}>
      <p>Showing {selected}</p>
    </Tabs>
  )
}

describe('Tabs', () => {
  it('follows the ARIA tabs pattern', () => {
    render(<Example />)

    expect(screen.getByRole('tablist', { name: 'Course sections' })).toBeInTheDocument()
    const home = screen.getByRole('tab', { name: 'Home' })
    expect(home).toHaveAttribute('aria-selected', 'true')
    expect(home).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('tab', { name: 'Modules' })).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('tabpanel')).toHaveAccessibleName('Home')
  })

  it('selects a tab by click', async () => {
    const user = userEvent.setup()
    render(<Example />)

    await user.click(screen.getByRole('tab', { name: 'Grades' }))

    expect(screen.getByRole('tab', { name: 'Grades' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('Showing grades')).toBeInTheDocument()
  })

  it('moves with the arrow keys, Home, and End, wrapping at the ends', async () => {
    const user = userEvent.setup()
    render(<Example />)
    await user.click(screen.getByRole('tab', { name: 'Home' }))

    await user.keyboard('{ArrowLeft}')
    expect(screen.getByRole('tab', { name: 'Grades' })).toHaveFocus()
    expect(screen.getByRole('tab', { name: 'Grades' })).toHaveAttribute('aria-selected', 'true')

    await user.keyboard('{ArrowRight}')
    expect(screen.getByRole('tab', { name: 'Home' })).toHaveFocus()

    await user.keyboard('{End}')
    expect(screen.getByRole('tab', { name: 'Grades' })).toHaveFocus()

    await user.keyboard('{Home}')
    expect(screen.getByRole('tab', { name: 'Home' })).toHaveFocus()
    expect(screen.getByText('Showing home')).toBeInTheDocument()
  })
})
